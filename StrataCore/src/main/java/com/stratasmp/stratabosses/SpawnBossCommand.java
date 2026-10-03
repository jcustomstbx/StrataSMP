package com.stratasmp.stratabosses;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SpawnBossCommand implements CommandExecutor, TabCompleter {
   private final BossRegistry registry;
   private final BossManager bossManager;
   private final BossArenaBuilder arenaBuilder;

   public SpawnBossCommand(BossRegistry registry, BossManager bossManager) {
      this.registry = registry;
      this.bossManager = bossManager;
      this.arenaBuilder = new BossArenaBuilder();
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
      if (!sender.hasPermission("stratabosses.admin")) {
         sender.sendMessage("You don't have permission to spawn bosses.");
         return true;
      } else if (sender instanceof Player player) {
         if (args.length < 1) {
            player.sendMessage("Usage: /spawnboss <" + String.join("|", this.registry.all().keySet()) + ">");
            return true;
         } else {
            BossDefinition def = this.registry.byId(args[0].toLowerCase());
            if (def == null) {
               player.sendMessage("Unknown boss. Valid ids: " + String.join(", ", this.registry.all().keySet()));
               return true;
            } else {
               Location spot = this.targetLocation(player);
               if (this.bossManager.activeBossNear(spot, 25.0)) {
                  player.sendMessage("Another boss is standing too close - its arena would overlap. Pick a spot further away.");
                  return true;
               }
               BossArenaBuilder.ArenaResult arena = this.arenaBuilder.build(spot, def.id);
               LivingEntity entity = (LivingEntity)player.getWorld().spawnEntity(arena.spawnLocation, def.baseType);
               if (!entity.isValid()) {
                  BossArenaBuilder.restore(arena.snapshot);
                  player.sendMessage("Spawn got blocked here (region protection or similar) - try a different spot.");
                  return true;
               } else {
                  try {
                     this.bossManager.makeBoss(entity, def);
                     this.bossManager.attachArena(entity.getUniqueId(), arena.snapshot);
                  } catch (RuntimeException e) {
                     entity.remove();
                     BossArenaBuilder.restore(arena.snapshot);
                     player.sendMessage("The boss could not be set up: " + e.getMessage());
                     return true;
                  }
                  player.sendMessage("Spawned " + def.displayName + ".");
                  return true;
               }
            }
         }
      } else {
         sender.sendMessage("Only players can use /spawnboss.");
         return true;
      }
   }

   private Location targetLocation(Player player) {
      Block targetBlock = player.getTargetBlockExact(60);
      return targetBlock != null ? targetBlock.getLocation().add(0.5, 1.0, 0.5) : player.getLocation();
   }

   @Nullable
   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
      return args.length != 1
         ? List.of()
         : new ArrayList<>(this.registry.all().keySet()).stream().filter(id -> id.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
   }
}
