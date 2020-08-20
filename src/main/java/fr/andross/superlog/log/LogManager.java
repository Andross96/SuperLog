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
package fr.andross.superlog.log;

import fr.andross.superlog.SuperLog;
import fr.andross.superlog.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * Log manager
 * @version 1.2
 * @author Andross
 */
public final class LogManager {
    private final SuperLog pl;
    private final Map<File, List<String>> cache = new HashMap<>();
    private final Map<String, List<CommandSender>> live = new HashMap<>();
    private boolean debug = false;

    /**
     * Should not be instantiated. Use {@link SuperLog#getLogManager()} instead.
     * @param pl the plugin instance
     * @param sender sender
     */
    public LogManager(@NotNull final SuperLog pl, @NotNull final CommandSender sender) {
        this.pl = pl;

        // Running utilities
        pl.getLogUtils().gzipOldLogs(sender);
        pl.getLogUtils().deleteOldLogs(sender);
    }

    /**
     * Saving a log
     * @param log the event logged
     */
    public void log(@NotNull final LogSerializer log) {
        if (debug) pl.getLogger().info("[Debug] Start log processing..");

        // Adding log
        final File f = pl.getLogUtils().generateFile(log);
        if (f == null) {
            if (debug) pl.getLogger().info("[Debug] Stopping log processing, as I can not create a log file (maybe invalid 'logs-format'?)");
            return;
        }
        String cancelled = "";
        if (log.getEvent() instanceof Cancellable) cancelled = ((Cancellable) log.getEvent()).isCancelled() ? "[Cancelled]" : "";
        final String message = "[" + pl.getLogUtils().getCurrentLogTime() + "][" + log.getEvent().getEventName() + "]" + cancelled + ": " + log.getMessage();

        synchronized(cache) {
            // Adding directly in file?
            if (pl.getConfig().getInt("save-delay") == 0) {
                save(f, new String[] { message });
                return;
            }

            // Or adding in cache
            final List<String> logs = cache.getOrDefault(f, new ArrayList<>());
            logs.add(message);
            cache.put(f, logs);
        }

        if (debug) pl.getLogger().info("[Debug] Log: OK");

        // Live logging:
        if (live.isEmpty() || log.getPlayerName() == null) return;
        final List<CommandSender> senders = live.get(log.getPlayerName());
        if (senders != null && !senders.isEmpty()) {
            String logLiveMessage = pl.getConfig().getString("logs-live-format");
            if (logLiveMessage != null) {
                logLiveMessage = Utils.color(logLiveMessage.replace("{TIME}", pl.getLogUtils().getCurrentLogLiveTime())
                        .replace("{EVENT}", log.getEvent().getEventName())
                        .replace("{LOG}", log.getMessage()));
                for (final CommandSender sender : senders)
                    sender.sendMessage(logLiveMessage);
            }
        }
    }

    /**
     * Saving logs into the file
     * @param f the file
     * @param messages the logs
     * @return if the file was correctly saved, or not
     */
    private boolean save(@NotNull final File f, @NotNull final String[] messages) {
        // Check file (create folders etc.)
        if (!f.exists()) {
            try {
                final File directory = f.getParentFile();
                if (!directory.exists()) directory.mkdirs();
                f.createNewFile();
            } catch (final Exception e) {
                pl.getLogger().log(Level.WARNING, "Can not create file '" + f.getName() + "'.", e);
                return false;
            }
        }

        // Write log
        try (final Writer writer = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8)) {
            for (final String message : messages)
                writer.append(message).append(System.lineSeparator());
        } catch (final Exception e) {
            pl.getLogger().log(Level.WARNING, "Error writing logs in '" + f.getName() + "'.", e);
            return false;
        }

        // Clearing cache
        if (cache.containsKey(f)) cache.get(f).clear();
        return true;
    }

    /**
     * Saving all logs
     * @return the amount of files saved
     */
    public int saveAll() {
        int count = 0;

        synchronized (cache) {
            final Iterator<Map.Entry<File, List<String>>> i = cache.entrySet().iterator();
            while (i.hasNext()) {
                final Map.Entry<File, List<String>> map = i.next();
                final List<String> logs = map.getValue();
                if (logs == null || logs.isEmpty()) {
                    i.remove();
                    continue;
                }
                if (save(map.getKey(), logs.toArray(new String[0]))) count++;
            }
        }

        if (!pl.isEnabled() || count == 0) return count;
        String logMessage = pl.getLogUtils().getColoredString("messages.logs-saved");
        if (logMessage != null) {
            final String prefix = pl.getLogUtils().getColoredString("messages.prefix");
            logMessage = logMessage.replace("{LOGS}", Integer.toString(count));
            if (pl.getConfig().getBoolean("logs-in-console")) Bukkit.getConsoleSender().sendMessage(prefix + logMessage);
            if (pl.getConfig().getBoolean("logs-in-game")) Bukkit.broadcast(prefix + logMessage, "superlog.getlogs");
        }

        return count;
    }

    /**
     * Sending alert commands if logged
     * @param pName player name
     * @param command command name
     */
    public void alertCommands(@NotNull final String pName, @NotNull final String command) {
        // Should the command be alerted?
        final Set<String> alertCommands = pl.getLogConfig().getAlertCommands();
        if (!alertCommands.contains("*") && alertCommands.stream().noneMatch(command::startsWith)) return;

        // Preparing message
        String alertMessage = pl.getConfig().getString("commands-alert.message");
        if (alertMessage == null) return; // no message set
        final String prefix = Utils.color(pl.getConfig().getString("messages.prefix"));
        alertMessage = Utils.color(alertMessage.replace("{PLAYER}", pName).replace("{COMMAND}", command));
        final String finalMessage = prefix + alertMessage;

        // Sending message
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("superlog.getlogs") && !p.getName().equals(pName))
                .forEach(p -> p.sendMessage(finalMessage));
    }

    /**
     * Map of live logging players
     * @return map of live logging players
     */
    @NotNull
    public Map<String, List<CommandSender>> getLive() {
        return live;
    }

    /**
     * If the plugin should debug every log
     * @return true if the plugin should debug every log, otherwise false
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * Set if the plugin should debug all logging events
     * @param debug if the plugin should debug events
     */
    public void setDebug(final boolean debug) {
        this.debug = debug;
    }
}
