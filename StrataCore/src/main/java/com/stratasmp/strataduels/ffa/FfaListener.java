package com.stratasmp.strataduels.ffa;

import com.stratasmp.strataduels.arena.Arena;
import com.stratasmp.strataduels.match.MatchManager;
import com.stratasmp.strataduels.match.PlayerSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class FfaListener implements Listener {
   private static final Set<String> ALLOWED_DURING_FFA = Set.of("ffa", "msg", "tell", "r", "reply");
   private static final long BOUNDARY_NOTICE_COOLDOWN_MILLIS = 2000L;
   private final FfaManager ffa;
   private final MatchManager matches;
   private final Map<UUID, Long> lastBoundaryNoticeAt = new HashMap<>();

   public FfaListener(FfaManager ffa, MatchManager matches) {
      this.ffa = ffa;
      this.matches = matches;
   }

   private boolean restricted(Player player) {
      return !player.hasPermission("strataduels.admin") && this.ffa.isFighter(player.getUniqueId());
   }

   @EventHandler(ignoreCancelled = true)
   public void onCommand(PlayerCommandPreprocessEvent event) {
      Player player = event.getPlayer();
      if (this.restricted(player)) {
         String base = event.getMessage().substring(1).split("\\s+", 2)[0].toLowerCase();
         if (!ALLOWED_DURING_FFA.contains(base)) {
            event.setCancelled(true);
            player.sendMessage("You can't use that during the FFA - use /ffa leave to drop out.");
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onInventoryOpen(InventoryOpenEvent event) {
      if (event.getPlayer() instanceof Player player && this.restricted(player)) {
         event.setCancelled(true);
         player.sendMessage("You can't open that during the FFA.");
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onDrop(PlayerDropItemEvent event) {
      if (this.restricted(event.getPlayer())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onDamage(EntityDamageEvent event) {
      if (!(event.getEntity() instanceof Player victim)) {
         return;
      }
      UUID id = victim.getUniqueId();
      if (this.ffa.isLimbo(id)) {
         event.setCancelled(true);
         return;
      }
      if (!this.ffa.isFighter(id)) {
         return;
      }
      if (!this.ffa.isActive()) {
         event.setCancelled(true);
         return;
      }
      Player attacker = null;
      if (event instanceof EntityDamageByEntityEvent byEntity) {
         attacker = this.resolveAttacker(byEntity.getDamager());
         if (attacker != null && !attacker.getUniqueId().equals(id)) {
            if (!this.ffa.isFighter(attacker.getUniqueId())) {
               event.setCancelled(true);
               return;
            }
            event.setCancelled(false);
            this.ffa.recordHit(id, attacker.getUniqueId());
         }
      }
      if (!event.isCancelled() && event.getFinalDamage() >= victim.getHealth() && !this.hasTotem(victim)) {
         event.setCancelled(true);
         double maxHealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
         victim.setHealth(Math.min(1.0, maxHealth));
         this.ffa.handleLethal(id, attacker == null ? null : attacker.getUniqueId());
      }
   }

   // RankEssentials tags both sides "in combat" on any player hit; an FFA has its own rules and must not leave a timer behind
   @EventHandler(priority = EventPriority.MONITOR)
   public void onHitClearCombat(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player victim && this.ffa.isFighter(victim.getUniqueId())) {
         this.matches.clearCombatTags(victim.getUniqueId());
         Player attacker = this.resolveAttacker(event.getDamager());
         if (attacker != null && this.ffa.isFighter(attacker.getUniqueId())) {
            this.matches.clearCombatTags(attacker.getUniqueId());
         }
      }
   }

   private boolean hasTotem(Player player) {
      return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
         || player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent event) {
      Player victim = event.getEntity();
      UUID id = victim.getUniqueId();
      if (this.ffa.isFighter(id) || this.ffa.isLimbo(id)) {
         event.getDrops().clear();
         event.setDroppedExp(0);
         event.setKeepInventory(false);
         this.ffa.handleLethal(id, null);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onRespawn(PlayerRespawnEvent event) {
      PlayerSnapshot snapshot = this.ffa.limboSnapshot(event.getPlayer().getUniqueId());
      if (snapshot != null) {
         event.setRespawnLocation(snapshot.location());
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onBlockPlace(BlockPlaceEvent event) {
      if (this.ffa.isActive() && this.ffa.isFighter(event.getPlayer().getUniqueId())) {
         event.setCancelled(false);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onBlockBreak(BlockBreakEvent event) {
      if (this.ffa.isActive() && this.ffa.isFighter(event.getPlayer().getUniqueId())) {
         event.setCancelled(false);
      }
   }

   private Player resolveAttacker(Entity damager) {
      if (damager instanceof Player player) {
         return player;
      }
      return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
   }

   @EventHandler(ignoreCancelled = true)
   public void onMove(PlayerMoveEvent event) {
      if (this.ffa.isFrozen(event.getPlayer().getUniqueId())
         && (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockY() != event.getTo().getBlockY()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ())) {
         event.setTo(event.getFrom());
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onMoveOutOfArena(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      if (!this.ffa.isActive() || !this.ffa.isFighter(player.getUniqueId()) || this.ffa.arena() == null) {
         return;
      }
      Location clamped = this.ffa.arena().clampToPlayArea(event.getTo());
      if (clamped != null) {
         event.setTo(clamped);
         this.notifyBoundaryBounce(player);
      }
   }

   // PlayerMoveEvent doesn't fire for teleports (ender pearls, chorus fruit, other plugins), so those are clamped here
   @EventHandler(ignoreCancelled = true)
   public void onTeleportOutOfArena(PlayerTeleportEvent event) {
      this.keepInsideArena(event);
   }

   @EventHandler(ignoreCancelled = true)
   public void onPortalOutOfArena(PlayerPortalEvent event) {
      this.keepInsideArena(event);
   }

   private void keepInsideArena(PlayerTeleportEvent event) {
      Player player = event.getPlayer();
      Arena arena = this.ffa.arena();
      if (!this.ffa.isActive() || !this.ffa.isFighter(player.getUniqueId()) || arena == null || event.getTo() == null) {
         return;
      }
      Location clamped = arena.clampToPlayArea(event.getTo());
      if (clamped != null) {
         event.setTo(clamped);
         this.notifyBoundaryBounce(player);
      } else if (!arena.contains(event.getTo())) {
         event.setCancelled(true);
         this.notifyBoundaryBounce(player);
      }
   }

   private void notifyBoundaryBounce(Player player) {
      long now = System.currentTimeMillis();
      Long last = this.lastBoundaryNoticeAt.get(player.getUniqueId());
      if (last != null && now - last < BOUNDARY_NOTICE_COOLDOWN_MILLIS) {
         return;
      }
      this.lastBoundaryNoticeAt.put(player.getUniqueId(), now);
      player.sendActionBar(Component.text("You can't leave the arena!", NamedTextColor.RED));
   }
}
