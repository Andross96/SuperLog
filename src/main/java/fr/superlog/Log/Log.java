package fr.superlog.Log;

import fr.superlog.Log.Utils.LogUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.*;
import java.util.logging.Logger;

public final class Log {
    public static Logger LOGGER;
    public static boolean DEBUG;

    private final Plugin pl;
    private final LogUtils utils;
    private final LogConfig config;
    private final Map<File, ArrayList<String>> cache = new HashMap<>();
    private final Map<String, CommandSender> logLive = new HashMap<>();
    private BukkitTask task = null;

    public Log(final Plugin pl){
        this.pl = pl;
        this.utils = new LogUtils();
        this.config = new LogConfig(this);
    }

    public void log(final LogEvents log){
        if(DEBUG) LOGGER.info("[Debug] Start log processing..");
        if (log == null) return;

        // Adding log
        final File f = utils.generateFile(log, pl.getDataFolder(), config.getLogsFormat());
        final String message = "[" + utils.getTime(config.getDateFormat()) + "][" + log.getEventName() + "]: " + log.getMessage();


        synchronized(cache) {
            // Adding directly in file
            if(config.getSaveDelay() == 0) {
                save(f, new String[] { message });
                return;
            }

            // Or adding in cache
            ArrayList<String> logs = cache.get(f);
            if (logs == null) logs = new ArrayList<>();
            logs.add(message);
            cache.put(f, logs);
        }

        if(DEBUG) LOGGER.info("[Debug] Log: OK");
        // Live logging:
        if(logLive.isEmpty() || log.getPlayerName() == null) return;
        final CommandSender sender = logLive.get(log.getPlayerName());
        if(sender == null || (sender instanceof Player && !((Player)sender).isOnline())){ // Disconnected
            logLive.remove(log.getPlayerName());
            return;
        }
        final String logLiveMessage = config.getLogsLiveFormat()
                .replace("{TIME}", utils.getTime("HH:mm:ss"))
                .replace("{EVENT}", log.getEventName())
                .replace("{LOG}", log.getMessage());
        sender.sendMessage(utils.color(logLiveMessage));
    }

    private int save(final File f, final String[] messages) {
        // Check file (create folders etc.)
        if(!utils.checkFile(f)) return 0;

        // Write log
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(f, true), utils.UTF_8)){
            for(String message : messages) {
                writer.append(message);
                writer.append(System.lineSeparator());
            }
        } catch(Exception e) {
            e.printStackTrace();
            LOGGER.warning("Error writing logs in " + f.getName());
            return 0;
        }

        // Clearing cache
        if(cache.containsKey(f)) cache.get(f).clear();
        return 1;
    }

    public int saveAll() {
        int count = 0;

        synchronized(cache) {
            Iterator<Map.Entry<File, ArrayList<String>>> i = cache.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry<File, ArrayList<String>> map = i.next();
                final ArrayList<String> logs = map.getValue();
                if (logs == null || logs.isEmpty()) {
                    i.remove();
                    continue;
                }
                count += save(map.getKey(), logs.toArray(new String[0]));
            }
        }

        if(!pl.isEnabled() || count == 0) return count;
        final String logMessage = config.getMessage("logsSaved").replace("{OCCURRENCES}", String.valueOf(count));
        if(config.getLogsInConsole()) {
            final String message = utils.decolor(logMessage);
            LOGGER.info(message);
        }
        if(config.getLogsInGame()) {
            final String message = utils.color(logMessage);
            for(Player p : pl.getServer().getOnlinePlayers()) {
                if(!p.hasPermission("superlog.getlogs")) continue;
                p.sendMessage(message);
            }
        }

        return count;
    }

    protected void alertCommands(final String pName, final String command) {
        if(config.isNotEnabled() || config.getAlertCommands().contains("*")) return;
        if(!config.getAlertCommands().contains(command.split(" ")[0])) return;

        final String finalMessage = utils.color(config.getMessage("prefix") + config.getMessage("alertCommandsFormat").replace("{PLAYER}", pName).replace("{COMMAND}", command));
        for(Player p : pl.getServer().getOnlinePlayers()) {
            if(!p.hasPermission("superlog.getlogs")) continue;
            if(p.getName().equals(pName)) continue;
            p.sendMessage(finalMessage);
        }
    }

    public final Plugin getPlugin(){ return pl; }

    public final LogConfig getConfig(){ return config; }

    public final LogUtils getUtils(){ return utils; }

    public final Map<String, CommandSender> getLogLive(){ return logLive; }

    public static void toggleDebug(){ DEBUG = !DEBUG; }

    protected final BukkitTask getTask(){ return task; }

}
