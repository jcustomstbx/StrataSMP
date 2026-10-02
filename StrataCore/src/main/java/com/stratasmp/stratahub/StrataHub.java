package com.stratasmp.stratahub;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;


public final class StrataHub extends StrataModule {
   public StrataHub(StrataCore core) {
      super(core, "StrataHub");
   }


    private HubData hubData;
    private PlayerLocations playerLocations;
    private Msg msg;
    private NpcGear npcGear;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.msg = new Msg(this);
        this.npcGear = new NpcGear(this);
        this.hubData = new HubData(this);
        this.playerLocations = new PlayerLocations(this);

        getServer().getPluginManager().registerEvents(new EntryNpcListener(this), this);
        getServer().getPluginManager().registerEvents(new SmpLocationTracker(this), this);
        getServer().getPluginManager().registerEvents(new SmpLockdownListener(this), this);
        getServer().getPluginManager().registerEvents(new HubCommandGuard(this), this);
        getServer().getPluginManager().registerEvents(new VoidRescueListener(this), this);
        getServer().getPluginManager().registerEvents(new ClosedWorldListener(this), this);

        OpBeaconBeam beaconBeam = new OpBeaconBeam(this);
        getServer().getPluginManager().registerEvents(beaconBeam, this);
        beaconBeam.start();
        new SafeZoneOutline(this).start();
        new AntiFloodClickable(this).hook();
        getServer().getPluginManager().registerEvents(new BarrierNoClip(this), this);
        getServer().getPluginManager().registerEvents(new BoatWaterOnlyGuard(this), this);
        getServer().getPluginManager().registerEvents(new SafeZoneCombatGuard(this), this);
        SafeZoneMobGuard mobGuard = new SafeZoneMobGuard(this);
        getServer().getPluginManager().registerEvents(mobGuard, this);
        mobGuard.start();
        new RtpCooldownLimiter(this).start();
        getServer().getPluginManager().registerEvents(new RankRtpWarmup(this), this);

        HubCommand command = new HubCommand(this);
        getCommand("stratahub").setExecutor(command);

        getLogger().info("StrataHub enabled - " + hubData.entryNpcIds().size() + " entry NPC(s) configured.");
    }

    public HubData hubData() {
        return hubData;
    }

    public PlayerLocations playerLocations() {
        return playerLocations;
    }

    public Msg msg() {
        return msg;
    }

    NpcGear npcGear() {
        return npcGear;
    }
}
