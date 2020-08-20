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
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Log config
 * @version 1.2
 * @author Andross
 */
public final class LogConfig {
    private final Set<String> alertCommands = new HashSet<>();
    private final boolean isCitizensEnabled;
    private final boolean bukkitNewApi;
    private boolean enabled = true;
    private DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    /**
     * Should not be instantiated. Use {@link SuperLog#getLogConfig()} instead.
     * @param pl the plugin instance
     * @param sender the sender
     */
    public LogConfig(@NotNull final SuperLog pl, @NotNull final CommandSender sender) {
        // Is Citizens enabled?
        final Plugin citizensPlugin = pl.getServer().getPluginManager().getPlugin("Citizens");
        isCitizensEnabled = citizensPlugin != null && citizensPlugin.isEnabled();

        // 1.13+?
        final String bukkitVersion = Bukkit.getBukkitVersion();
        bukkitNewApi = bukkitVersion.contains("1.13") || bukkitVersion.contains("1.14") || bukkitVersion.contains("1.15") || bukkitVersion.contains("1.16");

        // Config
        final FileConfiguration config = pl.getConfig();
        final String prefix = pl.getLogUtils().getColoredString("messages.prefix");

        // Save delay
        if (config.getInt("save-delay") < 0) {
            config.set("save-delay", 300);
            sender.sendMessage(prefix + Utils.color("&c[Config] Invalid '&esave-delay&c'. Using default: 300s."));
        }

        // Date format
        final String dateFormatString = config.getString("date-format");
        if (dateFormatString == null) {
            sender.sendMessage(prefix + Utils.color("&c[Config] Unknown or not set '&edate-format&c'. Using default formatting instead (HH:mm:ss)."));
        } else {
            try {
                final DateFormat testFormat = new SimpleDateFormat(dateFormatString);
                testFormat.format(new Date());
                dateFormat = testFormat;
            } catch (final Exception e) {
                dateFormat = new SimpleDateFormat("HH:mm:ss");
                sender.sendMessage(prefix + Utils.color("&c[Config] Invalid '&edate-format&c'. Using default formatting instead (HH:mm:ss)."));
            }
        }

        // Logs format
        String logsFormat = config.getString("logs-format");
        try {
            if (logsFormat == null || logsFormat.isEmpty()) throw new IllegalStateException();
            for (final char c : Utils.forbiddenFileNameChar)
                if (logsFormat.indexOf(c) > 0)
                    throw new IllegalArgumentException();
        } catch (final IllegalStateException e) {
            config.set("logs-format", "{DAY}-{MONTH}-{YEAR}_{TYPE}.log");
            sender.sendMessage(prefix + Utils.color("&c[Config] Empty or not set '&elogs-format&c'. Using default."));
        } catch (final IllegalArgumentException e) {
            config.set("logs-format", "{DAY}-{MONTH}-{YEAR}_{TYPE}.log");
            sender.sendMessage(prefix + Utils.color("&c[Config] '&elogs-format&c' contains invalid file name character. Using default."));
        }

        // Logs live format
        String logsLiveFormat = config.getString("logs-live-format");
        if (logsLiveFormat == null || logsLiveFormat.isEmpty()) {
            config.set("logs-live-format", "&7&o[{TIME}][{EVENT}] {LOG}.log");
            sender.sendMessage(prefix + Utils.color("&c[Config] Invalid '&elogs-live-format&c'. Using default."));
        }

        // Getting all commands that should be alerted when used
        alertCommands.addAll(config.getStringList("commands-alert.list").stream().map(String::toLowerCase).collect(Collectors.toList()));


        ///////////////////////////////
        // Loading events
        ///////////////////////////////
        // Preparing the new listener
        final Listener listener = new Listener() { };

        // Registering alert commands listener if needed
        if (!alertCommands.isEmpty()) {
            final EventExecutor eventExecutor = (bukkitListener, bukkitEvent) -> {
                if (!enabled) return;
                final PlayerCommandPreprocessEvent e = (PlayerCommandPreprocessEvent) bukkitEvent;
                pl.getLogManager().alertCommands(e.getPlayer().getName(), e.getMessage().toLowerCase());
            };
            Bukkit.getPluginManager().registerEvent(PlayerCommandPreprocessEvent.class, listener, EventPriority.MONITOR, eventExecutor, pl);
        }

        // Getting events section
        final ConfigurationSection events = config.getConfigurationSection("events");
        if (events == null) {
            sender.sendMessage(prefix + Utils.color("&c[Config] There is nothing to log (empty '&eevents&c' section)."));
            return;
        }

        // For each event
        int count = 0;
        final Pattern argsPattern = Pattern.compile("\\{(.*?)\\}"); // e.g: player.name
        events: for (final String eventName : events.getKeys(false)) {
            // Checking if event is enabled
            final ConfigurationSection event = events.getConfigurationSection(eventName);
            if (event == null || !event.getBoolean("enabled")) continue;

            // Starting processing the event
            // Getting event class
            final Class<? extends Event> eventClass = Utils.getEventClass(eventName);
            if (eventClass == null) {
                sender.sendMessage(prefix + Utils.color("&c[Config] Can not load event '&e" + eventName + "&c': event class is not found."));
                continue;
            }

            // Loading event message
            String message = event.getString("message");
            if (message == null || message.isEmpty()) message = "executed.";

            // Initialize args
            final Map<String, List<String>> conditions = new HashMap<>();
            Field[] fields = null;

            // Loading args from message
            final Matcher m = argsPattern.matcher(message);
            final List<String> args = new ArrayList<>();
            final List<Field> fieldsSet = new ArrayList<>();
            while (m.find()) {
                String arg = m.group(1);
                if (!arg.matches("(?i)name|type|health|ip|locworld|locx|locy|locz|lastdeathcause|lastdeathby")) { // Loading fields
                    final String field = arg.contains(".") ? arg.split("\\.")[0] : arg;
                    Field f = null;

                    for (final Field fieldsInClass : Utils.getAllFields(eventClass)) {
                        if (fieldsInClass.getName().equalsIgnoreCase(field)) {
                            fieldsInClass.setAccessible(true);
                            fieldsSet.add(fieldsInClass);
                            f = fieldsInClass;
                            break;
                        }
                    }

                    if (f == null) sender.sendMessage(prefix + Utils.color("&c[Config] Can not found field '&e" + field + "&c' for event '&e" + eventName + "&c'."));
                }
                arg = arg.contains(".") ? arg.split("\\.")[1].toUpperCase() : arg.toUpperCase();
                args.add(arg);
            }
            if (!args.isEmpty()) conditions.put("ARGS", args);

            // Loading conditions
            for (final String condition : event.getKeys(false)) {
                if (condition.equalsIgnoreCase("enabled")) continue;
                if (condition.equalsIgnoreCase("message")) continue;

                List<String> list;
                if (!event.isList(condition)) { // Try to get conditions from a String
                    if (!event.isString(condition)) {
                        sender.sendMessage(prefix + Utils.color("&c[Config] Invalid condition list '&e" + condition + "&c' for event '&e" + eventName + "&c'."));
                        continue events;
                    }

                    final String conditionsList = event.getString(condition);
                    if (conditionsList == null || conditionsList.isEmpty()) continue;
                    final String[] conditionsListed = conditionsList.trim().replace("\\s+", "").split(",");
                    list = Arrays.asList(conditionsListed);
                } else list = event.getStringList(condition);

                if (list.isEmpty()) continue;
                if (condition.contains("-")) { // Field condition
                    final String field = condition.split("-")[0];
                    Field f = null;
                    for (final Field fieldsInClass : Utils.getAllFields(eventClass)) {
                        if (fieldsInClass.getName().equalsIgnoreCase(field)) {
                            if (!fieldsSet.contains(fieldsInClass)) {
                                fieldsInClass.setAccessible(true);
                                fieldsSet.add(fieldsInClass);
                            }
                            f = fieldsInClass;
                            break;
                        }
                    }
                    if (f == null) {
                        sender.sendMessage(prefix + Utils.color("&c[Config] Unknown condition '&e" + condition + "&c': unknown field '&e" + field + "&c' for event '&e" + eventName + "&c'."));
                        continue events;
                    }
                }
                final List<String> conditionsList = new ArrayList<>();
                for (final String s : list) conditionsList.add(s.toUpperCase());
                if (!conditionsList.isEmpty()) conditions.put(condition.toUpperCase(), conditionsList);
            }

            // Saving fields
            if (!fieldsSet.isEmpty()) fields = fieldsSet.toArray(new Field[0]);

            // Creating the LogEventConfig
            final LoggedEvent loggedEvent = new LoggedEvent(message, conditions, fields);

            // Creating the EventExecutor method, based on event type
            final EventExecutor eventExecutor = pl.getLogUtils().createEventExecutor(eventClass, loggedEvent);
            if (eventExecutor == null) {
                sender.sendMessage(prefix + Utils.color("&c[Config] Event '&e" + eventName + "&c' is not loggable/supported."));
                continue;
            }

            // Register event
            // Priority monitor for all, except for the PlayerCommandPreprocessEvent, which have to be started firstly
            final EventPriority priority = eventName.equals("PlayerCommandPreprocessEvent") ? EventPriority.LOWEST : EventPriority.MONITOR;
            Bukkit.getPluginManager().registerEvent(eventClass, listener, priority, eventExecutor, pl);
            count++; // one more event registered
        }

        // Result
        if (count == 0) sender.sendMessage(prefix + Utils.color("&e[!!] There is nothing to log. Add events in config.yml."));
        else sender.sendMessage(prefix + Utils.color("&aLogging &e" + count + "&a event" + (count > 1 ? "s" : "") + "."));

        // Running async loop for saving cache
        final int saveDelay = config.getInt("save-delay");
        if (saveDelay != 0 && count != 0) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(pl, pl.getLogManager()::saveAll, saveDelay * 20L, (saveDelay * 20L));
            sender.sendMessage(prefix + Utils.color("&aSaving logs async each &e" + saveDelay + "&a seconds."));
        }
    }

    /**
     * Get the set of commands that should be alerted when used
     * @return set of commands that should be alerted
     */
    @NotNull
    public Set<String> getAlertCommands() {
        return alertCommands;
    }

    /**
     * If Citizens is enabled on this instance
     * @return true if Citizens is running, otherwise false
     */
    public boolean isCitizensEnabled() {
        return isCitizensEnabled;
    }

    /**
     * If we are running on the new api-version 1.13+ version
     * @return true if MC>=1.13, otherwise false
     */
    public boolean isBukkitNewApi() {
        return bukkitNewApi;
    }

    /**
     * Check if the plugin is manually enabled
     * @return true if the plugin is enabled, otherwise false
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set manually if the plugin should be enabled
     * @param enabled if the plugin should be enabled
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Get the date format entered in the config
     * @return the date format configured in this config
     */
    @NotNull
    public DateFormat getDateFormat() {
        return dateFormat;
    }
}
