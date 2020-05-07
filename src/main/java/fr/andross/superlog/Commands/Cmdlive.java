package fr.andross.superlog.Commands;

import fr.andross.superlog.Log.Log;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Cmdlive implements Cmd {

	public void run(final Log log, final CommandSender sender, final String[] args) {
		final String prefix = log.getConfig().getMessage("prefix");
		if(!sender.hasPermission("superlog.commands.save")) {
			sender.sendMessage(log.getUtils().color(prefix + log.getConfig().getMessage("noperm")));
			return;
		}
		
		if(args.length < 2) {
			sender.sendMessage(log.getUtils().color(prefix + "&7Live command arguments:"));
			sender.sendMessage(log.getUtils().color(prefix + "  &7\u2937 &b<player>&7: player name to live log"));

			// checking if the player is live logging someone
			log.getLogLive().forEach((p,s) -> {
				if(s.getName().equals(sender.getName())) sender.sendMessage(log.getUtils().color(prefix + "  &7\u2937 &eYou are actually live logging &2" + p + "&e."));
			});
			return;
		}
		
		final Player p = log.getPlugin().getServer().getPlayer(args[1]);
		if(p == null) {
			sender.sendMessage(log.getUtils().color(prefix + "&cPlayer &e" + args[1] + "&c not found."));
			return;
		}
		
		final String pName = p.getName();

		// First entrance, we add the sender
		if(!log.getLogLive().containsValue(sender)){
			log.getLogLive().put(pName, sender);
			sender.sendMessage(log.getUtils().color(prefix + "&2You are now live logging &a" + pName + "&2."));
			return;
		}

		// Else we have to modify or remove actual sender logging
		for(final Map.Entry<String, CommandSender> entry : log.getLogLive().entrySet()){
			if(!entry.getValue().equals(sender)) continue;

			if(entry.getKey().equals(pName)){ // Disabling log live
				log.getLogLive().remove(pName);
				sender.sendMessage(log.getUtils().color(prefix + "&eYou don't live log &2" + pName + "&e anymore."));
				return;
			}

			// Else we change the player
			log.getLogLive().put(pName, sender);
			sender.sendMessage(log.getUtils().color(prefix + "&2You are now live logging &a" + pName + "&2."));
			return;
		}
	}

	public List<String> getTabCompletition(final String[] args) {
		if(args.length < 3) return null;
		else return new ArrayList<>();
	}

}
