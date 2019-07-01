package fr.superlog.Commands;

import java.util.List;

import fr.superlog.Log.Log;
import org.bukkit.command.CommandSender;

public interface Cmd {

	 void run(final Log log, final CommandSender sender, final String[] args);

	 List<String> getTabCompletition(final String[] args);
	
}
