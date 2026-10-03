package com.stratasmp.strataduels.match;

import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
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

public class DuelListener implements Listener {
   private static final Set<String> ALLOWED_DURING_MATCH = Set.of("duel", "msg", "tell", "r", "reply");
   private static final long BOUNDARY_NOTICE_COOLDOWN_MILLIS = 2000L;
   private final MatchManager matchManager;
   private final Map<UUID, Long> lastBoundaryNoticeAt = new HashMap<>();

   public DuelListener(MatchManager matchManager) {
      this.matchManager = matchManager;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission("strataduels.admin") && this.matchManager.getActiveMatchFor(player.getUniqueId()) != null) {
         String base = event.getMessage().substring(1).split("\\s+", 2)[0].toLowerCase();
         if (!ALLOWED_DURING_MATCH.contains(base)) {
            event.setCancelled(true);
            player.sendMessage("You can't use that while dueling.");
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onInventoryOpen(InventoryOpenEvent event) {
      if (event.getPlayer() instanceof Player player
         && !player.hasPermission("strataduels.admin")
         && this.matchManager.getActiveMatchFor(player.getUniqueId()) != null) {
         // vaults, ender chests, other plugins' storage GUIs - none of it should be
         // reachable mid-duel. A kit is only ever meant to exist for the length of
         // the match, and PlayerSnapshot.applyState() has no way to claw an item
         // back out of a completely different plugin's storage once it's moved there.
         event.setCancelled(true);
         player.sendMessage("You can't open that while dueling.");
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDrop(PlayerDropItemEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission("strataduels.admin") && this.matchManager.getActiveMatchFor(player.getUniqueId()) != null) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player victim) {
         DuelMatch match = this.matchManager.getActiveMatchFor(victim.getUniqueId());
         if (match != null && match.state != DuelMatch.State.ACTIVE && !match.playerA.equals(match.playerB)) {
            // before the start and during the end delay nobody can be hurt: a duelist killed for real here would
            // be restored onto a dead player and lose their real inventory on respawn
            event.setCancelled(true);
            return;
         }
         if (match != null && match.state == DuelMatch.State.ACTIVE) {
            if (event instanceof EntityDamageByEntityEvent byEntity) {
               Player attacker = this.resolveAttacker(byEntity.getDamager());
               UUID opponent = match.opponentOf(victim.getUniqueId());
               if (attacker != null && attacker.getUniqueId().equals(opponent)) {
                  event.setCancelled(false);
               }
            }

            if (!event.isCancelled() && event.getFinalDamage() >= victim.getHealth() && !this.hasTotem(victim)) {
               event.setCancelled(true);
               double maxHealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
               victim.setHealth(Math.min(1.0, maxHealth));
               this.matchManager.handleLethalHit(victim.getUniqueId());
            }
         }
      }
   }

   // runs after every other plugin's damage handling, so it undoes the combat tag that RankEssentials
   // applies to both players the moment they hit each other - no timer should exist during a duel
   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onDuelHitClearCombat(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player victim && this.matchManager.getActiveMatchFor(victim.getUniqueId()) != null) {
         this.matchManager.clearCombatTags(victim.getUniqueId());
         Player attacker = this.resolveAttacker(event.getDamager());
         if (attacker != null && this.matchManager.getActiveMatchFor(attacker.getUniqueId()) != null) {
            this.matchManager.clearCombatTags(attacker.getUniqueId());
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
      if (this.matchManager.getActiveMatchFor(victim.getUniqueId()) != null) {
         event.getDrops().clear();
         event.setDroppedExp(0);
         this.matchManager.handleLethalHit(victim.getUniqueId());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onRespawn(PlayerRespawnEvent event) {
      Player player = event.getPlayer();
      DuelMatch match = this.matchManager.getActiveMatchFor(player.getUniqueId());
      if (match != null) {
         PlayerSnapshot snapshot = match.playerA.equals(player.getUniqueId()) ? match.snapshotA : match.snapshotB;
         if (snapshot != null) {
            event.setRespawnLocation(snapshot.location());
         }
      }
   }

   @EventHandler
   public void onKnockback(EntityPushedByEntityAttackEvent event) {
      if (event.getEntity() instanceof Player victim) {
         DuelMatch match = this.matchManager.getActiveMatchFor(victim.getUniqueId());
         if (match != null && match.state == DuelMatch.State.ACTIVE) {
            Player source = this.resolveAttacker(event.getPushedBy());
            UUID opponent = match.opponentOf(victim.getUniqueId());
            if (source != null && source.getUniqueId().equals(opponent)) {
               event.setKnockback(event.getKnockback());
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onBlockPlace(BlockPlaceEvent event) {
      if (this.isActiveDuelist(event.getPlayer())) {
         event.setCancelled(false);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onBlockBreak(BlockBreakEvent event) {
      if (this.isActiveDuelist(event.getPlayer())) {
         event.setCancelled(false);
      }
   }

   private boolean isActiveDuelist(Player player) {
      DuelMatch match = this.matchManager.getActiveMatchFor(player.getUniqueId());
      return match != null && match.state == DuelMatch.State.ACTIVE;
   }

   private Player resolveAttacker(Entity damager) {
      if (damager instanceof Player player) {
         return player;
      } else {
         return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (this.matchManager.isFrozen(event.getPlayer().getUniqueId())
         && (
            event.getFrom().getBlockX() != event.getTo().getBlockX()
               || event.getFrom().getBlockY() != event.getTo().getBlockY()
               || event.getFrom().getBlockZ() != event.getTo().getBlockZ()
         )) {
         event.setTo(event.getFrom());
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onMoveOutOfArena(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      DuelMatch match = this.matchManager.getActiveMatchFor(player.getUniqueId());
      if (match == null || match.state != DuelMatch.State.ACTIVE) {
         return;
      }
      Location clamped = match.arena.clampToPlayArea(event.getTo());
      if (clamped != null) {
         // bounces them off the boundary like a wall - walking (or an elytra glide) simply can't
         // carry a duelist past it, same as the room's real barriers, no forfeit either way
         event.setTo(clamped);
         this.notifyBoundaryBounce(player);
      }
   }

   // PlayerMoveEvent doesn't fire for teleports (ender pearls, chorus fruit, other plugins) -
   // this closes that gap the same way, so a pearl can't tunnel a duelist through a wall either.
   @EventHandler(
      ignoreCancelled = true
   )
   public void onTeleportOutOfArena(PlayerTeleportEvent event) {
      this.keepInsideArena(event);
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPortalOutOfArena(PlayerPortalEvent event) {
      this.keepInsideArena(event);
   }

   private void keepInsideArena(PlayerTeleportEvent event) {
      Player player = event.getPlayer();
      DuelMatch match = this.matchManager.getActiveMatchFor(player.getUniqueId());
      if (match == null || match.state != DuelMatch.State.ACTIVE || event.getTo() == null) {
         return;
      }
      Location clamped = match.arena.clampToPlayArea(event.getTo());
      if (clamped != null) {
         // land them just inside the wall/ceiling they tried to clip through, instead of ending the match
         event.setTo(clamped);
         this.notifyBoundaryBounce(player);
      } else if (!match.arena.contains(event.getTo())) {
         // a portal or another plugin tried to move them to an entirely different world - nothing to
         // clamp to there, so just refuse the teleport and leave them where they were
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
