# SuperLog ![GPL-3.0](http://cdn.andross.fr/badges/license.svg) ![Stable](http://cdn.andross.fr/badges/stable.svg) ![Version](http://cdn.andross.fr/badges/v1.2.svg) ![Discord](http://cdn.andross.fr/badges/discord.svg)

### Description
SuperLog is a simple lightweight & customizable asynchronous logging plugin.

### Features:
* Support all bukkit versions;
* Language: fully customizable language, write your own format for logs...
* Configuration: easy to use, variable friendly, fully documented
* Asynchronous saving: in game playing will not being affected, as the saving processus is on another thread; You have 2 configurations: (save-delay in config.yml)
  * Using cache (save log in file after xxx seconds, better and default way)
  * Without cache (write in file everytime an event happen; use a bit more ressources)
* Event listener optimizated: the plugin listen only to the events configurated
* Event conditions: log the event only if it respect your conditions (a type of block/entity, a player name...)
* Live alerts for commands used: receive a message ingame when commands are used by players (fully customizable in config)
* Live log listening in game: receive logs for player X directly by message ingame
* Auto-GZip: put logs into compressed GZip files, after X days, configurable.
* Auto-Delete: delete old logs, after X days, configurable.
* Reload supported

![stats](https://bstats.org/signatures/bukkit/SuperLog.svg)

### Requirements
* Java 1.8
* Any bukkit based server

### Links and Contacts
* [Spigot page](https://www.spigotmc.org/resources/superlog-async-1-7-1-16.65399/)
* [Bukkit page](https://dev.bukkit.org/projects/superlog-1-7-1-13-async)
* [Documentation](http://superlog.andross.fr/)

For any bug/suggestions: `Andross#5254`