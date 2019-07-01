package fr.superlog.Log.Utils;

import fr.superlog.Log.Log;
import fr.superlog.Log.LogEvents;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.GZIPOutputStream;

public class LogUtils {
    public final Charset UTF_8 = Charset.forName("UTF-8");

    public String getTime(final String type) {
        return type == null ? "Unknown." : new SimpleDateFormat(type).format(new Date());
    }

    public File generateFile(final LogEvents log, final File dataFolder, final String logsFormat) {
        final String fileDirection = dataFolder + File.separator + "logs" + File.separator;
        return new File((log.getPlayerName() != null ? fileDirection + "players" + File.separator + log.getPlayerName() + File.separator : fileDirection),
                logsFormat.replace("{EVENT}", log.getEventName())
                        .replace("{TYPE}", log.getEventType())
                        .replace("{DAY}", getTime("dd"))
                        .replace("{MONTH}", getTime("MM"))
                        .replace("{YEAR}", getTime("yy")));
    }

    public boolean checkFile(final File f) {
        if(f == null) return false;
        if(!f.exists()) {
            try{
                File directory = f.getParentFile();
                if(!directory.exists()) if(!directory.mkdirs()) throw new Exception();
                if(!f.createNewFile()) throw new Exception();
            }catch(Exception e) {
                Log.getLogger().warning("Can't create log file " + f.getName());
                if(Log.isDebug()) e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    public String color(String texte){
        return ChatColor.translateAlternateColorCodes('&', texte);
    }

    public String decolor(String texte){
        return ChatColor.stripColor(color(texte));
    }


    public void gzipOldLogs(final int days, final File dataFolder) {
        try {
            if(days == 0) return;
            if(days < 0) {
                Log.getLogger().warning("Invalid 'gzipLogsAfter' in config.yml: must indicate a positive number of days");
                return;
            }

            if(!new File(dataFolder + File.separator + "logs").isDirectory() || !new File(dataFolder + File.separator + "logs" + File.separator + "players").isDirectory()) return;

            final long daysTimeStamp = days * 86400000;
            int count = 0;

            count += gzipOldLog(dataFolder + File.separator + "logs" + File.separator, daysTimeStamp);
            if(count != 0) Log.getLogger().info("GZipped " + count + " old logs.");
        } catch(Exception e) {
            if(Log.isDebug()) e.printStackTrace();
            Log.getLogger().info("Unknown error during gziping old logs.");
        }

    }

    public int gzipOldLog(String directoryName, long dayPassed) {
        int count = 0;
        File oneFolder = new File(directoryName);
        if(!oneFolder.isDirectory()) return count;
        File[] files = oneFolder.listFiles();
        if(files == null) return count;

        for (File file : files) {
            // If it's another directory
            if(file.isDirectory()) {
                count += gzipOldLog(file.getAbsolutePath(), dayPassed);
                continue;
            }
            // If it's not old enough
            if((System.currentTimeMillis() - file.lastModified()) < dayPassed) continue;

            // Writing
            try(FileInputStream newFile = new FileInputStream(file);
                GZIPOutputStream gzip = new GZIPOutputStream(new FileOutputStream(new File(file, file.getName() + ".gz")))){
                byte[] buffer = new byte[1024];
                int len;
                while((len=newFile.read(buffer)) != -1) gzip.write(buffer, 0, len);
                gzip.finish();
                count++;
            } catch (Exception e) {
                Log.getLogger().info("Error during GZipping " + file.getName() + ".");
            }

            file.delete();
            count++;
        }
        return count;
    }

    public void deleteOldLogs(final int days, final boolean evenGZippedLogs, final File dataFolder) {
        try {
            if(days == 0) return;
            if(days < 0) {
                Log.getLogger().warning("Invalid 'deleteLogsAfter' in config.yml: must indicate a positive number of days");
                return;
            }

            if(!new File(dataFolder + File.separator + "logs").isDirectory()) return;

            final long daysTimeStamp = days * 86400000;
            int count = 0;

            count += deleteOldLog(dataFolder + File.separator + "logs" + File.separator, daysTimeStamp, evenGZippedLogs);
            if(count != 0) Log.getLogger().info("Deleted " + count + " old logs.");
        } catch(Exception e) {
            if(Log.isDebug()) e.printStackTrace();
            Log.getLogger().info("Error during deleting old logs.");
        }
    }

    private int deleteOldLog(String directoryName, long dayPassed, final boolean evenGZippedLogs) {
        int count = 0;
        final File oneFolder = new File(directoryName);
        File[] files = oneFolder.listFiles();
        if(files == null) return count;

        for (File file : files) {
            // If it's another directory
            if(file.isDirectory()) {
                count += deleteOldLog(file.getAbsolutePath(), dayPassed, evenGZippedLogs);
                continue;
            }
            // If it's not old enough
            if((System.currentTimeMillis() - file.lastModified()) < dayPassed) continue;

            // Checking if it deletes even GZip file
            try {
                if(!evenGZippedLogs) {
                    String fileName = file.getName();
                    if((fileName.substring(fileName.lastIndexOf("."))).equals(".gz")) continue;
                }
            }catch(Exception e) { continue; }

            file.delete();
            count++;
        }
        return count;
    }

}
