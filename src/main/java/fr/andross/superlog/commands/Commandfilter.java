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
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Filter command
 * @version 1.2
 * @author Andross
 */
public class Commandfilter implements ICommand {
    private final List<String> help = Stream.of(
            "&7Filtrer command argument:",
            "&3/log filter &b<playerName> <eventName>&7: create a 'filtered.log' about playerName & the eventName)",
            "  &7- &b<playerName>: player name to filtrer",
            "  &7- &b<eventName>: name of the event to filtrer"
    ).map(Utils::color).collect(Collectors.toList());

    @Override
    public void run(@NotNull final SuperLog pl, @NotNull final CommandSender sender, @NotNull final String[] args) {
        if (!sender.hasPermission("superlog.commands.filter")) {
            final String noperm = pl.getLogUtils().getColoredString("messages.noperm");
            if (noperm != null) sender.sendMessage(noperm);
            return;
        }

        final String prefix = pl.getLogUtils().getColoredString("messages.prefix");
        if (args.length < 3) {
            help.forEach(l -> sender.sendMessage(prefix + l));
            return;
        }

        final String playerName = args[1];
        final String eventName = args[2];

        Bukkit.getScheduler().runTaskAsynchronously(pl, () -> {
            try {
                // Checking player log
                final File folder = new File(pl.getDataFolder() + File.separator +
                        "logs" + File.separator +
                        "players" + File.separator +
                        playerName + File.separator);
                if (!folder.isDirectory()) throw new Exception("&cThe player &e" + playerName + "&c doesn't have logs.");

                // Processing
                final List<String> lines = new ArrayList<>();
                final File[] files = folder.listFiles();
                if (files == null) throw new Exception("&cThe player &e" + playerName + "&c doesn't have logs.");

                final String logFormat = pl.getConfig().getString("logs-format");
                if (logFormat == null) throw new Exception("&cInvalid '&elogs-format&c' in config.");
                final String logFormatExtension = logFormat.substring(logFormat.lastIndexOf("."));
                for (final File file : files) {
                    // Checking file
                    int lastIndex = file.getName().lastIndexOf(".");
                    if (lastIndex < 0) continue;
                    if (!file.getName().substring(lastIndex).equals(logFormatExtension)) continue;

                    // Reading & filtering
                    try (final BufferedReader br = new BufferedReader(new FileReader(file))){
                        for (String line; (line = br.readLine()) != null;) {
                            if (!line.contains(eventName)) continue;
                            lines.add(line);
                        }
                    } catch (final Exception e) {
                        sender.sendMessage(prefix + Utils.color("&cUnable to read " + file.getName()));
                        if (pl.getLogManager().isDebug()) e.printStackTrace();
                    }
                }

                // Writing in file
                if (lines.isEmpty()) throw new Exception("&cNo logs found for player &e" + playerName + "&c and event &e" + eventName + "&c.");
                final File filtered = new File(pl.getDataFolder() + File.separator + "logs_filtered" + File.separator, "filtered_" + playerName + "_" + eventName + ".log");
                final File directory = filtered.getParentFile();
                if (!directory.exists()) directory.mkdirs();
                if (filtered.isFile()) filtered.delete();
                filtered.createNewFile();
                final Writer writer = new OutputStreamWriter(new FileOutputStream(filtered, true), StandardCharsets.UTF_8);
                for (final String newLine : lines) writer.append(newLine).append(System.lineSeparator());
                writer.close();
                throw new Exception("&2"+ filtered.getName() + " created, with &a" + lines.size() + " logs&2.");
            } catch (final Exception e) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(pl, () -> sender.sendMessage(prefix + Utils.color(e.getMessage())));
            }
        });
    }

    @NotNull
    @Override
    public List<String> getTabCompletition(@NotNull final String[] args) {
        return Collections.emptyList();
    }
}
