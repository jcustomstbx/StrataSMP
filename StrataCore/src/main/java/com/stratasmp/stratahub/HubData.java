package com.stratasmp.stratahub;

import org.bukkit.configuration.file.YamlConfiguration;
import com.stratasmp.stratacore.StrataModule;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Which Citizens NPCs act as SMP entry points - set with /stratahub addentry/removeentry. */
public final class HubData {

    private final File file;
    private final Set<Integer> entryNpcIds = new HashSet<>();
    private final Set<Integer> comingSoonNpcIds = new HashSet<>();
    private boolean smpLockdown;

    public HubData(StrataModule plugin) {
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<Integer> ids = cfg.getIntegerList("entry-npcs");
        entryNpcIds.addAll(ids);
        comingSoonNpcIds.addAll(cfg.getIntegerList("coming-soon-npcs"));
        smpLockdown = cfg.getBoolean("smp-lockdown", false);
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("entry-npcs", List.copyOf(entryNpcIds));
        cfg.set("coming-soon-npcs", List.copyOf(comingSoonNpcIds));
        cfg.set("smp-lockdown", smpLockdown);
        try {
            cfg.save(file);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't save StrataHub data.yml", e);
        }
    }

    public boolean isSmpLockdown() {
        return smpLockdown;
    }

    public void setSmpLockdown(boolean lockdown) {
        this.smpLockdown = lockdown;
        save();
    }

    public boolean isEntryNpc(int npcId) {
        return entryNpcIds.contains(npcId);
    }

    public boolean addEntry(int npcId) {
        boolean added = entryNpcIds.add(npcId);
        if (added) {
            save();
        }
        return added;
    }

    public boolean removeEntry(int npcId) {
        boolean removed = entryNpcIds.remove(npcId);
        if (removed) {
            save();
        }
        return removed;
    }

    public Set<Integer> entryNpcIds() {
        return Set.copyOf(entryNpcIds);
    }

    public boolean isComingSoonNpc(int npcId) {
        return comingSoonNpcIds.contains(npcId);
    }

    public boolean addComingSoon(int npcId) {
        boolean added = comingSoonNpcIds.add(npcId);
        if (added) {
            save();
        }
        return added;
    }

    public boolean removeComingSoon(int npcId) {
        boolean removed = comingSoonNpcIds.remove(npcId);
        if (removed) {
            save();
        }
        return removed;
    }

    public Set<Integer> comingSoonNpcIds() {
        return Set.copyOf(comingSoonNpcIds);
    }
}
