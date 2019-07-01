package fr.superlog.Log;

import fr.superlog.Log.Utils.LogEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogConfig {
    private FileConfiguration config;
    private final Set<String> alertCommands = new HashSet<>();
    private boolean versionBiggerThan1_13;
    private boolean isCitizensEnabled;
    private boolean enabled = true;

    public LogConfig(final Log log) {
        final Plugin pl = log.getPlugin();
        isCitizensEnabled = pl.getServer().getPluginManager().getPlugin("Citizens") != null;
        versionBiggerThan1_13 = pl.getServer().getBukkitVersion().contains("1.13") || pl.getServer().getBukkitVersion().contains("1.14");


        ///////////////////////////////
        // Checking config.yml
        ///////////////////////////////
        if(!(new File(pl.getDataFolder(), "config.yml").isFile())) pl.saveDefaultConfig();
        pl.reloadConfig();
        config = pl.getConfig();

        int save_delay = config.getInt("save-delay");
        if(save_delay < 0) {
            config.set("save-delay", 300);
            Log.getLogger().warning("Save-delay invalid in config.yml. Using default: 300s.");
        }

        try {
            log.getUtils().getTime(config.getString("date-format"));
        }catch(Exception e) {
            if(Log.isDebug()) e.printStackTrace();
            config.set("date-format", "HH:mm:ss");
            Log.getLogger().warning("Invalid 'date-format' in config.yml. Using default formatting instead.");
        }

        String logsFormat = config.getString("logsFormat");
        try {
            if(logsFormat == null || logsFormat.isEmpty()) throw new Exception();
            char[] forbiddenFileNameChar = { '/', '\n', '\r', '\t', '\0', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':' };
            for(char c : forbiddenFileNameChar) if(logsFormat.indexOf(c) > 0) throw new Exception();
        }catch(Exception e) {
            if(Log.isDebug()) e.printStackTrace();
            config.set("logsFormat", "{DAY}-{MONTH}-{YEAR}_{TYPE}.log");
            Log.getLogger().warning("Invalid logsFormat set in config.yml. Using default.");
        }

        String logsLiveFormat = config.getString("logsLiveFormat");
        if(logsLiveFormat == null || logsLiveFormat.isEmpty()){
            config.set("logsLiveFormat", "&7&o[{TIME}][{EVENT}] {LOG}.log");
            Log.getLogger().warning("Invalid logsFormat set in config.yml. Using default.");
        }


        // Loading loggedCommands
        alertCommands.clear();
        alertCommands.addAll(config.getStringList("alertCommands"));


        ///////////////////////////////
        // Loading events
        ///////////////////////////////
        // Disable previously activated listeners
        HandlerList.unregisterAll(pl);
        // Preparing the new listener

        final Listener listener = new Listener() { };

        // Load the alertCommands event:
        if(!alertCommands.isEmpty()){
            final EventExecutor eventExecutor = (bukkitListener, bukkitEvent) -> {
                if(!enabled) return;
                PlayerCommandPreprocessEvent e = (PlayerCommandPreprocessEvent) bukkitEvent;
                log.alertCommands(e.getPlayer().getName(), e.getMessage());
            };
            pl.getServer().getPluginManager().registerEvent(PlayerCommandPreprocessEvent.class, listener, EventPriority.MONITOR, eventExecutor, log.getPlugin(), false);
        }

        // Load events
        final ConfigurationSection events = config.getConfigurationSection("events");
        if(events == null) {
            Log.getLogger().warning("[!!] There is nothing to log. Add events in config.yml.");
            return;
        }
        
        // For each event
        int count = 0;
        final Pattern argsPattern = Pattern.compile("\\{(.*?)\\}"); // e.g: player.name
        for(final String eventName : events.getKeys(false)) {
            // Checking if event is enabled
            final ConfigurationSection event = events.getConfigurationSection(eventName);
            if(event == null || !event.getBoolean("enabled")) continue;
            
            // Starting processing the event
            // Getting event class
            final Class<? extends Event> eventClass = getEventClass(eventName);
            if(eventClass == null){
                Log.getLogger().warning("[!!] Can't load event '" + eventName + "': event class is not found.");
                continue;
            }
            
            // Intialise variables needed to create a correct LogEvent
            final Map<String, Set<String>> conditions = new HashMap<>();
            Field[] fields = null;

            
            // Loading event message
            String message = event.getString("message");
            if(message == null || message.isEmpty()) message = "executed.";
            // Loading args from message
            final Matcher m = argsPattern.matcher(message);
            final Set<String> args = new HashSet<>();
            final Set<Field> fieldsSet = new HashSet<>();
            while(m.find()){
                String arg = m.group(1);
                if(!arg.matches("(?i)name|type|health|ip|locworld|locx|locy|locz|lastdeathcause|lastdeathby")){ // Loading fields
                    String field = arg.contains(".") ? arg.split("\\.")[0] : arg;
                    Field f = null;

                    for(Field fieldsInClass : getAllFields(eventClass)){
                        if(fieldsInClass.getName().equalsIgnoreCase(field)){
                            fieldsInClass.setAccessible(true);
                            fieldsSet.add(fieldsInClass);
                            f = fieldsInClass;
                            break;
                        }
                    }

                    if(f == null) Log.getLogger().warning("[!!] Can not found field '" + field + "' for event '" + eventName + "'.");
                }
                arg = arg.contains(".") ? arg.split("\\.")[1].toUpperCase() : arg.toUpperCase();
                args.add(arg);
            }
            if(!args.isEmpty()) conditions.put("ARGS", args);

            // Loading conditions
            boolean errorInConditions = false;
            for(final String condition : event.getKeys(false)) {
                if(condition.equals("enabled")) continue;
                if(condition.equals("message")) continue;

                List<String> list;
                if(!event.isList(condition)){ // Try to get conditions from a String
                    if(!event.isString(condition)) {
                        Log.getLogger().warning("[!!] Invalid condition list '" + condition + "' for event '" + eventName + "'.");
                        errorInConditions = true;
                        break;
                    }

                    String conditionsList = event.getString(condition);
                    if (conditionsList == null || conditionsList.isEmpty()) continue;
                    final String[] conditionsListed = conditionsList.trim().replace("\\s+", "").split(",");
                    list = Arrays.asList(conditionsListed);
                }else list = event.getStringList(condition);

                if(list.isEmpty()) continue;
                if(condition.contains("-")){ // Field condition
                    String field = condition.split("-")[0];
                    Field f = null;
                    for(Field fieldsInClass : getAllFields(eventClass)){
                        if(fieldsInClass.getName().equalsIgnoreCase(field)){
                            if(!fieldsSet.contains(fieldsInClass)) {
                                fieldsInClass.setAccessible(true);
                                fieldsSet.add(fieldsInClass);
                            }
                            f = fieldsInClass;
                            break;
                        }
                    }
                    if(f == null){
                        Log.getLogger().warning("[!!] Unknown condition '" + condition + "': unknown field '" + field + "' for event '" + eventName + "'.");
                        errorInConditions = true;
                        break;
                    }
                }
                final Set<String> conditionsSet = new HashSet<>();
                for(String s : list) conditionsSet.add(s.toUpperCase());
                if(!conditionsSet.isEmpty()) conditions.put(condition.toUpperCase(), conditionsSet);
            }

            // If conditions are not valid, we don't register the event
            if(errorInConditions) continue;

            // Saving fields
            if(!fieldsSet.isEmpty()) fields = fieldsSet.toArray(new Field[0]);

            // Creating the LogEvent
            final LogEvent logEvent = new LogEvent(conditions, message, fields, versionBiggerThan1_13);

            // Creating the EventExecutor method, based on event type
            final EventExecutor eventExecutor = createEventExecutor(log, eventClass, logEvent);
            if(eventExecutor == null){
                Log.getLogger().warning("[!!] Event '" + eventName + "' is not supported yet.");
                continue;
            }


            // Register event
            // Priority monitor for all, except for the PlayerCommandPreprocessEvent, which have to be started firstly
            final EventPriority priority = eventName.equals("PlayerCommandPreprocessEvent") ? EventPriority.HIGHEST : EventPriority.MONITOR;
            try {
                pl.getServer().getPluginManager().registerEvent(eventClass, listener, priority, eventExecutor, log.getPlugin(), true);
            }catch(Exception ex){
                if(Log.isDebug())ex.printStackTrace();
                Log.getLogger().warning("[!!] Event '" + eventName + "' is not valid or not supported yet.");
            }
            count++; // one more event registered
        }

        // Result
        if(count == 0) Log.getLogger().warning("[!!] There is nothing to log. Add events in config.yml.");
        else if(count == 1) Log.getLogger().info("Listening to 1 event.");
        else Log.getLogger().info("Listening to " + count + " events.");

        // Running async loop for saving cache
        if(save_delay != 0 && count != 0) {
            if (log.getTask() != null) log.getTask().cancel();
            pl.getServer().getScheduler().runTaskTimerAsynchronously(pl, log::saveAll, 0L, (long) (save_delay * 20));
            Log.getLogger().info("Saving logs async each " + save_delay + " seconds.");
        }
    }


    @SuppressWarnings("unchecked")
    private Class<? extends Event> getEventClass(final String eName) {
        final String[] packageName = new String[] {
                "org.bukkit.event.block.", "org.bukkit.event.enchantment.","org.bukkit.event.entity.",
                "org.bukkit.event.hanging.", "org.bukkit.event.inventory." , "org.bukkit.event.player.",
                "org.bukkit.event.server.", "org.bukkit.event.vehicle.", "org.bukkit.event.weather.",
                "org.bukkit.event.world."
        };
        for(String className : packageName) {
            try {  return (Class<? extends Event>) Class.forName(className + eName);
            }catch(Exception e) { /* NoThing:) */ }
        }
        return null;
    }


    private Set<Field> getAllFields(Class<?> clazz){
        final Set<Field> fields = new HashSet<>();
        while(clazz != null){
            Collections.addAll(fields, clazz.getDeclaredFields());
            clazz = clazz.getSuperclass();
        }
        return fields;
    }


    private EventExecutor createEventExecutor(final Log log, final Class<? extends Event> event, final LogEvent logEvent){
        EventExecutor eventExecutor = null;
        Class<?> superClasses = event.getSuperclass();
        while(superClasses != null){
            switch(superClasses.getSimpleName()){
                case "PlayerEvent":
                    eventExecutor = (bukkitListener,bukkitEvent) -> {
                        if(!enabled) return;
                        log.log(new LogEvents(bukkitEvent, logEvent).runPlayerEvents(isCitizensEnabled));
                    };
                    break;
                case "BlockEvent":
                    eventExecutor = (bukkitListener,bukkitEvent) -> {
                        if(!enabled) return;
                        log.log(new LogEvents(bukkitEvent, logEvent).runBlockEvents());
                    };
                    break;
                case "EntityEvent":
                    eventExecutor = (bukkitListener,bukkitEvent) -> {
                        if(!enabled) return;
                        log.log(new LogEvents(bukkitEvent, logEvent).runEntityEvents());
                    };
                    break;
                case "HangingEvent":
                    eventExecutor = (bukkitListener,bukkitEvent) -> {
                        if(!enabled) return;
                        log.log(new LogEvents(bukkitEvent, logEvent).runHangingEvents());
                    };
                    break;
                case "InventoryEvent":
                    eventExecutor = (bukkitListener,bukkitEvent) -> {
                        if(!enabled) return;
                        log.log(new LogEvents(bukkitEvent, logEvent).runInventoryEvents());
                    };
                    break;
                case "ServerEvent":
                    eventExecutor = (bukkitListener,bukkitEvent) -> {
                        if(!enabled) return;
                        log.log(new LogEvents(bukkitEvent, logEvent).runServerEvents());
                    };
                    break;
                case "PluginEvent":
                    eventExecutor = (bukkitListener,bukkitEvent) -> {
                        if(!enabled) return;
                        log.log(new LogEvents(bukkitEvent, logEvent).runPluginEvents());
                    };
                    break;
                case "VehicleEvent":
                    eventExecutor = (bukkitListener,bukkitEvent) -> {
                        if(!enabled) return;
                        log.log(new LogEvents(bukkitEvent, logEvent).runVehicleEvents());
                    };
                    break;
                case "WeatherEvent":
                    eventExecutor = (bukkitListener,bukkitEvent) -> {
                        if(!enabled) return;
                        log.log(new LogEvents(bukkitEvent, logEvent).runWeatherEvents());
                    };
                    break;
                case "WorldEvent":
                    eventExecutor = (bukkitListener,bukkitEvent) -> {
                        if(!enabled) return;
                        log.log(new LogEvents(bukkitEvent, logEvent).runWorldEvents());
                    };
                    break;
                case "ChunkEvent":
                    eventExecutor = (bukkitListener,bukkitEvent) -> {
                        if(!enabled) return;
                        log.log(new LogEvents(bukkitEvent, logEvent).runChunkEvents());
                    };
                    break;
                default: superClasses = superClasses.getSuperclass(); break;
            }
            if(eventExecutor != null) break;
        }
        return eventExecutor;
    }

    protected int getSaveDelay(){ return config.getInt("save-delay"); }
    protected String getDateFormat(){ return config.getString("date-format"); }
    public String getLogsFormat(){ return config.getString("logsFormat"); }
    public String getLogsLiveFormat(){ return config.getString("logsLiveFormat"); }
    protected boolean getLogsInConsole(){ return config.getBoolean("logsInConsole"); }
    protected boolean getLogsInGame(){ return config.getBoolean("logsInGame"); }
    public int getGZipLogsAfter(){ return config.getInt("gzipLogsAfter"); }
    public int getDeleteLogs(){ return config.getInt("deleteLogs.after"); }
    public boolean getDeleteLogsGZipped(){ return config.getBoolean("deleteLogs.evenGZippedLogs"); }
    public String getMessage(final String message){ return config.getString("messages." + message); }

    protected Set<String> getAlertCommands(){ return alertCommands; }

    public boolean isNotEnabled(){ return !enabled; }
    public void toggleEnabled(){ enabled = !enabled; }

}
