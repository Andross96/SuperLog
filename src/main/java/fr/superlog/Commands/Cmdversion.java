package fr.superlog.Commands;

import java.util.ArrayList;
import java.util.List;

import fr.superlog.Log.Log;
import org.bukkit.command.CommandSender;

public class Cmdversion implements Cmd {

	public void run(final Log log, final CommandSender sender, final String[] args) {
		final String prefix = log.getConfig().getMessage("prefix");
		sender.sendMessage(log.getUtils().color(prefix + "&7&m             &3&l[&e&lSuperLog&3&l]&r&7&m             "));
		sender.sendMessage(log.getUtils().color(prefix + "&2Version: &a&l" + log.getPlugin().getDescription().getVersion()));
		sender.sendMessage(log.getUtils().color(prefix + " &r&eFor any bug/questions/suggestions: "));
		sender.sendMessage(log.getUtils().color(prefix + "    &r&evisit the main thread on spigot "));
		sender.sendMessage(log.getUtils().color(prefix + "&7&m                                                "));
	}

	@Override
	public List<String> getTabCompletition(final String[] args) {
		return new ArrayList<>();
	}
}
