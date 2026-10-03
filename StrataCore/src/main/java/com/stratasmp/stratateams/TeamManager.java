package com.stratasmp.stratateams;

import com.stratasmp.strataeconomy.api.StrataApi;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;

public class TeamManager {
   private static final long INVITE_EXPIRY_MILLIS = 300000L;
   private final StrataModule plugin;
   private final File file;
   private final StrataApi economy;
   private final long creationCost;
   private final Map<UUID, Team> teams = new LinkedHashMap<>();
   private final Map<UUID, UUID> membership = new HashMap<>();
   private final Map<UUID, PendingInvite> invites = new HashMap<>();

   public TeamManager(StrataModule plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "teams.yml");
      this.creationCost = plugin.getConfig().getLong("team-creation-cost", 30000L);
      this.economy = StrataApi.get();
      this.load();
   }

   private void load() {
      if (this.file.exists()) {
         YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.file);
         ConfigurationSection teamsSection = cfg.getConfigurationSection("teams");
         if (teamsSection != null) {
            for (String idStr : teamsSection.getKeys(false)) {
               ConfigurationSection t = teamsSection.getConfigurationSection(idStr);
               if (t != null) {
                  UUID id = UUID.fromString(idStr);
                  String name = t.getString("name");
                  UUID owner = UUID.fromString(t.getString("owner"));
                  Team team = new Team(id, name, owner);
                  team.friendlyFire = t.getBoolean("friendly-fire", false);

                  for (String memberStr : t.getStringList("members")) {
                     UUID memberUuid = UUID.fromString(memberStr);
                     team.members.add(memberUuid);
                     this.membership.put(memberUuid, id);
                  }

                  ConfigurationSection h = t.getConfigurationSection("home");
                  if (h != null) {
                     World world = Bukkit.getWorld(h.getString("world", ""));
                     if (world != null) {
                        team.home = new Location(
                           world, h.getDouble("x"), h.getDouble("y"), h.getDouble("z"), (float)h.getDouble("yaw"), (float)h.getDouble("pitch")
                        );
                     } else {
                        // the world may load after us (Multiverse); keep the raw values and resolve them later
                        team.unresolvedHome = new java.util.LinkedHashMap<>(h.getValues(false));
                     }
                  }

                  this.teams.put(id, team);
               }
            }
         }
      }
   }

   public void save() {
      YamlConfiguration cfg = new YamlConfiguration();

      for (Team team : this.teams.values()) {
         String base = "teams." + team.id;
         cfg.set(base + ".name", team.name);
         cfg.set(base + ".owner", team.owner.toString());
         cfg.set(base + ".friendly-fire", team.friendlyFire);
         cfg.set(base + ".members", team.members.stream().map(UUID::toString).toList());
         if (team.home == null && team.unresolvedHome != null) {
            cfg.set(base + ".home", team.unresolvedHome);
         } else if (team.home != null && team.home.getWorld() != null) {
            cfg.set(base + ".home.world", team.home.getWorld().getName());
            cfg.set(base + ".home.x", team.home.getX());
            cfg.set(base + ".home.y", team.home.getY());
            cfg.set(base + ".home.z", team.home.getZ());
            cfg.set(base + ".home.yaw", (double)team.home.getYaw());
            cfg.set(base + ".home.pitch", (double)team.home.getPitch());
         }
      }

      try {
         cfg.save(this.file);
      } catch (IOException var5) {
         this.plugin.getLogger().warning("Couldn't save teams.yml: " + var5.getMessage());
      }
   }

   /** Turns saved homes into locations once their worlds exist. */
   public void resolveHomes() {
      for (Team team : this.teams.values()) {
         if (team.home == null && team.unresolvedHome != null) {
            Object worldName = team.unresolvedHome.get("world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName.toString());
            if (world != null) {
               java.util.Map<String, Object> h = team.unresolvedHome;
               team.home = new Location(world, num(h.get("x")), num(h.get("y")), num(h.get("z")),
                  (float)num(h.get("yaw")), (float)num(h.get("pitch")));
               team.unresolvedHome = null;
            }
         }
      }
   }

   private static double num(Object value) {
      return value instanceof Number n ? n.doubleValue() : 0.0;
   }

   public Team getTeam(UUID playerUuid) {
      UUID teamId = this.membership.get(playerUuid);
      return teamId == null ? null : this.teams.get(teamId);
   }

   public Team getTeamByName(String name) {
      for (Team team : this.teams.values()) {
         if (team.name.equalsIgnoreCase(name)) {
            return team;
         }
      }

      return null;
   }

   public boolean sameTeamAndFriendlyFireOff(UUID a, UUID b) {
      Team teamA = this.getTeam(a);
      return teamA == null ? false : teamA.members.contains(b) && !teamA.friendlyFire;
   }

   public long getCreationCost() {
      return this.creationCost;
   }

   public String createTeam(Player owner, String name) {
      if (this.getTeam(owner.getUniqueId()) != null) {
         return "You're already in a team.";
      } else if (name.length() < 3 || name.length() > 16) {
         return "Team names must be 3-16 characters.";
      } else if (this.getTeamByName(name) != null) {
         return "A team with that name already exists.";
      } else {
         if (this.economy != null && this.creationCost > 0L) {
            if (!this.economy.has(owner.getUniqueId(), this.creationCost)) {
               return "You need " + this.economy.format(this.creationCost) + " to start a team.";
            }

            this.economy.withdraw(owner.getUniqueId(), this.creationCost);
         }

         Team team = new Team(UUID.randomUUID(), name, owner.getUniqueId());
         this.teams.put(team.id, team);
         this.membership.put(owner.getUniqueId(), team.id);
         this.save();
         return null;
      }
   }

   public String invite(Player owner, Player target) {
      Team team = this.getTeam(owner.getUniqueId());
      if (team == null) {
         return "You're not in a team.";
      } else if (!team.owner.equals(owner.getUniqueId())) {
         return "Only the team owner can invite players.";
      } else if (this.getTeam(target.getUniqueId()) != null) {
         return target.getName() + " is already in a team.";
      } else {
         this.invites.put(target.getUniqueId(), new PendingInvite(team.id, System.currentTimeMillis() + 300000L));
         return null;
      }
   }

   public String acceptInvite(Player player) {
      PendingInvite invite = this.invites.get(player.getUniqueId());
      if (invite == null || invite.expiresAt() < System.currentTimeMillis()) {
         this.invites.remove(player.getUniqueId());
         return "You don't have a pending team invite.";
      } else if (this.getTeam(player.getUniqueId()) != null) {
         return "You're already in a team.";
      } else {
         Team team = this.teams.get(invite.teamId());
         if (team == null) {
            return "That team no longer exists.";
         } else {
            team.members.add(player.getUniqueId());
            this.membership.put(player.getUniqueId(), team.id);
            this.invites.remove(player.getUniqueId());
            this.save();
            return null;
         }
      }
   }

   public boolean denyInvite(Player player) {
      return this.invites.remove(player.getUniqueId()) != null;
   }

   public String kick(Player owner, String targetName) {
      Team team = this.getTeam(owner.getUniqueId());
      if (team == null) {
         return "You're not in a team.";
      } else if (!team.owner.equals(owner.getUniqueId())) {
         return "Only the team owner can remove members.";
      } else {
         UUID targetUuid = this.resolveMember(team, targetName);
         if (targetUuid == null) {
            return targetName + " isn't in your team.";
         } else if (targetUuid.equals(owner.getUniqueId())) {
            return "You can't remove yourself - use /myteam disband instead.";
         } else {
            team.members.remove(targetUuid);
            this.membership.remove(targetUuid);
            this.save();
            return null;
         }
      }
   }

   public String rename(Player owner, String newName) {
      Team team = this.getTeam(owner.getUniqueId());
      if (team == null) {
         return "You're not in a team.";
      } else if (!team.owner.equals(owner.getUniqueId())) {
         return "Only the team owner can rename the team.";
      } else if (newName.length() < 3 || newName.length() > 16) {
         return "Team names must be 3-16 characters.";
      } else if (this.getTeamByName(newName) != null) {
         return "A team with that name already exists.";
      } else {
         team.name = newName;
         this.save();
         return null;
      }
   }

   public String disband(Player owner) {
      Team team = this.getTeam(owner.getUniqueId());
      if (team == null) {
         return "You're not in a team.";
      } else if (!team.owner.equals(owner.getUniqueId())) {
         return "Only the team owner can disband the team.";
      } else {
         for (UUID memberUuid : team.members) {
            this.membership.remove(memberUuid);
         }

         this.teams.remove(team.id);
         this.save();
         return null;
      }
   }

   public String leave(Player player) {
      Team team = this.getTeam(player.getUniqueId());
      if (team == null) {
         return "You're not in a team.";
      } else if (team.owner.equals(player.getUniqueId())) {
         return "The owner can't leave - use /myteam disband instead.";
      } else {
         team.members.remove(player.getUniqueId());
         this.membership.remove(player.getUniqueId());
         this.save();
         return null;
      }
   }

   public String toggleFriendlyFire(Player owner) {
      Team team = this.getTeam(owner.getUniqueId());
      if (team == null) {
         return "You're not in a team.";
      } else if (!team.owner.equals(owner.getUniqueId())) {
         return "Only the team owner can change friendly fire.";
      } else {
         team.friendlyFire = !team.friendlyFire;
         this.save();
         return null;
      }
   }

   public String setHome(Player owner) {
      Team team = this.getTeam(owner.getUniqueId());
      if (team == null) {
         return "You're not in a team.";
      } else if (!team.owner.equals(owner.getUniqueId())) {
         return "Only the team owner can set the team home.";
      } else {
         team.home = owner.getLocation();
         this.save();
         return null;
      }
   }

   /** Removes a member by UUID, for callers (the GUI) that already know who they mean. */
   public String kick(Player owner, UUID targetUuid) {
      Team team = this.getTeam(owner.getUniqueId());
      if (team == null) {
         return "You're not in a team.";
      } else if (!team.owner.equals(owner.getUniqueId())) {
         return "Only the team owner can remove members.";
      } else if (targetUuid.equals(owner.getUniqueId())) {
         return "You can't remove yourself - use /myteam disband instead.";
      } else if (!team.members.remove(targetUuid)) {
         return "That player isn't in your team.";
      } else {
         this.membership.remove(targetUuid);
         this.save();
         return null;
      }
   }

   private UUID resolveMember(Team team, String name) {
      for (UUID memberUuid : team.members) {
         OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(memberUuid);
         if (name.equalsIgnoreCase(offlinePlayer.getName())) {
            return memberUuid;
         }
      }

      return null;
   }
}
