/*
 * SuperLog - Save almost all minecraft actions into logs!
 * Copyright (C) 2020 André Sustac
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.andross.superlog.commands;

import fr.andross.superlog.SuperLog;
import fr.andross.superlog.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Command live
 * @version 1.2
 * @author Andross
 */
public class Commandlive implements ICommand {

	@Override
	public void run(@NotNull final SuperLog pl, @NotNull final CommandSender sender, @NotNull final String[] args) {
		if (!sender.hasPermission("superlog.commands.live")) {
			final String noperm = pl.getLogUtils().getColoredString("messages.noperm");
			if (noperm != null) sender.sendMessage(noperm);
			return;
		}

		final String prefix = pl.getLogUtils().getColoredString("messages.prefix");
		if (args.length < 2) {
			sender.sendMessage(prefix + Utils.color("&7Live command arguments:"));
			sender.sendMessage(prefix + Utils.color("  &7- &b<player>&7: player name to live log"));

			// Checking if the player is live logging someone
			final List<String> logging = pl.getLogManager().getLive().entrySet().stream()
					.filter(e -> e.getValue().contains(sender))
					.map(Map.Entry::getKey).collect(Collectors.toList());
			if (!logging.isEmpty()) {
				sender.sendMessage(prefix + Utils.color("  &7- &eYou are actually live logging:"));
				logging.forEach(p -> sender.sendMessage("    &7- " + p));
			}
			return;
		}
		
		final Player p = Bukkit.getPlayer(args[1]);
		if (p == null) {
			sender.sendMessage(prefix + Utils.color("&cPlayer &e" + args[1] + "&c not found."));
			return;
		}
		
		final String pName = p.getName();

		// Getting senders for this target
		final List<CommandSender> senders = pl.getLogManager().getLive().getOrDefault(pName, new ArrayList<>());

		// Already logging, unlogging
		if (senders.contains(sender)) {
			senders.remove(sender);
			pl.getLogManager().getLive().put(pName, senders);
			sender.sendMessage(prefix + Utils.color("&eYou don't live log &2" + pName + "&e anymore."));
		} else {
			// Adding to log
			senders.add(sender);
			pl.getLogManager().getLive().put(pName, senders);
			sender.sendMessage(prefix + Utils.color("&2You are now live logging &a" + pName + "&2."));
		}
	}

	@NotNull
	@Override
	public List<String> getTabCompletition(@NotNull final String[] args) {
		if (args.length == 2) return StringUtil.copyPartialMatches(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), new ArrayList<>());
		return Collections.emptyList();
	}

}
