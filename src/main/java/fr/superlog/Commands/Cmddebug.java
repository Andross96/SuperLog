package fr.superlog.Commands;

import java.util.ArrayList;
import java.util.List;

import fr.superlog.Log.Log;
import org.bukkit.command.CommandSender;

public class Cmddebug implements Cmd {

	public void run(final Log log, final CommandSender sender, final String[] args) {
		final String prefix = log.getConfig().getMessage("prefix");
		if(!sender.isOp()) {
			sender.sendMessage(log.getUtils().color(prefix + log.getConfig().getMessage("noperm")));
			return;
		}

		log.toggleDebug();

		if(!log.isDebug()) sender.sendMessage(log.getUtils().color(prefix + "&eDebug: &c&lOFF&e."));
		else sender.sendMessage(log.getUtils().color(prefix + "&eDebug: &2&lON&e."));
	}

	public List<String> getTabCompletition(final String[] args) {
		return new ArrayList<>();
	}
	
}