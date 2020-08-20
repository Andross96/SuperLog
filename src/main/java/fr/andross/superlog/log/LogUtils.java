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
import fr.andross.superlog.utils.LoggedEvent;
import fr.andross.superlog.utils.Utils;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.hanging.HangingEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.server.PluginEvent;
import org.bukkit.event.vehicle.VehicleEvent;
import org.bukkit.event.weather.WeatherEvent;
import org.bukkit.event.world.ChunkEvent;
import org.bukkit.event.world.WorldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

/**
 * Utils for logging
 * @version 1.2
 * @author Andross
 */
public final class LogUtils {
    private final SuperLog pl;
    private final File dataFolder;
    private final DateFormat dayFormat = new SimpleDateFormat("dd");
    private final DateFormat monthFormat = new SimpleDateFormat("MM");
    private final DateFormat yearFormat = new SimpleDateFormat("yy");
    private final DateFormat all = new SimpleDateFormat("HH:mm:ss");

    /**
     * Should not be instantiated. Use {@link SuperLog#getLogUtils()} instead.
     * @param pl the plugin instance
     */
    public LogUtils(@NotNull final SuperLog pl) {
        this.pl = pl;
        this.dataFolder = pl.getDataFolder();
    }

    /**
     * Generate a log file
     * @param log the event logged
     * @return the generated file
     */
    @Nullable
    public File generateFile(@NotNull final LogSerializer log) {
        final String fileDirectory = dataFolder + File.separator + "logs" + File.separator;
        final Date d = new Date();
        final String fileName = pl.getConfig().getString("logs-format");
        if (fileName == null) return null;
        return new File((log.getPlayerName() != null ? fileDirectory + "players" + File.separator + log.getPlayerName() + File.separator : fileDirectory),
                fileName.replace("{EVENT}", log.getEvent().getEventName())
                        .replace("{TYPE}", log.getEventType())
                        .replace("{DAY}", dayFormat.format(d))
                        .replace("{MONTH}", monthFormat.format(d))
                        .replace("{YEAR}", yearFormat.format(d)));
    }

    /**
     * Get a formated string of the current time for logs
     * @return current log time
     */
    @NotNull
    public String getCurrentLogTime() {
        return pl.getLogConfig().getDateFormat().format(new Date());
    }

    /**
     * Get a formated string of the current time for live logs
     * @return current live log time
     */
    @NotNull
    public String getCurrentLogLiveTime() {
        return all.format(new Date());
    }

    /**
     * Get a colored string from the config
     * @param node node in the config
     * @return the string colored, otherwise null
     */
    @Nullable
    public String getColoredString(@NotNull final String node) {
        final String message = pl.getConfig().getString(node);
        return message == null ? null : Utils.color(message);
    }

    /**
     * Check if an update is available
     * <i>(executed async)</i>
     */
    public void checkUpdate() {
        try {
            final HttpsURLConnection c = (HttpsURLConnection) new URL("https://api.spigotmc.org/legacy/update.php?resource=65399").openConnection();
            c.setRequestMethod("GET");
            final String lastVersion = new BufferedReader(new InputStreamReader(c.getInputStream())).readLine();
            if (!lastVersion.equals(pl.getDescription().getVersion()))
                pl.getLogger().info("A newer version (v" + lastVersion + ") is available!");
        } catch (final IOException e) {
            pl.getLogger().info("Unable to communicate with the spigot api to check for newer versions.");
        }
    }

    /**
     * GZipping old logs
     * @param sender sender
     */
    protected void gzipOldLogs(@NotNull final CommandSender sender) {
        final int days = pl.getConfig().getInt("gzip-logs-after");
        if (days == 0) return;
        if (days < 0) {
            sender.sendMessage(Utils.color("&cInvalid '&egzip-logs-after' in config.yml: must indicate a positive number of days"));
            return;
        }

        // Nothing to GZip?
        if (!new File(dataFolder + File.separator + "logs").isDirectory() || !new File(dataFolder + File.separator + "logs" + File.separator + "players").isDirectory()) return;

        final int count = gzipOldLog(dataFolder + File.separator + "logs" + File.separator, TimeUnit.DAYS.toMillis(days));
        if (count != 0) sender.sendMessage(Utils.color("&aGZipped &e" + count + "&a old logs."));
    }

    /**
     * Gzipping a log file
     * @param directoryName directory name
     * @param dayPassed day passed
     * @return amount of log file gzipped
     */
    private int gzipOldLog(@NotNull final String directoryName, final long dayPassed) {
        int count = 0;
        final File oneFolder = new File(directoryName);
        if (!oneFolder.isDirectory()) return count;
        final File[] files = oneFolder.listFiles();
        if (files == null) return count;

        for (final File file : files) {
            // If it's another directory
            if (file.isDirectory()) {
                count += gzipOldLog(file.getAbsolutePath(), dayPassed);
                continue;
            }
            // Not old enough?
            if ((System.currentTimeMillis() - file.lastModified()) < dayPassed) continue;

            // Already a GZipped file?
            if (getFileExtension(file).equalsIgnoreCase("gz")) continue;

            // Writing
            try (final FileInputStream newFile = new FileInputStream(file);
                 final GZIPOutputStream gzip = new GZIPOutputStream(new FileOutputStream(new File(file.getParent(), file.getName() + ".gz")))){
                byte[] buffer = new byte[1024];
                int len;
                while ((len=newFile.read(buffer)) != -1) gzip.write(buffer, 0, len);
                gzip.finish();
            } catch (final Exception e) {
                pl.getLogger().log(Level.WARNING, "Error during GZipping " + file.getName() + ".", e);
                continue;
            }
            file.delete();
            count++;
        }
        return count;
    }

    /**
     * Deleting old logs
     * @param sender sender
     */
    protected void deleteOldLogs(@NotNull final CommandSender sender) {
        final int days = pl.getConfig().getInt("delete-logs.after");
        if (days == 0) return;
        if (days < 0) {
            sender.sendMessage(Utils.color("&cInvalid '&edelete-logs.after&c' in config.yml: must indicate a positive number of days"));
            return;
        }
        if (!new File(dataFolder + File.separator + "logs").isDirectory()) return;

        final int count = deleteOldLog(dataFolder + File.separator + "logs" + File.separator, TimeUnit.DAYS.toMillis(days), pl.getConfig().getBoolean("delete-logs.even-gzipped"));
        if (count != 0) sender.sendMessage(Utils.color("&aDeleted &e" + count + "&a old logs."));
    }

    /**
     * Deleting an old log file
     * @param directoryName directory name
     * @param dayPassed days passed
     * @param evenGZippedLogs even gzipped logs file
     * @return the amount of deleted files
     */
    private int deleteOldLog(@NotNull final String directoryName, final long dayPassed, final boolean evenGZippedLogs) {
        int count = 0;
        final File oneFolder = new File(directoryName);
        final File[] files = oneFolder.listFiles();
        if (files == null) return count;
        for (final File file : files) {
            // If it's another directory
            if (file.isDirectory()) {
                count += deleteOldLog(file.getAbsolutePath(), dayPassed, evenGZippedLogs);
                continue;
            }
            // Not old enough?
            if ((System.currentTimeMillis() - file.lastModified()) < dayPassed) continue;

            // Checking if it deletes even GZipped files
            if (!evenGZippedLogs && getFileExtension(file).equalsIgnoreCase("gz")) continue;

            file.delete();
            count++;
        }
        return count;
    }

    /**
     * Getting a file extension type
     * @param f file
     * @return the file extension
     */
    @NotNull
    private String getFileExtension(@NotNull final File f) {
        final String fileName = f.getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
    }

    /**
     * Creating an event executor for a group of event
     * @param event event type
     * @param loggedEvent logged event configuration
     * @return an event executor, null if not supported
     */
    @Nullable
    public EventExecutor createEventExecutor(@NotNull final Class<? extends Event> event, @NotNull final LoggedEvent loggedEvent) {
        EventExecutor eventExecutor = null;
        Class<?> superClasses = event.getSuperclass();
        while (superClasses != null) {
            switch (superClasses.getSimpleName()) {
                case "PlayerEvent":
                    eventExecutor = (bukkitListener, bukkitEvent) -> {
                        if (pl.getLogConfig().isEnabled()) logPlayerEvent(bukkitEvent, loggedEvent);
                    };
                    break;
                case "BlockEvent":
                    eventExecutor = (bukkitListener, bukkitEvent) -> {
                        if (pl.getLogConfig().isEnabled()) logBlockEvent(bukkitEvent, loggedEvent);
                    };
                    break;
                case "EntityEvent":
                    eventExecutor = (bukkitListener, bukkitEvent) -> {
                        if (pl.getLogConfig().isEnabled()) logEntityEvent(bukkitEvent, loggedEvent);
                    };
                    break;
                case "HangingEvent":
                    eventExecutor = (bukkitListener, bukkitEvent) -> {
                        if (pl.getLogConfig().isEnabled()) logHangingEvent(bukkitEvent, loggedEvent);
                    };
                    break;
                case "InventoryEvent":
                    eventExecutor = (bukkitListener, bukkitEvent) -> {
                        if (pl.getLogConfig().isEnabled()) logInventoryEvent(bukkitEvent, loggedEvent);
                    };
                    break;
                case "ServerEvent":
                    eventExecutor = (bukkitListener, bukkitEvent) -> {
                        if (pl.getLogConfig().isEnabled()) logServerEvent(bukkitEvent, loggedEvent);
                    };
                    break;
                case "PluginEvent":
                    eventExecutor = (bukkitListener, bukkitEvent) -> {
                        if (pl.getLogConfig().isEnabled()) logPluginEvent(bukkitEvent, loggedEvent);
                    };
                    break;
                case "VehicleEvent":
                    eventExecutor = (bukkitListener, bukkitEvent) -> {
                        if (pl.getLogConfig().isEnabled()) logVehicleEvent(bukkitEvent, loggedEvent);
                    };
                    break;
                case "WeatherEvent":
                    eventExecutor = (bukkitListener, bukkitEvent) -> {
                        if (pl.getLogConfig().isEnabled()) logWeatherEvent(bukkitEvent, loggedEvent);
                    };
                    break;
                case "WorldEvent":
                    eventExecutor = (bukkitListener, bukkitEvent) -> {
                        if (pl.getLogConfig().isEnabled()) logWorldEvent(bukkitEvent, loggedEvent);
                    };
                    break;
                case "ChunkEvent":
                    eventExecutor = (bukkitListener, bukkitEvent) -> {
                        if (pl.getLogConfig().isEnabled()) logChunkEvent(bukkitEvent, loggedEvent);
                    };
                    break;
                default: superClasses = superClasses.getSuperclass(); break;
            }
            if (eventExecutor != null) return eventExecutor;
        }
        return null;
    }

    /**
     * Logging a PlayerEvent
     * @param event the event runned
     * @param loggedEvent the log event configuration
     */
    public void logPlayerEvent(@NotNull final Event event, @NotNull final LoggedEvent loggedEvent) {
        final boolean debug = pl.getLogManager().isDebug();
        if (debug) pl.getLogger().info("[Debug] Started " + event.getEventName());

        // Getting Player
        final Player p = ((PlayerEvent) event).getPlayer();

        // Is Citizens NPC?
        if (pl.getLogConfig().isCitizensEnabled() && p.hasMetadata("NPC")) return;

        // Starting serialization
        final LogSerializer serializer = new LogSerializer(pl, loggedEvent, event);
        // Ignored event?
        if (serializer.isAnIgnoredEvent(p.getName(), null)) {
            if (debug) pl.getLogger().info("[Debug] Event should not be logged. Ignoring it.");
            return;
        }

        // Serializing fields
        if (serializer.serializeFields(event)) {
            if (debug) pl.getLogger().info("[Debug] Fields should not be logged. Ignoring it.");
            return;
        }

        // Serializing the rest of the event
        serializer.serialize(p, null);
        serializer.setPlayerName(p.getName());
        serializer.setEventType("PlayerEvents");
        if (debug) pl.getLogger().info("[Debug] Serialization: OK.");

        // Adding log
        pl.getLogManager().log(serializer);
    }

    /**
     * Logging a BlockEvent
     * @param event the event runned
     * @param loggedEvent the log event configuration
     */
    public void logBlockEvent(@NotNull final Event event, @NotNull final LoggedEvent loggedEvent) {
        // We ignore a BlockMultiPlaceEvent
        if (event instanceof BlockMultiPlaceEvent) return;

        final boolean debug = pl.getLogManager().isDebug();
        if (debug) pl.getLogger().info("[Debug] Started " + event.getEventName());

        // Getting Block
        final Block b = ((BlockEvent) event).getBlock();

        // Block event related to a player?
        Player p = null;
        try {
            Field f = event.getClass().getDeclaredField("player");
            f.setAccessible(true);
            p = (Player) f.get(event);
        } catch (final Exception ignored) { /* we don't care */ }

        // Starting serialization
        final LogSerializer serializer = new LogSerializer(pl, loggedEvent, event);
        // Ignored event?
        if (serializer.isAnIgnoredEvent(b.getType().name(), null)) {
            if (debug) pl.getLogger().info("[Debug] Event should not be logged. Ignoring it.");
            return;
        }

        // Serializing fields
        if (serializer.serializeFields(event)) {
            if (debug) pl.getLogger().info("[Debug] Fields should not be logged. Ignoring it.");
            return;
        }

        // Serializing the rest of the event
        serializer.serialize(b, null);
        if (p != null) serializer.setPlayerName(p.getName());
        serializer.setEventType("BlockEvents");
        if (debug) pl.getLogger().info("[Debug] Serialization: OK.");

        // Adding log
        pl.getLogManager().log(serializer);
    }

    /**
     * Logging an EntityEvent
     * @param event the event runned
     * @param loggedEvent the log event configuration
     */
    public void logEntityEvent(@NotNull final Event event, @NotNull final LoggedEvent loggedEvent) {
        final boolean debug = pl.getLogManager().isDebug();
        if (debug) pl.getLogger().info("[Debug] Started " + event.getEventName());

        // Getting Entity, or the player if it's a player related event
        final Entity e = ((EntityEvent) event).getEntity();
        final Player p = (e instanceof Player) ? ((Player) e) : null;

        // Starting serialization
        final LogSerializer serializer = new LogSerializer(pl, loggedEvent, event);
        // Ignored event?
        if (serializer.isAnIgnoredEvent(e.getType().name(), null)) {
            if (debug) pl.getLogger().info("[Debug] Event should not be logged. Ignoring it.");
            return;
        }

        // Serializing fields
        if (serializer.serializeFields(event)) {
            if (debug) pl.getLogger().info("[Debug] Fields should not be logged. Ignoring it.");
            return;
        }

        // Serializing the rest of the event
        serializer.serialize(e, null);
        if (p != null) serializer.setPlayerName(p.getName());
        serializer.setEventType("EntityEvents");
        if (debug) pl.getLogger().info("[Debug] Serialization: OK.");

        // Adding log
        pl.getLogManager().log(serializer);
    }

    /**
     * Logging a HangingEvent
     * @param event the event runned
     * @param loggedEvent the log event configuration
     */
    public void logHangingEvent(@NotNull final Event event, @NotNull final LoggedEvent loggedEvent) {
        final boolean debug = pl.getLogManager().isDebug();
        if (debug) pl.getLogger().info("[Debug] Started " + event.getEventName());

        // Getting the Entity
        final Entity e = ((HangingEvent) event).getEntity();

        // Starting serialization
        final LogSerializer serializer = new LogSerializer(pl, loggedEvent, event);
        // Serializing fields
        if (serializer.serializeFields(event)) {
            if (debug) pl.getLogger().info("[Debug] Fields should not be logged. Ignoring it.");
            return;
        }

        // Hanging event related to a player?
        final Player p;
        try{
            Field f = event.getClass().getDeclaredField("player");
            f.setAccessible(true);
            p = (Player) f.get(event);
            serializer.serialize(p, "PLAYER");
            serializer.setPlayerName(p.getName());
        } catch (final Exception ignored) { /* we don't care */ }

        // Serializing the rest of the event
        serializer.serialize(e, null);
        serializer.setEventType("HangingEvents");
        if (debug) pl.getLogger().info("[Debug] Serialization: OK.");

        // Adding log
        pl.getLogManager().log(serializer);
    }

    /**
     * Logging an InventoryEvent
     * @param event the event runned
     * @param loggedEvent the log event configuration
     */
    public void logInventoryEvent(@NotNull final Event event, @NotNull final LoggedEvent loggedEvent) {
        final boolean debug = pl.getLogManager().isDebug();
        if (debug) pl.getLogger().info("[Debug] Started " + event.getEventName());

        // Getting Inventory
        final InventoryView iv = ((InventoryEvent) event).getView();
        final Inventory i = iv.getTopInventory();
        final HumanEntity p = iv.getPlayer();

        // Starting serialization
        final LogSerializer serializer = new LogSerializer(pl, loggedEvent, event);
        // Ignored event?
        if (serializer.isAnIgnoredEvent(i.getType().name(), null)) {
            if (debug) pl.getLogger().info("[Debug] Event should not be logged. Ignoring it.");
            return;
        }

        // Serializing fields
        if (serializer.serializeFields(event)) {
            if (debug) pl.getLogger().info("[Debug] Fields should not be logged. Ignoring it.");
            return;
        }

        // Serializing the rest of the event
        serializer.serialize(iv, p.getLocation());
        serializer.serialize(p, "PLAYER");
        serializer.setPlayerName(p.getName());
        serializer.setEventType("InventoryEvents");
        if (debug) pl.getLogger().info("[Debug] Serialization: OK.");

        // Adding log
        pl.getLogManager().log(serializer);
    }

    /**
     * Logging a ServerEvent
     * @param event the event runned
     * @param loggedEvent the log event configuration
     */
    public void logServerEvent(@NotNull final Event event, @NotNull final LoggedEvent loggedEvent) {
        final boolean debug = pl.getLogManager().isDebug();
        if (debug) pl.getLogger().info("[Debug] Started " + event.getEventName());

        // Starting serialization
        final LogSerializer serializer = new LogSerializer(pl, loggedEvent, event);

        // Serializing fields
        if (serializer.serializeFields(event)) {
            if (debug) pl.getLogger().info("[Debug] Fields should not be logged. Ignoring it.");
            return;
        }

        // Serializing the rest of the event
        serializer.setEventType("ServerEvents");
        if (debug) pl.getLogger().info("[Debug] Serialization: OK.");

        // Adding log
        pl.getLogManager().log(serializer);
    }

    /**
     * Logging a PluginEvent
     * @param event the event runned
     * @param loggedEvent the log event configuration
     */
    public void logPluginEvent(@NotNull final Event event, @NotNull final LoggedEvent loggedEvent) {
        final boolean debug = pl.getLogManager().isDebug();
        if (debug) pl.getLogger().info("[Debug] Started " + event.getEventName());

        // Getting plugin
        final Plugin plugin = ((PluginEvent) event).getPlugin();

        // Starting serialization
        final LogSerializer serializer = new LogSerializer(pl, loggedEvent, event);
        // Ignored event?
        if (serializer.isAnIgnoredEvent(plugin.getName(), null)) {
            if (debug) pl.getLogger().info("[Debug] Event should not be logged. Ignoring it.");
            return;
        }

        // Serializing fields
        if (serializer.serializeFields(event)) {
            if (debug) pl.getLogger().info("[Debug] Fields should not be logged. Ignoring it.");
            return;
        }

        // Serializing the rest of the event
        serializer.serialize(plugin);
        serializer.setEventType("PluginEvents");
        if (debug) pl.getLogger().info("[Debug] Serialization: OK.");

        // Adding log
        pl.getLogManager().log(serializer);
    }

    /**
     * Logging a VehicleEvent
     * @param event the event runned
     * @param loggedEvent the log event configuration
     */
    public void logVehicleEvent(@NotNull final Event event, @NotNull final LoggedEvent loggedEvent) {
        final boolean debug = pl.getLogManager().isDebug();
        if (debug) pl.getLogger().info("[Debug] Started " + event.getEventName());

        // Starting serialization
        final LogSerializer serializer = new LogSerializer(pl, loggedEvent, event);
        // Serializing fields
        if (serializer.serializeFields(event)) {
            if (debug) pl.getLogger().info("[Debug] Fields should not be logged. Ignoring it.");
            return;
        }

        // Serializing the rest of the event
        serializer.serialize(((VehicleEvent) event).getVehicle(), null);
        serializer.setEventType("VehicleEvents");
        if (debug) pl.getLogger().info("[Debug] Serialization: OK.");

        // Adding log
        pl.getLogManager().log(serializer);
    }

    /**
     * Logging a WeatherEvent
     * @param event the event runned
     * @param loggedEvent the log event configuration
     */
    public void logWeatherEvent(@NotNull final Event event, @NotNull final LoggedEvent loggedEvent) {
        final boolean debug = pl.getLogManager().isDebug();
        if (debug) pl.getLogger().info("[Debug] Started " + event.getEventName());

        // Starting serialization
        final LogSerializer serializer = new LogSerializer(pl, loggedEvent, event);
        // Serializing fields
        if (serializer.serializeFields(event)) {
            if (debug) pl.getLogger().info("[Debug] Fields should not be logged. Ignoring it.");
            return;
        }

        // Serializing the rest of the event
        serializer.serialize(((WeatherEvent) event).getWorld());
        serializer.setEventType("WeatherEvents");
        if (debug) pl.getLogger().info("[Debug] Serialization: OK.");

        // Adding log
        pl.getLogManager().log(serializer);
    }

    /**
     * Logging a WorldEvent
     * @param event the event runned
     * @param loggedEvent the log event configuration
     */
    public void logWorldEvent(@NotNull final Event event, @NotNull final LoggedEvent loggedEvent) {
        final boolean debug = pl.getLogManager().isDebug();
        if (debug) pl.getLogger().info("[Debug] Started " + event.getEventName());

        // Starting serialization
        final LogSerializer serializer = new LogSerializer(pl, loggedEvent, event);
        // Serializing fields
        if (serializer.serializeFields(event)) {
            if (debug) pl.getLogger().info("[Debug] Fields should not be logged. Ignoring it.");
            return;
        }

        // Serializing the rest of the event
        serializer.serialize(((WorldEvent) event).getWorld());
        serializer.setEventType("WorldEvents");
        if (debug) pl.getLogger().info("[Debug] Serialization: OK.");

        // Adding log
        pl.getLogManager().log(serializer);
    }

    /**
     * Logging a ChunkEvent
     * @param event the event runned
     * @param loggedEvent the log event configuration
     */
    public void logChunkEvent(@NotNull final Event event, @NotNull final LoggedEvent loggedEvent) {
        final boolean debug = pl.getLogManager().isDebug();
        if (debug) pl.getLogger().info("[Debug] Started " + event.getEventName());

        // Starting serialization
        final LogSerializer serializer = new LogSerializer(pl, loggedEvent, event);
        // Serializing fields
        if (serializer.serializeFields(event)) {
            if (debug) pl.getLogger().info("[Debug] Fields should not be logged. Ignoring it.");
            return;
        }

        // Serializing the rest of the event
        serializer.serialize(((ChunkEvent) event).getChunk());
        serializer.setEventType("ChunkEvents");
        if (debug) pl.getLogger().info("[Debug] Serialization: OK.");

        // Adding log
        pl.getLogManager().log(serializer);
    }

}
