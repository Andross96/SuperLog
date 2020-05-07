package fr.andross.superlog.Commands;

import fr.andross.superlog.Log.Log;
import org.bukkit.command.CommandSender;

import java.util.List;

public interface Cmd {

	 void run(final Log log, final CommandSender sender, final String[] args);

	 List<String> getTabCompletition(final String[] args);
	
}
