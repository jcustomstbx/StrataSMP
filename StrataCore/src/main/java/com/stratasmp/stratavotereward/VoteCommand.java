package com.stratasmp.stratavotereward;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import com.stratasmp.stratacore.StrataModule;
import org.jetbrains.annotations.NotNull;

public class VoteCommand implements CommandExecutor {
   private final StrataModule plugin;

   public VoteCommand(StrataModule plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
      sender.sendMessage(VoteReminder.buildVoteMessage(this.plugin));
      return true;
   }
}
