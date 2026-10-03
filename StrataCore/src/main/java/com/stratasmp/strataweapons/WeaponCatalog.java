package com.stratasmp.strataweapons;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import com.stratasmp.stratacore.StrataModule;

public class WeaponCatalog {
   private final StrataModule plugin;
   private final NamespacedKey soulboundKey;
   private final NamespacedKey weaponKeyKey;
   private final Map<String, ConfigurationSection> definitions = new HashMap<>();
   private final Map<String, String> byMaterialAndModel = new HashMap<>();
   private final Set<String> armorKeys = new HashSet<>();

   public WeaponCatalog(StrataModule plugin) {
      this.plugin = plugin;
      this.soulboundKey = new NamespacedKey(plugin, "soulbound");
      this.weaponKeyKey = new NamespacedKey(plugin, "weapon_key");
      this.load();
   }

   private void load() {
      ConfigurationSection weapons = this.plugin.getConfig().getConfigurationSection("weapons");
      if (weapons != null) {
         for (String key : weapons.getKeys(false)) {
            ConfigurationSection def = weapons.getConfigurationSection(key);
            if (def == null) {
               this.plugin.getLogger().warning("Weapon '" + key + "' is not a section, skipped.");
               continue;
            }
            this.definitions.put(key.toLowerCase(), def);
            Material material = Material.matchMaterial(def.getString("material", "NETHERITE_SWORD"));
            if (material == null) {
               material = Material.NETHERITE_SWORD;
            }

            int modelData = def.getInt("custom-model-data", -1);
            if (modelData >= 0) {
               this.byMaterialAndModel.put(material.name() + ":" + modelData, key.toLowerCase());
            }
         }
      }

      ConfigurationSection armor = this.plugin.getConfig().getConfigurationSection("armor-skins");
      if (armor != null) {
         for (String key : armor.getKeys(false)) {
            ConfigurationSection def = armor.getConfigurationSection(key);
            if (def == null || def.getString("item-model") == null) {
               this.plugin.getLogger().warning("Armour skin '" + key + "' has no item-model, skipped.");
               continue;
            }
            this.definitions.put(key.toLowerCase(), def);
            this.armorKeys.add(key.toLowerCase());
         }
      }
   }

   public boolean has(String key) {
      return this.definitions.containsKey(key.toLowerCase());
   }

   /** Every skin, weapons and armour. */
   public Set<String> keys() {
      return this.definitions.keySet();
   }

   public Set<String> weaponKeys() {
      Set<String> out = new HashSet<>(this.definitions.keySet());
      out.removeAll(this.armorKeys);
      return out;
   }

   public boolean isArmorSkin(String key) {
      return this.armorKeys.contains(key.toLowerCase());
   }

   /** Armour skins list before weapon skins in the menus. */
   public int menuOrder(String key) {
      return isArmorSkin(key) ? 0 : 1;
   }

   public ItemStack build(String key) {
      ConfigurationSection def = this.definitions.get(key.toLowerCase());
      if (def == null || this.armorKeys.contains(key.toLowerCase())) {
         return null;
      } else {
         Material material = Material.matchMaterial(def.getString("material", "NETHERITE_SWORD"));
         if (material == null) {
            material = Material.NETHERITE_SWORD;
         }

         ItemStack item = new ItemStack(material);
         ItemMeta meta = item.getItemMeta();
         int modelData = def.getInt("custom-model-data", -1);
         if (modelData >= 0) {
            meta.setCustomModelData(modelData);
         }

         String name = def.getString("display-name", key);
         List<String> gradient = def.getStringList("gradient");
         if (gradient.size() == 2) {
            TextColor from = TextColor.fromHexString(gradient.get(0));
            TextColor to = TextColor.fromHexString(gradient.get(1));
            meta.displayName(GradientText.build(name, from, to));
         } else {
            meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
         }

         ConfigurationSection enchants = def.getConfigurationSection("enchantments");
         if (enchants != null) {
            for (String enchantKey : enchants.getKeys(false)) {
               Enchantment enchant = (Enchantment)Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchantKey.toLowerCase()));
               if (enchant == null) {
                  this.plugin.getLogger().warning("Unknown enchantment '" + enchantKey + "' in weapon '" + key + "'");
               } else {
                  meta.addEnchant(enchant, enchants.getInt(enchantKey), true);
               }
            }
         }

         meta.getPersistentDataContainer().set(this.soulboundKey, PersistentDataType.BYTE, (byte)1);
         meta.getPersistentDataContainer().set(this.weaponKeyKey, PersistentDataType.STRING, key.toLowerCase());
         item.setItemMeta(meta);
         return item;
      }
   }

   public boolean isSoulbound(ItemStack item) {
      return item != null && item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().has(this.soulboundKey, PersistentDataType.BYTE) : false;
   }

   /** A soulbound weapon, or a bundle / shulker box with one inside it. */
   public boolean holdsSoulbound(ItemStack item) {
      if (item == null || !item.hasItemMeta()) {
         return false;
      }
      return isSoulbound(item) || nestedItems(item).stream().anyMatch(this::isSoulbound);
   }

   public static List<ItemStack> nestedItems(ItemStack item) {
      ItemMeta meta = item.getItemMeta();
      if (meta instanceof BundleMeta bundle) {
         return bundle.getItems();
      }
      if (meta instanceof BlockStateMeta state && state.hasBlockState() && state.getBlockState() instanceof ShulkerBox box) {
         return Arrays.stream(box.getInventory().getContents()).filter(Objects::nonNull).toList();
      }
      return List.of();
   }

   public String weaponKeyOf(ItemStack item) {
      if (item != null && item.hasItemMeta()) {
         ItemMeta meta = item.getItemMeta();
         String tagged = (String)meta.getPersistentDataContainer().get(this.weaponKeyKey, PersistentDataType.STRING);
         if (tagged != null) {
            return tagged;
         } else {
            return !meta.hasCustomModelData() ? null : this.byMaterialAndModel.get(item.getType().name() + ":" + meta.getCustomModelData());
         }
      } else {
         return null;
      }
   }

   public String displayNameOf(String key) {
      ConfigurationSection def = this.definitions.get(key.toLowerCase());
      return def == null ? key : def.getString("display-name", key);
   }

   /** StrataCharm needed to unlock this skin - per-skin "charm-cost", else skin-charms.default-cost (1). */
   public int charmCostOf(String key) {
      int fallback = Math.max(1, this.plugin.getConfig().getInt("skin-charms.default-cost", 1));
      ConfigurationSection def = this.definitions.get(key.toLowerCase());
      return def == null ? fallback : Math.max(1, def.getInt("charm-cost", fallback));
   }

   public Color auraColorOf(String key) {
      ConfigurationSection def = this.definitions.get(key.toLowerCase());
      if (def == null) {
         return null;
      } else {
         String hex = def.getString("aura-color");
         if (hex == null) {
            return null;
         } else {
            TextColor textColor = TextColor.fromHexString(hex);
            return textColor == null ? null : Color.fromRGB(textColor.red(), textColor.green(), textColor.blue());
         }
      }
   }

   /* ---- skins: apply a catalog weapon's look to any melee weapon the player owns ---- */

   public static boolean isSkinnable(Material material) {
      if (material == Material.MACE || material == Material.TRIDENT) {
         return true;
      }
      String n = material.name();
      return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_SPEAR");
   }

   private Component nameFor(ConfigurationSection def, String key) {
      String name = def.getString("display-name", key);
      List<String> gradient = def.getStringList("gradient");
      if (gradient.size() == 2) {
         TextColor from = TextColor.fromHexString(gradient.get(0));
         TextColor to = TextColor.fromHexString(gradient.get(1));
         return GradientText.build(name, from, to);
      }
      return Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false);
   }

   /**
    * Stamps {@code key}'s model, name and identity onto {@code held} without
    * changing its material. Enchantments already on the weapon are kept.
    * @return false if the item type can't be skinned or the key is unknown.
    */
   public boolean applySkinTo(ItemStack held, String key) {
      if (held == null) {
         return false;
      }
      ConfigurationSection def = this.definitions.get(key.toLowerCase());
      if (def == null) {
         return false;
      }
      if (!canSkin(held.getType(), key)) {
         return false;
      }
      if (this.armorKeys.contains(key.toLowerCase())) {
         return applyArmorSkin(held, def, key);
      }
      ItemMeta meta = held.getItemMeta();
      int modelData = def.getInt("custom-model-data", -1);
      if (modelData >= 0) {
         meta.setCustomModelData(modelData);
      }
      meta.displayName(nameFor(def, key));
      meta.getPersistentDataContainer().set(this.soulboundKey, PersistentDataType.BYTE, (byte) 1);
      meta.getPersistentDataContainer().set(this.weaponKeyKey, PersistentDataType.STRING, key.toLowerCase());
      held.setItemMeta(meta);
      return true;
   }

   public static boolean isArmorPiece(Material material) {
      return pieceOf(material) != null;
   }

   private static String pieceOf(Material material) {
      String n = material.name();
      if (n.endsWith("_HELMET")) {
         return "helmet";
      }
      if (n.endsWith("_CHESTPLATE")) {
         return "chestplate";
      }
      if (n.endsWith("_LEGGINGS")) {
         return "leggings";
      }
      return n.endsWith("_BOOTS") ? "boots" : null;
   }

   private boolean isCrown(ConfigurationSection def) {
      return "crown".equalsIgnoreCase(def.getString("type"));
   }

   /** Whether a skin can go on this kind of item: swords/axes/maces/tridents for weapons, armour pieces for armour, helmets only for crowns. */
   public boolean canSkin(Material material, String key) {
      ConfigurationSection def = this.definitions.get(key.toLowerCase());
      if (def == null) {
         return false;
      }
      if (!this.armorKeys.contains(key.toLowerCase())) {
         if (!isSkinnable(material)) {
            return false;
         }
         if (def.getBoolean("restrict-material", false)) {
            Material required = Material.matchMaterial(def.getString("material", ""));
            return required != null && material == required;
         }
         return true;
      }
      String piece = pieceOf(material);
      return piece != null && (!isCrown(def) || piece.equals("helmet"));
   }

   public String wrongItemHint(String key) {
      ConfigurationSection def = this.definitions.get(key.toLowerCase());
      if (def != null && this.armorKeys.contains(key.toLowerCase())) {
         return isCrown(def)
            ? "Hold the helmet you want to give this crown to, then click."
            : "Hold the helmet, chestplate, leggings or boots you want to skin, then click.";
      }
      return "Hold the sword / axe / mace / trident / spear you want to skin, then click.";
   }

   /**
    * Armour skins are two components: item_model (the icon) and equippable's asset id (what it looks like
    * on the player). A crown has no asset id, so the client draws the item model on the head instead.
    * Everything else about the piece - material, protection, enchants - is untouched.
    */
   private boolean applyArmorSkin(ItemStack held, ConfigurationSection def, String key) {
      if (!canSkin(held.getType(), key)) {
         return false;
      }
      Equippable current = held.getData(DataComponentTypes.EQUIPPABLE);
      if (current == null) {
         return false;
      }
      boolean crown = isCrown(def);
      String base = def.getString("item-model");
      Key itemModel = Key.key(crown ? base : base + "_" + pieceOf(held.getType()));

      ItemMeta meta = held.getItemMeta();
      meta.displayName(nameFor(def, key));
      meta.getPersistentDataContainer().set(this.soulboundKey, PersistentDataType.BYTE, (byte) 1);
      meta.getPersistentDataContainer().set(this.weaponKeyKey, PersistentDataType.STRING, key.toLowerCase());
      held.setItemMeta(meta);

      // components go on after the meta, or setItemMeta would write the old values back over them
      Equippable.Builder worn = crown
         ? Equippable.equippable(current.slot()).equipSound(current.equipSound()).damageOnHurt(current.damageOnHurt())
         : current.toBuilder().assetId(Key.key(def.getString("equipment", base)));
      held.setData(DataComponentTypes.EQUIPPABLE, worn);
      held.setData(DataComponentTypes.ITEM_MODEL, itemModel);
      return true;
   }

   /**
    * Takes a skin back off {@code held}: the model, soulbound tag and identity go, the material and
    * enchantments stay. The name is cleared only if it is still the skin's own, so a rename done
    * afterwards is kept.
    * @return the skin key that was removed, or null if the item wasn't skinned.
    */
   public String removeSkinFrom(ItemStack held) {
      String key = weaponKeyOf(held);
      if (key == null) {
         return null;
      }
      ItemMeta meta = held.getItemMeta();
      ConfigurationSection def = this.definitions.get(key.toLowerCase());
      if (def != null && meta.hasDisplayName()) {
         PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
         if (plain.serialize(meta.displayName()).equals(plain.serialize(nameFor(def, key)))) {
            meta.displayName(null);
         }
      }
      meta.setCustomModelData(null);
      meta.getPersistentDataContainer().remove(this.soulboundKey);
      meta.getPersistentDataContainer().remove(this.weaponKeyKey);
      held.setItemMeta(meta);
      if (this.armorKeys.contains(key.toLowerCase())) {
         held.resetData(DataComponentTypes.EQUIPPABLE);
         held.resetData(DataComponentTypes.ITEM_MODEL);
      }
      return key;
   }

   /**
    * Removes the skin from {@code item} and from anything skinned inside it (a bundle or shulker box).
    * Used on death drops so what lands on the ground is the plain item.
    * @return true if anything was changed.
    */
   public boolean stripSkins(ItemStack item) {
      if (item == null || !item.hasItemMeta()) {
         return false;
      }
      boolean changed = removeSkinFrom(item) != null;
      ItemMeta meta = item.getItemMeta();
      if (meta instanceof BundleMeta bundle && !bundle.getItems().isEmpty()) {
         List<ItemStack> inner = new ArrayList<>(bundle.getItems());
         boolean any = false;
         for (ItemStack piece : inner) {
            any |= stripSkins(piece);
         }
         if (any) {
            bundle.setItems(inner);
            item.setItemMeta(bundle);
            changed = true;
         }
      } else if (meta instanceof BlockStateMeta state && state.hasBlockState() && state.getBlockState() instanceof ShulkerBox box) {
         boolean any = false;
         for (ItemStack piece : box.getInventory().getContents()) {
            if (piece != null) {
               any |= stripSkins(piece);
            }
         }
         if (any) {
            state.setBlockState(box);
            item.setItemMeta(state);
            changed = true;
         }
      }
      return changed;
   }

   private ItemStack armorMenuIcon(ConfigurationSection def, String key, boolean owned) {
      boolean crown = isCrown(def);
      String base = def.getString("item-model");
      ItemStack icon = new ItemStack(crown ? Material.PAPER : Material.NETHERITE_CHESTPLATE);
      ItemMeta meta = icon.getItemMeta();
      meta.setItemModel(NamespacedKey.fromString(crown ? base : base + "_chestplate"));
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
      meta.displayName(nameFor(def, key));
      String hold = crown ? "Click while holding a helmet" : "Click while holding a piece of armour";
      meta.lore(List.of(
            Component.text(owned ? hold : "Not unlocked", owned ? NamedTextColor.GRAY : NamedTextColor.RED)
                  .decoration(TextDecoration.ITALIC, false),
            Component.text(owned ? (crown ? "to wear this crown." : "to apply it. Works on any armour piece.") : "Available in the store.",
                  NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
      icon.setItemMeta(meta);
      return icon;
   }

   /** Menu icon for the /skins GUI - the real model, plus lore. */
   public ItemStack menuIcon(String key, boolean owned) {
      ConfigurationSection def = this.definitions.get(key.toLowerCase());
      if (def == null) {
         return null;
      }
      if (this.armorKeys.contains(key.toLowerCase())) {
         return armorMenuIcon(def, key, owned);
      }
      Material iconMaterial = Material.NETHERITE_SWORD;
      if (def.getBoolean("restrict-material", false)) {
         Material required = Material.matchMaterial(def.getString("material", ""));
         if (required != null) {
            iconMaterial = required;
         }
      }
      ItemStack icon = new ItemStack(iconMaterial);
      ItemMeta meta = icon.getItemMeta();
      int modelData = def.getInt("custom-model-data", -1);
      if (modelData >= 0) {
         meta.setCustomModelData(modelData);
      }
      meta.displayName(nameFor(def, key));
      meta.lore(List.of(
            Component.text(owned ? "Click while holding a sword," : "Not unlocked",
                  owned ? NamedTextColor.GRAY : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
            Component.text(owned ? "axe, mace or trident to apply." : "Available in the store.",
                  NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
      icon.setItemMeta(meta);
      return icon;
   }
}
