package com.stratasmp.strataevents;

import com.stratasmp.stratacore.AsyncYamlSaver;
import com.stratasmp.stratacore.AtomicYaml;
import com.stratasmp.strataeconomy.StrataEconomy;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Pays a Stratas bonus for the first login of each day, growing with the streak. */
final class LoginFeature implements Listener {
    private final StrataEvents plugin;
    private final LoginStreaks streaks = new LoginStreaks();
    private final AsyncYamlSaver saver;

    LoginFeature(StrataEvents plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "login-streaks.yml");
        this.saver = new AsyncYamlSaver(plugin, file);
        streaks.load(AtomicYaml.load(file, plugin.getLogger()));
    }

    private ConfigurationSection cfg() {
        return plugin.getConfig().getConfigurationSection("login");
    }

    private boolean enabled() {
        ConfigurationSection login = cfg();
        return login != null && login.getBoolean("enabled", true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled()) return;
        long delay = Math.max(1, cfg().getLong("delay-seconds", 5)) * 20L;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) claim(player);
        }, delay);
    }

    private void claim(Player player) {
        LocalDate today = plugin.today();
        LoginStreaks.Claim claim = streaks.claim(player.getUniqueId(), today);
        if (claim == null) return;
        ConfigurationSection login = cfg();
        long amount = LoginStreaks.stratasFor(claim.streak(), login.getLong("stratas.base", 25),
                login.getLong("stratas.per-day", 25), login.getInt("stratas.max-day", 7));
        StrataEconomy economy = plugin.core().module(StrataEconomy.class);
        String paid = "";
        if (economy != null && economy.isEnabled() && amount > 0) {
            economy.stratas().deposit(player.getUniqueId(), amount);
            paid = " +" + economy.money(amount);
        }
        player.sendMessage(Component.text("Daily login! Day " + claim.streak() + " streak." + paid, NamedTextColor.GOLD));
        milestone(player, claim.streak(), economy);
        saver.save(streaks.snapshot());
    }

    private void milestone(Player player, int streak, StrataEconomy economy) {
        ConfigurationSection milestone = cfg().getConfigurationSection("milestones." + streak);
        if (milestone == null) return;
        long bonus = milestone.getLong("stratas", 0);
        if (bonus > 0 && economy != null && economy.isEnabled()) {
            economy.stratas().deposit(player.getUniqueId(), bonus);
            player.sendMessage(Component.text("Streak bonus for " + streak + " days: +" + economy.money(bonus), NamedTextColor.LIGHT_PURPLE));
        }
        for (String command : milestone.getStringList("commands")) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Streak milestone command failed for " + player.getName() + ": " + command + " (" + e + ")");
            }
        }
    }

    void showStatus(Player player) {
        LocalDate today = plugin.today();
        int current = streaks.currentStreak(player.getUniqueId(), today);
        boolean claimed = streaks.claimedToday(player.getUniqueId(), today);
        player.sendMessage(Component.text("Login streak: " + current + " day(s), best " + streaks.best(player.getUniqueId()) + ". "
                + (claimed ? "Today's reward is claimed." : "Today's reward arrives when you log in."), NamedTextColor.YELLOW));
    }
}
