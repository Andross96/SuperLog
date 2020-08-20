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
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Debug command
 * @version 1.2
 * @author Andross
 */
public class Commanddebug implements ICommand {

	@Override
	public void run(@NotNull final SuperLog pl, @NotNull final CommandSender sender, @NotNull final String[] args) {
		if (!sender.isOp()) {
			final String noperm = pl.getLogUtils().getColoredString("messages.noperm");
			if (noperm != null) sender.sendMessage(noperm);
			return;
		}

		final String prefix = pl.getLogUtils().getColoredString("messages.prefix");
		pl.getLogManager().setDebug(!pl.getLogManager().isDebug());

		if (!pl.getLogManager().isDebug()) sender.sendMessage(prefix + Utils.color("&eDebug: &c&lOFF&e."));
		else sender.sendMessage(prefix + Utils.color("&eDebug: &2&lON&e."));
	}

	@NotNull
	@Override
	public List<String> getTabCompletition(@NotNull final String[] args) {
		return Collections.emptyList();
	}
	
}