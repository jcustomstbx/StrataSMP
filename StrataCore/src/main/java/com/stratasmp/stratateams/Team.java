package com.stratasmp.stratateams;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

public class Team {
   public final UUID id;
   public String name;
   public UUID owner;
   public final Set<UUID> members = new LinkedHashSet<>();
   public boolean friendlyFire = false;
   public Location home;

   public Team(UUID id, String name, UUID owner) {
      this.id = id;
      this.name = name;
      this.owner = owner;
      this.members.add(owner);
   }
}
