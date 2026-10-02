package com.stratasmp.strataweapons;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;


public class StrataWeapons extends StrataModule {
   public StrataWeapons(StrataCore core) {
      super(core, "StrataWeapons");
   }

   private SpearCooldownTask spearCooldown;

   public void onDisable() {
      if (this.spearCooldown != null) {
         this.spearCooldown.shutdown();
      }
   }

   public void onEnable() {
      this.saveDefaultConfig();
      WeaponCatalog catalog = new WeaponCatalog(this);
      this.getCommand("givecw").setExecutor(new GiveWeaponCommand(catalog));
      this.getCommand("transferenchants").setExecutor(new TransferEnchantsCommand(catalog));
      this.getServer().getPluginManager().registerEvents(new SoulboundListener(catalog), this);
      this.getServer().getPluginManager().registerEvents(new WeaponAnvilListener(catalog), this);
      SkinPurge purge = new SkinPurge(this, catalog);
      this.getServer().getPluginManager().registerEvents(purge, this);
      this.getCommand("skinpurge").setExecutor(purge);
      SkinsGui skinsGui = new SkinsGui(this, catalog);
      this.getServer().getPluginManager().registerEvents(skinsGui, this);
      this.getCommand("skins").setExecutor(new SkinsCommand(skinsGui));
      this.getCommand("unskin").setExecutor(new UnskinCommand(catalog));
      StrataCharmService charmService = new StrataCharmService(this);
      StrataCharmGui charmGui = new StrataCharmGui(this, catalog, charmService);
      skinsGui.setCharmGui(charmGui);
      charmGui.setSkinsGui(skinsGui);
      this.getServer().getPluginManager().registerEvents(charmGui, this);
      this.getCommand("stratacharm").setExecutor(charmGui);
      this.getCommand("givestratacharm").setExecutor(new GiveStrataCharmCommand(this, charmService));
      KillMessageManager killMessages = new KillMessageManager(this);
      this.getCommand("killmsg").setExecutor(new KillMessageCommand(killMessages));
      this.getServer().getPluginManager().registerEvents(new KillMessageListener(this, catalog, killMessages), this);
      new WeaponAuraTask(catalog).runTaskTimer(this, 0L, 4L);
      if (this.getConfig().getBoolean("spear.remove-attack-cooldown", false)
         || this.getConfig().getBoolean("spear.clear-lunge-cooldown", false)) {
         this.spearCooldown = new SpearCooldownTask(this);
         this.spearCooldown.start();
      }
      this.getLogger().info("StrataWeapons enabled - " + catalog.weaponKeys().size() + " weapon skin(s) and "
         + (catalog.keys().size() - catalog.weaponKeys().size()) + " armour skin(s) loaded.");
   }
}
