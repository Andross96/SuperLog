package fr.superlog;

import java.util.ArrayList;
import java.util.List;

import fr.superlog.Commands.Cmd;
import fr.superlog.Log.Log;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class SuperLog extends JavaPlugin {
	private Log log;

    @Override
    public void onEnable() {
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

    	if(args.length > 0 && args[0].equalsIgnoreCase("reload")){ // reload the plugin
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
			Cmd cmd = (Cmd)Class.forName("fr.superlog.Commands.Cmd" + args[0].toLowerCase()).newInstance();
	        cmd.run(log, sender, args);
		}catch(Exception e) {
			sender.sendMessage(log.getUtils().color(
					prefix + "&7Command usage:\n"
							+ prefix + "  &7\u2937 &3/log filtrer &b<player> <event>\n"
							+ prefix + "  &7\u2937 &3/log live &b<player>\n"
							+ prefix + "  &7\u2937 &3/log player &b<add|remove|contains|reload>\n"
							+ prefix + "  &7\u2937 &3/log reload\n"
							+ prefix + "  &7\u2937 &3/log toggle\n"
							+ prefix + "  &7\u2937 &3/log version"));
		}
    	return true;
    }
    
    @Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {    	
    	List<String> list = new ArrayList<>();

    	if(!sender.hasPermission("superlog.commands.access")) return list;
    	
    	if(args.length == 1) {
    		list.add("filtrer");
    		list.add("live");
    		list.add("player");
    		list.add("reload");
    		list.add("log");
    		list.add("toggle");
    		list.add("version");
    		return list;
    	}
    	
    	try {
			Cmd cmd = (Cmd)Class.forName("fr.superlog.Commands.Cmd" + args[0].toLowerCase()).newInstance();
	        return cmd.getTabCompletition(args);
		}catch(Exception e) {
			return list;
		}
    }

}
