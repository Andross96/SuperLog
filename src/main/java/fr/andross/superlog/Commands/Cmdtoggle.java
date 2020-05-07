package fr.andross.superlog.Commands;

import fr.andross.superlog.Log.Log;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class Cmdtoggle implements Cmd {

	@Override
	public void run(final Log log, final CommandSender sender, final String[] args) {
		final String prefix = log.getConfig().getMessage("prefix");
		if(!sender.hasPermission("superlog.commands.toggle")) {
			sender.sendMessage(log.getUtils().color(prefix + log.getConfig().getMessage("noperm")));
			return;
		}

		log.getConfig().toggleEnabled();

		if(log.getConfig().isNotEnabled()) sender.sendMessage(log.getUtils().color(prefix + "&eSuperLog: &c&lOFF&e."));
		else sender.sendMessage(log.getUtils().color(prefix + "&eSuperLog: &2&lON&e."));
		
	}

	public List<String> getTabCompletition(final String[] args) {
		return new ArrayList<>();
	}

}
