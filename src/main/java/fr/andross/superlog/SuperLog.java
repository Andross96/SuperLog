package fr.andross.superlog;

import fr.andross.superlog.Commands.Cmd;
import fr.andross.superlog.Log.Log;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SuperLog extends JavaPlugin {
	private Log log;

    @Override
    public void onEnable() {
    	Log.LOGGER = getLogger();
		log = new Log(this);

		// Utilities
		log.getUtils().gzipOldLogs(log.getConfig().getGZipLogsAfter(), getDataFolder());
		log.getUtils().deleteOldLogs(log.getConfig().getDeleteLogs(), log.getConfig().getDeleteLogsGZipped(), getDataFolder());
    }
    
    @Override
    public void onDisable() { 
		getLogger().info("Saved " + log.saveAll() + " logs.");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    	if(!sender.hasPermission("superlog.commands.access")) {
    		sender.sendMessage(log.getUtils().color(log.getConfig().getMessage("noperm")));
    		return true;
    	}

		final String prefix = log.getConfig().getMessage("prefix");

    	if (args.length > 0 && args[0].equalsIgnoreCase("reload")) { // reload the plugin
			getServer().getScheduler().runTaskAsynchronously(log.getPlugin(), () -> {
				// Saving logs
				final int count = log.saveAll();

				// Finished to save, we can come back synchronously
				getServer().getScheduler().scheduleSyncDelayedTask(log.getPlugin(), () -> {
					sender.sendMessage(log.getUtils().color(prefix + "&2Logs: " + count + " saved."));
					log = new Log(this);
					sender.sendMessage(log.getUtils().color(prefix + "&2Plugin reloaded!"));
				});
			});

    		return true;
		}
    	
    	try {
			final Cmd cmd = (Cmd) Class.forName("fr.andross.superlog.Commands.Cmd" + args[0].toLowerCase()).newInstance();
	        cmd.run(log, sender, args);
		} catch (Exception e) {
			sender.sendMessage(log.getUtils().color(
					prefix + "&7Command usage:\n"
							+ prefix + "  &7\u2937 &3/log filter &b<player> <event>\n"
							+ prefix + "  &7\u2937 &3/log live &b<player>\n"
							+ prefix + "  &7\u2937 &3/log player &b<add|remove|contains|reload>\n"
							+ prefix + "  &7\u2937 &3/log reload\n"
							+ prefix + "  &7\u2937 &3/log toggle\n"
							+ prefix + "  &7\u2937 &3/log version"));
		}
    	return true;
    }

    private final List<String> tab = Arrays.asList("filter", "live", "player", "reload", "log", "toggle", "version");
    @Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    	if (!sender.hasPermission("superlog.commands.access")) return new ArrayList<>();

    	if (args.length == 1) return StringUtil.copyPartialMatches(args[0], tab, new ArrayList<>());
    	
    	try {
			final Cmd cmd = (Cmd) Class.forName("fr.andross.superlog.Commands.Cmd" + args[0].toLowerCase()).newInstance();
	        return cmd.getTabCompletition(args);
		} catch (Exception e) {
			return new ArrayList<>();
		}
    }

}
