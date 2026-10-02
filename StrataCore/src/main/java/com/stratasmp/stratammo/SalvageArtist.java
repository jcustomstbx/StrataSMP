package com.stratasmp.stratammo;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SalvageArtist {
   private static final Map<String, Material> TIER_MATERIAL = Map.of(
      "WOODEN",
      Material.STICK,
      "STONE",
      Material.COBBLESTONE,
      "IRON",
      Material.IRON_INGOT,
      "GOLDEN",
      Material.GOLD_INGOT,
      "DIAMOND",
      Material.DIAMOND,
      "NETHERITE",
      Material.NETHERITE_INGOT,
      "LEATHER",
      Material.LEATHER,
      "CHAINMAIL",
      Material.IRON_NUGGET
   );
   private static final Map<String, Integer> PIECE_VALUE = Map.of(
      "PICKAXE", 3, "AXE", 3, "SHOVEL", 1, "HOE", 2, "SWORD", 2, "HELMET", 5, "CHESTPLATE", 8, "LEGGINGS", 7, "BOOTS", 4
   );
   private final XpNotifier notifier;
   private final PerkSettings perks;
   private final PerkCooldowns cooldowns;

   public SalvageArtist(XpNotifier notifier, PerkSettings perks, PerkCooldowns cooldowns) {
      this.notifier = notifier;
      this.perks = perks;
      this.cooldowns = cooldowns;
   }

   public void salvage(Player player) {
      if (!this.perks.enabled()) {
         player.sendMessage(Component.text("Perks are currently disabled on this server.", NamedTextColor.RED));
      } else if (this.notifier.levelOf(player, Skill.REPAIR) < 75) {
         player.sendMessage(Component.text("You need level 75 Repair to salvage gear.", NamedTextColor.RED));
      } else {
         ItemStack held = player.getInventory().getItemInMainHand();
         String[] parts = held.getType().name().split("_", 2);
         if (parts.length == 2 && TIER_MATERIAL.containsKey(parts[0]) && PIECE_VALUE.containsKey(parts[1])) {
            if (!this.cooldowns.tryTrigger(player.getUniqueId(), "REPAIR:salvage", this.perks.utilityCooldownSeconds())) {
               long left = this.cooldowns.secondsLeft(player.getUniqueId(), "REPAIR:salvage");
               player.sendMessage(Component.text("Salvage Artist is on cooldown for " + left + "s.", NamedTextColor.RED));
            } else {
               Material material = TIER_MATERIAL.get(parts[0]);
               int fullValue = PIECE_VALUE.get(parts[1]);
               if (material == Material.NETHERITE_INGOT) {
                  // a netherite piece only ever cost one ingot to smith, whatever it is, so paying out per piece
                  // (up to 8 ingots for a chestplate) turned each one into a netherite dupe
                  material = Material.NETHERITE_SCRAP;
                  fullValue = 4;
               }
               boolean highTier = ThreadLocalRandom.current().nextInt(100) < this.perks.repairSalvageArtistChance();
               int amount = highTier ? fullValue : Math.max(1, fullValue / 2);
               held.setAmount(held.getAmount() - 1);
               player.getInventory().addItem(new ItemStack[]{new ItemStack(material, amount)});
               player.sendMessage(
                  Component.text((highTier ? "Salvaged premium materials: " : "Salvaged: ") + amount + "x " + material.name(), NamedTextColor.YELLOW)
               );
               player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.6F, highTier ? 1.4F : 1.0F);
            }
         } else {
            player.sendMessage(Component.text("Hold a tool or piece of armor to salvage it.", NamedTextColor.RED));
         }
      }
   }
}
