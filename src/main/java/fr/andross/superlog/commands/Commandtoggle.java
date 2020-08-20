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
 * Command toggle
 * @version 1.2
 * @author Andross
 */
public class Commandtoggle implements ICommand {

	@Override
	public void run(@NotNull final SuperLog pl, @NotNull final CommandSender sender, @NotNull final String[] args) {
		if (!sender.hasPermission("superlog.commands.toggle")) {
			final String noperm = pl.getLogUtils().getColoredString("messages.noperm");
			if (noperm != null) sender.sendMessage(noperm);
			return;
		}

		pl.getLogConfig().setEnabled(!pl.getLogConfig().isEnabled());

		final String prefix = pl.getLogUtils().getColoredString("messages.prefix");
		if (pl.getLogConfig().isEnabled()) sender.sendMessage(prefix + Utils.color("&eSuperLog: &c&lOFF&e."));
		else sender.sendMessage(prefix + Utils.color("&eSuperLog: &2&lON&e."));
	}

	@NotNull
	@Override
	public List<String> getTabCompletition(@NotNull final String[] args) {
		return Collections.emptyList();
	}

}
