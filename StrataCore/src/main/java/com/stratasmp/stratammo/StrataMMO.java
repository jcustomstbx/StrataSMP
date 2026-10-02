package com.stratasmp.stratammo;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;
import com.stratasmp.stratammo.commands.MmoCommand;
import com.stratasmp.stratammo.listeners.AlchemyListener;
import com.stratasmp.stratammo.listeners.CombatListener;
import com.stratasmp.stratammo.listeners.EnchantingListener;
import com.stratasmp.stratammo.listeners.FarmingListener;
import com.stratasmp.stratammo.listeners.FishingListener;
import com.stratasmp.stratammo.listeners.JoinQuitListener;
import com.stratasmp.stratammo.listeners.MiningListener;
import com.stratasmp.stratammo.listeners.RepairListener;
import com.stratasmp.stratammo.listeners.WoodcuttingListener;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;

public class StrataMMO extends StrataModule {
   public StrataMMO(StrataCore core) { super(core, "StrataMMO"); }

   private DataManager smpData;
   private Set<String> smpWorlds;

   @Override
   public void onEnable() {
      saveDefaultConfig();
      smpWorlds=worldSet("worlds.smp",List.of("world","world_nether","world_the_end"));

      LevelCurve levelCurve=new LevelCurve(getConfig().getInt("level-curve.base",100),getConfig().getInt("level-curve.per-level",40),getConfig().getInt("level-cap",0));
      XpValues xpValues=new XpValues(getConfig().getConfigurationSection("xp"));
      PerkSettings perkSettings=new PerkSettings(getConfig().getConfigurationSection("perks"));
      boolean antiFarm=getConfig().getBoolean("anti-farm.enabled",true);
      int milestoneInterval=getConfig().getInt("milestone-interval",25);
      boolean stratasRewardEnabled=getConfig().getBoolean("stratas-reward.enabled",false);
      int stratasRewardInterval=getConfig().getInt("stratas-reward.interval",10);
      long stratasRewardAmount=getConfig().getLong("stratas-reward.amount",1250L);

      smpData=new DataManager(this,"smp");
      PlacedBlockTracker placedTracker=new PlacedBlockTracker();
      getServer().getPluginManager().registerEvents(placedTracker,this);
      ProfileContext smp=createProfile("smp",smpWorlds,smpData,levelCurve,xpValues,perkSettings,placedTracker,antiFarm,
              milestoneInterval,stratasRewardEnabled,stratasRewardInterval,stratasRewardAmount);

      PluginCommand command=getCommand("mmo");
      command.setExecutor(smp.command());
      command.setTabCompleter(smp.command());

      long autosaveTicks=getConfig().getLong("autosave-interval",5L)*60L*20L;
      getServer().getScheduler().runTaskTimer(this,smpData::saveAll,autosaveTicks,autosaveTicks);
      if(getConfig().getBoolean("web.push-enabled",false)){
         String apiUrl=getConfig().getString("web.api-url","");
         String apiKey=getConfig().getString("web.api-key","");
         long pushTicks=getConfig().getLong("web.push-interval-seconds",60L)*20L;
         LeaderboardPusher pusher=new LeaderboardPusher(this,smpData,levelCurve,apiUrl,apiKey);
         getServer().getScheduler().runTaskTimerAsynchronously(this,()->{smpData.refreshLeaderboardCache();pusher.push();},20L,pushTicks);
      } else {
         getServer().getScheduler().runTaskTimerAsynchronously(this,()->smpData.refreshLeaderboardCache(),20L,1200L);
      }
      getLogger().info("StrataMMO enabled - "+Skill.values().length+" SMP skills.");
   }

   private ProfileContext createProfile(String profile,Set<String> worlds,DataManager dataManager,LevelCurve curve,XpValues xp,
                                       PerkSettings settings,PlacedBlockTracker placed,boolean antiFarm,int milestoneInterval,
                                       boolean stratas,int stratasInterval,long stratasAmount){
      XpNotifier notifier=new XpNotifier(dataManager,curve,milestoneInterval,stratas,stratasInterval,stratasAmount,profile,worlds);
      PerkCooldowns cooldowns=new PerkCooldowns();
      ActiveUltimates ultimates=new ActiveUltimates();
      UltimatePerks perks=new UltimatePerks(notifier,cooldowns,settings,ultimates);
      SalvageArtist salvage=new SalvageArtist(notifier,settings,cooldowns);
      PluginManager manager=getServer().getPluginManager();
      manager.registerEvents(new JoinQuitListener(dataManager,worlds),this);
      manager.registerEvents(new MiningListener(xp,notifier,placed,antiFarm,settings,cooldowns,ultimates,this),this);
      manager.registerEvents(new WoodcuttingListener(xp,notifier,placed,antiFarm,settings,cooldowns,ultimates,this),this);
      manager.registerEvents(new FarmingListener(xp,notifier,settings,cooldowns,ultimates,this),this);
      manager.registerEvents(new CombatListener(xp,notifier),this);
      manager.registerEvents(new FishingListener(xp,notifier,settings,cooldowns,ultimates),this);
      manager.registerEvents(new EnchantingListener(xp,notifier,settings,cooldowns,ultimates,this),this);
      manager.registerEvents(new RepairListener(xp,notifier,settings,ultimates,this),this);
      manager.registerEvents(new AlchemyListener(xp,notifier,settings,cooldowns,ultimates,this),this);
      for(Player player:getServer().getOnlinePlayers())if(inProfile(worlds,player))dataManager.load(player);
      return new ProfileContext(new MmoCommand(dataManager,curve,perks,salvage));
   }

   private Set<String> worldSet(String path,List<String> defaults){
      List<String> configured=getConfig().getStringList(path);
      if(configured.isEmpty())configured=defaults;
      return configured.stream().map(s->s.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
   }

   private boolean inProfile(Set<String> profile,Player player){return profile.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));}

   @Override
   public void onDisable(){
      if(smpData!=null)smpData.saveAll();
   }

   private record ProfileContext(MmoCommand command){}
}
