package fr.superlog.Log;

import fr.superlog.Log.Utils.LogEvent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.hanging.HangingEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.server.PluginEvent;
import org.bukkit.event.vehicle.VehicleEvent;
import org.bukkit.event.weather.WeatherEvent;
import org.bukkit.event.world.ChunkEvent;
import org.bukkit.event.world.WorldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.Set;

public class LogEvents {
    private final Event e;
    private LogSerializer s;
    private String eventType;
    private String playerName;

    protected LogEvents(final Event e, final LogEvent logEvent) {
        this.e = e;
        this.s = new LogSerializer(logEvent);
    }

    public String getPlayerName(){ return playerName; }
    public String getEventType(){ return eventType; }
    public String getEventName(){ return e.getEventName(); }
    public String getMessage(){ return s.getMessage(); }

    protected LogEvents runPlayerEvents(boolean isCitizensEnabled){
        // Getting Player
        final Player p = ((PlayerEvent)e).getPlayer();
        if(Log.DEBUG) Log.LOGGER.info("[Debug] Started " + e.getEventName());
        // Checking if it's a npc
        if(isCitizensEnabled && p.hasMetadata("NPC")) return null;

        // Checking conditions
        if(s.isAnIgnoredEvent(p.getName(), null)) return null;
        if(Log.DEBUG) Log.LOGGER.info("[Debug] Condition: ok");
        if(s.serializeFields(e)) return null;
        if(Log.DEBUG) Log.LOGGER.info("[Debug] Fields condition: ok");

        // Serializing the rest of the event
        playerName = p.getName();
        eventType = "PlayerEvents";
        s.serialize(p, null);
        if(Log.DEBUG) Log.LOGGER.info("[Debug] Serializing: ok");
        return this;
    }

    protected LogEvents runBlockEvents() {
        // We ignore a BlockMultiPlaceEvent
        if(e instanceof BlockMultiPlaceEvent) return null;

        // Getting Block
        final Block b = ((BlockEvent)e).getBlock();

        // Checking conditions
        if(s.isAnIgnoredEvent(b.getType().name(), null)) return null;
        if(s.serializeFields(e)) return null;

        // Block event related to a player?
        Player p = null;
        try{
            Field f = e.getClass().getDeclaredField("player");
            f.setAccessible(true);
            p = (Player)f.get(e);
        }catch(Exception ex){ /* we don't care */ }

        // Adding args messages for block & player
        eventType = "BlockEvents";
        if(p != null) playerName = p.getName();
        s.serialize(b, null);
        return this;
    }

    protected LogEvents runEntityEvents(){
        // Getting Entity, or the player if it's a player related event
        final Entity entity = ((EntityEvent)e).getEntity();
        final Player p = (entity instanceof Player) ? ((Player)entity) : null;

        // Checking conditions
        if(s.isAnIgnoredEvent(entity.getType().name(), null)) return null;
        if(s.serializeFields(e)) return null;

        // Serializing the event
        eventType = "EntityEvents";
        if(p != null) playerName = p.getName();
        s.serialize(entity, null);
        return this;
    }

    protected LogEvents runHangingEvents(){
        // Getting the Entity
        final Entity entity = ((HangingEvent)e).getEntity();

        // Checking conditions
        if(s.serializeFields(e)) return null;

        // Serializing the event
        eventType = "HangingEvents";
        s.serialize(entity, null);
        return this;
    }

    protected LogEvents runInventoryEvents() {
        // Getting Inventory
        final InventoryView iv = ((InventoryEvent)e).getView();
        final Inventory i = iv.getTopInventory();
        final HumanEntity p = iv.getPlayer();

        // Checking conditions
        if(s.isAnIgnoredEvent(iv.getType().name(), null)) return null;
        if(s.serializeFields(e)) return null;

        // Serializing the event
        eventType = "InventoryEvents";
        playerName = p.getName();

        s.serialize(p, "PLAYER");

        final Set<String> args = s.getLogEvent().getConditions("ARGS");
        if(args == null) return this;

        // Serializing an Inventory
        for(String arg : args) {
            Location loc = iv.getTopInventory().getLocation();
            String value = null;
            switch(arg) {
                case "NAME": value =  iv.getTitle(); break;
                case "TYPE": value = (iv.getType() == InventoryType.CRAFTING ? "INVENTORY" : iv.getType().name()); break;
                case "LOCWORLD": if(loc != null && loc.getWorld() != null) value = loc.getWorld().getName(); break;
                case "LOCX": value = String.valueOf(loc.getBlockX()); break;
                case "LOCY": value = String.valueOf(loc.getBlockY()); break;
                case "LOCZ": value = String.valueOf(loc.getBlockZ()); break;
                case "ITEMS":
                    final StringBuilder message = new StringBuilder();
                    final ItemStack[] storage = iv.getType() == InventoryType.CRAFTING ? p.getInventory().getContents() : i.getContents();
                    for(ItemStack item : storage) {
                        if(item == null || item.getType() == Material.AIR) continue;
                        message.append("[Name: ");
                        message.append(item.getType().name());
                        message.append("; Amount: ");
                        message.append(item.getAmount());
                        message.append("]");
                    }
                    final String fMessage = message.toString();
                    value = fMessage.isEmpty() ? "nothing" : fMessage;
                    break;
                default: continue;
            }
            s.replace("{" + arg + "}", value);
        }
        return this;
    }

    protected LogEvents runServerEvents() {
        eventType = "ServerEvents";
        return s.serializeFields(e) ? null : this;
    }

    protected LogEvents runPluginEvents() {
        final Plugin pl = ((PluginEvent)e).getPlugin();

        // Checking conditions & fields
        if(s.isAnIgnoredEvent(pl.getName(), null)) return null;
        if(s.serializeFields(e)) return null;

        eventType = "PluginEvents";

        // Serializing the rest of the event
        Set<String> args = s.getLogEvent().getConditions("ARGS");
        if(args == null) return this;
        for(String arg : args) {
            String value;
            switch(arg) {
                case "NAME": value = pl.getName(); break;
                case "DESCRIPTION": value = pl.getDescription().getDescription(); break;
                case "AUTHOR": value = String.join(",", pl.getDescription().getAuthors()); break;
                case "VERSION": value = pl.getDescription().getVersion(); break;
                default: continue;
            }
            s.replace(arg, value);
        }
        return this;
    }

    protected LogEvents runVehicleEvents() {
        eventType = "VehicleEvents";

        // Serializing
        if(s.serializeFields(e)) return null;
        s.serialize(((VehicleEvent)e).getVehicle(), null);
        return this;
    }

    protected LogEvents runWeatherEvents() {
        eventType = "WeatherEvents";

        // Serializing
        if(s.serializeFields(e)) return null;
        final Set<String> args = s.getLogEvent().getConditions("ARGS");
        if(args != null && args.contains("WORLD")) s.replace("{WORLD}", ((WeatherEvent)e).getWorld().getName());
        return this;
    }

    protected LogEvents runWorldEvents() {
        eventType = "WorldEvents";

        // Adding args for world
        if(s.serializeFields(e)) return null;
        final Set<String> args = s.getLogEvent().getConditions("ARGS");
        if(args != null && args.contains("WORLD")) s.replace("{WORLD}", ((WorldEvent)e).getWorld().getName());
        return this;
    }

    protected LogEvents runChunkEvents() {
        final Chunk c = ((ChunkEvent)e).getChunk();
        eventType = "ChunkEvents";

        // Adding args for chunk
        if(s.serializeFields(e)) return null;
        final Set<String> args = s.getLogEvent().getConditions("ARGS");
        if(args == null) return this;
        for(String arg : args) {
            String value;
            switch(arg) {
                case "LOCWORLD": value = c.getWorld().getName(); break;
                case "LOCX": value = String.valueOf(c.getX()); break;
                case "LOCZ": value = String.valueOf(c.getZ()); break;
                case "SLIME": value = String.valueOf(c.isSlimeChunk()); break;
                default: continue;
            }
            s.replace(arg, value);
        }
        return this;
    }
}
