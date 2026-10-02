package com.stratasmp.strataeconomy.currency;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;

/** Vault bridge - all real work lives in {@link StratasService}. Stratas are whole numbers. */
public final class VaultStratasProvider implements Economy {

    private static final EconomyResponse NO_BANKS =
            new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "StrataEconomy has no bank support");

    private final StratasService service;
    private final String symbol;
    private final String singular;
    private final String plural;

    public VaultStratasProvider(StratasService service, String symbol, String singular, String plural) {
        this.service = service;
        this.symbol = symbol;
        this.singular = singular;
        this.plural = plural;
    }

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "StrataEconomy"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 0; }

    @Override public String format(double amount) {
        return Amounts.format(Math.round(amount), symbol, plural);
    }

    @Override public String currencyNamePlural() { return plural; }
    @Override public String currencyNameSingular() { return singular; }

    @Override public boolean hasAccount(OfflinePlayer player) { return player != null && player.getUniqueId() != null; }
    @Override public boolean hasAccount(String playerName) { return resolve(playerName) != null; }
    @Override public boolean hasAccount(OfflinePlayer player, String world) { return hasAccount(player); }
    @Override public boolean hasAccount(String playerName, String world) { return hasAccount(playerName); }

    @Override public double getBalance(OfflinePlayer player) { return service.getBalance(player.getUniqueId()); }
    @Override public double getBalance(String playerName) {
        OfflinePlayer p = resolve(playerName);
        return p == null ? 0 : getBalance(p);
    }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }
    @Override public double getBalance(String playerName, String world) { return getBalance(playerName); }

    @Override public boolean has(OfflinePlayer player, double amount) { return service.has(player.getUniqueId(), Math.round(amount)); }
    @Override public boolean has(String playerName, double amount) { return getBalance(playerName) >= amount; }
    @Override public boolean has(OfflinePlayer player, String world, double amount) { return has(player, amount); }
    @Override public boolean has(String playerName, String world, double amount) { return has(playerName, amount); }

    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        long a = Math.round(amount);
        service.deposit(player.getUniqueId(), a);
        return new EconomyResponse(a, service.getBalance(player.getUniqueId()), ResponseType.SUCCESS, null);
    }
    @Override public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer p = resolve(playerName);
        return p == null ? fail(amount, "Unknown player") : depositPlayer(p, amount);
    }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) { return depositPlayer(player, amount); }
    @Override public EconomyResponse depositPlayer(String playerName, String world, double amount) { return depositPlayer(playerName, amount); }

    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        long a = Math.round(amount);
        boolean ok = service.withdraw(player.getUniqueId(), a);
        long bal = service.getBalance(player.getUniqueId());
        return ok
                ? new EconomyResponse(a, bal, ResponseType.SUCCESS, null)
                : new EconomyResponse(a, bal, ResponseType.FAILURE, "Insufficient " + plural);
    }
    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer p = resolve(playerName);
        return p == null ? fail(amount, "Unknown player") : withdrawPlayer(p, amount);
    }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) { return withdrawPlayer(player, amount); }
    @Override public EconomyResponse withdrawPlayer(String playerName, String world, double amount) { return withdrawPlayer(playerName, amount); }

    @Override public boolean createPlayerAccount(OfflinePlayer player) { return true; }
    @Override public boolean createPlayerAccount(String playerName) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String world) { return true; }
    @Override public boolean createPlayerAccount(String playerName, String world) { return true; }

    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return NO_BANKS; }
    @Override public EconomyResponse createBank(String name, String player) { return NO_BANKS; }
    @Override public EconomyResponse deleteBank(String name) { return NO_BANKS; }
    @Override public EconomyResponse bankBalance(String name) { return NO_BANKS; }
    @Override public EconomyResponse bankHas(String name, double amount) { return NO_BANKS; }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return NO_BANKS; }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return NO_BANKS; }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return NO_BANKS; }
    @Override public EconomyResponse isBankOwner(String name, String player) { return NO_BANKS; }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return NO_BANKS; }
    @Override public EconomyResponse isBankMember(String name, String player) { return NO_BANKS; }
    @Override public List<String> getBanks() { return Collections.emptyList(); }

    private OfflinePlayer resolve(String name) {
        OfflinePlayer p = Bukkit.getPlayerExact(name);
        if (p != null) {
            return p;
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off.hasPlayedBefore() || off.isOnline() ? off : null;
    }

    private EconomyResponse fail(double amount, String msg) {
        return new EconomyResponse(amount, 0, ResponseType.FAILURE, msg);
    }
}
