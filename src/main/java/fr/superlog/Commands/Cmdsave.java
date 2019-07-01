package fr.superlog.Commands;

import java.util.ArrayList;
import java.util.List;

import fr.superlog.Log.Log;
import org.bukkit.command.CommandSender;

public class Cmdsave implements Cmd {

	public void run(final Log log, final CommandSender sender, final String[] args) {
		final String prefix = log.getConfig().getMessage("prefix");
		if(!sender.hasPermission("superlog.commands.save")) {
			sender.sendMessage(log.getUtils().color(prefix + log.getConfig().getMessage("noperm")));
			return;
		}
		
		log.getPlugin().getServer().getScheduler().runTaskAsynchronously(log.getPlugin(), () -> {
			final int count = log.saveAll();
			log.getPlugin().getServer().getScheduler().scheduleSyncDelayedTask(log.getPlugin(), () -> {
				sender.sendMessage(log.getUtils().color(prefix + "&2Logs: " + count + " saved."));
			});
		});
	}

	public List<String> getTabCompletition(final String[] args) {
		return new ArrayList<>();
	}
}
