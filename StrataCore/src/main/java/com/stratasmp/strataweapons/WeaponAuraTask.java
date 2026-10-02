package com.stratasmp.strataweapons;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class WeaponAuraTask extends BukkitRunnable {
   private final WeaponCatalog catalog;

   public WeaponAuraTask(WeaponCatalog catalog) {
      this.catalog = catalog;
   }

   public void run() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         PlayerInventory inv = player.getInventory();
         this.spawnIfWeapon(player, inv.getItemInMainHand(), true);
         this.spawnIfWeapon(player, inv.getItemInOffHand(), false);
      }
   }

   private void spawnIfWeapon(Player player, ItemStack item, boolean mainHand) {
      String key = this.catalog.weaponKeyOf(item);
      if (key != null) {
         Color color = this.catalog.auraColorOf(key);
         if (color != null) {
            Location handLocation = this.approximateHandLocation(player, mainHand);
            DustOptions dust = new DustOptions(color, 1.3F);
            player.getWorld().spawnParticle(Particle.DUST, handLocation, 4, 0.18, 0.18, 0.18, 0.0, dust);
         }
      }
   }

   private Location approximateHandLocation(Player player, boolean mainHand) {
      Location eye = player.getEyeLocation();
      Vector direction = eye.getDirection().normalize();
      Vector right = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();
      boolean isRightSide = mainHand == (player.getMainHand() == MainHand.RIGHT);
      Vector sideOffset = right.multiply(isRightSide ? 0.45 : -0.45);
      Vector forwardOffset = direction.multiply(0.3);
      return eye.clone().add(forwardOffset).add(sideOffset).subtract(0.0, 0.55, 0.0);
   }
}
