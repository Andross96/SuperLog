package fr.andross.superlog.Log.Utils;

import fr.andross.superlog.Log.Log;
import fr.andross.superlog.Log.LogEvents;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

public final class LogUtils {
    public final Charset UTF_8 = StandardCharsets.UTF_8;

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
        if (f == null) return false;
        if (!f.exists()) {
            try {
                File directory = f.getParentFile();
                if(!directory.exists()) if(!directory.mkdirs()) throw new Exception();
                if(!f.createNewFile()) throw new Exception();
            } catch(Exception e) {
                Log.LOGGER.warning("Can't create log file " + f.getName());
                if(Log.DEBUG) e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    public String color(final String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public String decolor(final String text) {
        return ChatColor.stripColor(color(text));
    }

    public void gzipOldLogs(final int days, final File dataFolder) {
        if (days == 0) return;
        if (days < 0) {
            Log.LOGGER.warning("Invalid 'gzipLogsAfter' in config.yml: must indicate a positive number of days");
            return;
        }

        // Nothing to GZip?
        if (!new File(dataFolder + File.separator + "logs").isDirectory() || !new File(dataFolder + File.separator + "logs" + File.separator + "players").isDirectory()) return;

        final int count = gzipOldLog(dataFolder + File.separator + "logs" + File.separator, TimeUnit.DAYS.toMillis(days));
        if (count != 0) Log.LOGGER.info("GZipped " + count + " old logs.");
    }

    public int gzipOldLog(final String directoryName, final long dayPassed) {
        int count = 0;
        final File oneFolder = new File(directoryName);
        if (!oneFolder.isDirectory()) return count;
        final File[] files = oneFolder.listFiles();
        if (files == null) return count;

        for (File file : files) {
            // If it's another directory
            if(file.isDirectory()) {
                count += gzipOldLog(file.getAbsolutePath(), dayPassed);
                continue;
            }
            // Not old enough?
            if((System.currentTimeMillis() - file.lastModified()) < dayPassed) continue;

            // Already a GZipped file?
            if (getFileExtension(file).equalsIgnoreCase("gz")) continue;

            // Writing
            try (FileInputStream newFile = new FileInputStream(file);
                GZIPOutputStream gzip = new GZIPOutputStream(new FileOutputStream(new File(file.getParent(), file.getName() + ".gz")))){
                byte[] buffer = new byte[1024];
                int len;
                while((len=newFile.read(buffer)) != -1) gzip.write(buffer, 0, len);
                gzip.finish();
            } catch (Exception e) {
                Log.LOGGER.info("Error during GZipping " + file.getName() + ".");
                continue;
            }
            file.delete();
            count++;
        }
        return count;
    }

    public void deleteOldLogs(final int days, final boolean evenGZippedLogs, final File dataFolder) {
        try {
            if (days == 0) return;
            if (days < 0) {
                Log.LOGGER.warning("Invalid 'deleteLogsAfter' in config.yml: must indicate a positive number of days");
                return;
            }

            if (!new File(dataFolder + File.separator + "logs").isDirectory()) return;

            final int count = deleteOldLog(dataFolder + File.separator + "logs" + File.separator, TimeUnit.DAYS.toMillis(days), evenGZippedLogs);
            if (count != 0) Log.LOGGER.info("Deleted " + count + " old logs.");
        } catch (Exception e) {
            if(Log.DEBUG) e.printStackTrace();
            Log.LOGGER.info("Error during deleting old logs.");
        }
    }

    private int deleteOldLog(final String directoryName, final long dayPassed, final boolean evenGZippedLogs) {
        int count = 0;
        final File oneFolder = new File(directoryName);
        final File[] files = oneFolder.listFiles();
        if (files == null) return count;

        for (File file : files) {
            // If it's another directory
            if(file.isDirectory()) {
                count += deleteOldLog(file.getAbsolutePath(), dayPassed, evenGZippedLogs);
                continue;
            }
            // Not old enough?
            if((System.currentTimeMillis() - file.lastModified()) < dayPassed) continue;

            // Checking if it deletes even GZipped files
            if (!evenGZippedLogs && getFileExtension(file).equalsIgnoreCase("gz")) continue;

            file.delete();
            count++;
        }
        return count;
    }

    private String getFileExtension(final File f) {
        final String fileName = f.getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
    }
}
