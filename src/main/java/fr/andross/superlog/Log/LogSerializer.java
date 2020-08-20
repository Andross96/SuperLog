/*
 * SuperLog - Save almost all minecraft actions into logs!
 * Copyright (C) 2020 André Sustac
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.andross.superlog.log;

import fr.andross.superlog.SuperLog;
import fr.andross.superlog.utils.LoggedEvent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Log serializer for an event
 * @version 1.2
 * @author Andross
 */
public final class LogSerializer {
    private final SuperLog pl;
    private final LoggedEvent loggedEvent;
    private final Event event;
    private String message;
    private String playerName = null;
    private String eventType = null;

    protected LogSerializer(@NotNull final SuperLog pl, @NotNull final LoggedEvent loggedEvent, @NotNull final Event event) {
        this.pl = pl;
        this.loggedEvent = loggedEvent;
        this.event = event;
        message = loggedEvent.getMessage();
    }

    /**
     * Quick utility to quickly args into the message
     * @param arg argument
     * @param value value
     */
    private void replace(@NotNull final String arg, @Nullable final String value) {
        message = message.replace(arg, value == null ? "Unknown" : value);
    }

    /**
     * Checking if this event should not be logged
     * @param condition condition to check
     * @param fieldName field to check
     * @return true if this event should not be logged, otherwise false
     */
    protected boolean isAnIgnoredEvent(@Nullable String condition, @Nullable String fieldName) {
        if (condition == null) return false;
        condition = condition.toUpperCase();
        if (fieldName != null) fieldName = fieldName.toUpperCase();
        boolean isIgnored = false;

        // Checking ignored type
        final List<String> ignored = loggedEvent.getConditions((fieldName == null ? "IGNORED" : fieldName + "-IGNORED"));
        if (ignored != null) isIgnored = ignored.stream().anyMatch(condition::equalsIgnoreCase);

        // Checking logged type
        if (!isIgnored) {
            final List<String> logged = loggedEvent.getConditions((fieldName == null ? "LOGGED" : fieldName + "-LOGGED"));
            if (logged != null) isIgnored = logged.stream().noneMatch(condition::equalsIgnoreCase);
        }

        return isIgnored;
    }

    /**
     * Serializing the event
     * @param e bukkit event
     * @return true if the event should be ignored, otherwise false
     */
    @SuppressWarnings("unchecked")
    protected boolean serializeFields(@NotNull final Event e) {
        final Field[] fields = loggedEvent.getFields();
        if (fields == null) return false;

        for (final Field f : fields) {
            Object o;
            try {
                o = f.get(e);
            } catch (final Exception ex) {
                // Should not happen, as its checked on loading
                pl.getLogger().info("Unknown field '" + f.getName() + "' for event '" + e.getEventName() + "'.");
                continue;
            }
            if (o == null) continue;

            final String fieldName = f.getName().toUpperCase();
            if (o instanceof String) {
                if (isAnIgnoredEvent((String) o, fieldName)) return true;
                replace("{" + fieldName + "}", (String) o);
            } else if (o instanceof Integer || o instanceof Boolean || o instanceof Long) replace("{" + fieldName + "}", String.valueOf(o));
            else if (o instanceof Advancement) replace("{" + fieldName + "}", ((Advancement) o).getKey().getKey());
            else if (o instanceof AnimalTamer) replace("{" + fieldName + "}", ((AnimalTamer) o).getName());
            else if (o instanceof ItemStack || o instanceof Item) {
                final ItemStack item;
                if (o instanceof ItemStack) item = (ItemStack) o; else item = ((Item) o).getItemStack();
                if (isAnIgnoredEvent(item.getType().name(), fieldName)) return true;
                replace("{" + fieldName + ".NAME}", item.getType().name());
                replace("{" + fieldName + ".AMOUNT}", String.valueOf(item.getAmount()));
            } else if (o instanceof Material) {
                final String materialName = ((Material) o).name();
                if (isAnIgnoredEvent(materialName, fieldName)) return true;
                replace("{" + fieldName + "}", materialName);
            } else if (o instanceof World) {
                final String worldName = ((World) o).getName();
                if (isAnIgnoredEvent(worldName, fieldName)) return true;
                replace("{" + fieldName + "}", worldName);
            } else if (o instanceof Block) {
                final Block b = (Block) o;
                if (isAnIgnoredEvent(b.getType().name(), fieldName)) return true;
                serialize(b, fieldName);
            } else if (o instanceof Location) {
                final Location location = (Location) o;
                replace("{" + fieldName + ".LOCWORLD}", location.getWorld() == null ? null : location.getWorld().getName());
                replace("{" + fieldName + ".LOCX}", String.valueOf(location.getBlockX()));
                replace("{" + fieldName + ".LOCY}", String.valueOf(location.getBlockY()));
                replace("{" + fieldName + ".LOCZ}", String.valueOf(location.getBlockZ()));
            } else if (o instanceof Entity) {
                final Entity entity = (Entity) o;
                if (isAnIgnoredEvent(entity.getType().name(), fieldName)) return true;
                serialize(entity, fieldName);
            } else if (o instanceof Inventory) replace("{" + fieldName + ".TYPE}", ((Inventory) o).getType().name());
            else if (o instanceof CommandSender) replace("{" + fieldName + ".NAME}", ((CommandSender) o).getName());
            else if (o instanceof List) {
                try {
                    final List<Object> list = (List<Object>) o;
                    if (!list.isEmpty()) {
                        if (list.get(0) instanceof ItemStack) { // List of ItemStack
                            final List<ItemStack> itemList = (List<ItemStack>) o;
                            final Set<String> itemNames = new HashSet<>();
                            itemList.forEach(i -> itemNames.add(i.getType().name()));
                            replace("{" + fieldName + "}", String.join(",", itemNames));
                        } else {
                            replace("{" + fieldName + "}", String.join(",", ((List<String>) o))); // List of String
                        }
                    }
                } catch (final Exception ex) {
                    pl.getLogger().info("Unsupported list '" + fieldName + "' for '" + e.getEventName() + "'.");
                }
            } else if (o instanceof Map) {
                try {
                    final Map<Enchantment, Integer> enchants = (Map<Enchantment, Integer>) o;
                    final StringBuilder message = new StringBuilder();
                    for (Map.Entry<Enchantment, Integer> map : enchants.entrySet()) {
                        message.append("[Enchantement: ");
                        message.append(map.getKey().getKey());
                        message.append("; Level: ");
                        message.append(map.getValue());
                        message.append("]");
                    }
                    replace("{" + fieldName + "}", message.toString());
                } catch (final Exception ex) {
                    pl.getLogger().info("Invalid map for '" + e.getEventName() + "'. Only Map<Enchantement, Integer> is supported.");
                }
            } else if (o instanceof MerchantRecipe) replace("{" + fieldName + "}", ((MerchantRecipe) o).getResult().getType().name());
            else if (o instanceof Enum) {
                final String enums = String.valueOf(o);
                if (isAnIgnoredEvent(enums, fieldName)) return true;
                replace("{" + fieldName + "}", enums);
            }
        }
        return false;
    }

    /**
     * Serializing a block
     * @param b block
     * @param fieldName field name
     */
    @SuppressWarnings("deprecation")
    protected void serialize(@NotNull final Block b, @Nullable final String fieldName) {
        final List<String> args = loggedEvent.getConditions("ARGS");
        if (args == null) return;

        // Serializing a Block
        final Location loc = b.getLocation();
        for (final String arg : args) {
            final String value;
            switch (arg) {
                case "NAME": value = pl.getLogConfig().isBukkitNewApi() ? b.getBlockData().getAsString() : b.getType().name() + ":" + b.getData(); break;
                case "LOCWORLD": value = loc.getWorld() == null ? "Unknown" : loc.getWorld().getName(); break;
                case "LOCX": value = String.valueOf(loc.getBlockX()); break;
                case "LOCY": value = String.valueOf(loc.getBlockY()); break;
                case "LOCZ": value = String.valueOf(loc.getBlockZ()); break;
                default: continue;
            }
            replace("{" + (fieldName == null ? "" : fieldName + ".") + arg + "}", value);
        }
    }

    /**
     * Serializing an entity
     * @param e the entity
     * @param fieldName field name
     */
    protected void serialize(@NotNull final Entity e, @Nullable final String fieldName) {
        final List<String> args = loggedEvent.getConditions("ARGS");
        if (args == null) return;

        // Serializing an Entity
        for (final String arg : args) {
            String value = null;
            switch (arg) {
                case "NAME": value = e.getName(); break;
                case "TYPE": value = e.getType().name(); break;
                case "HEALTH":
                    if (!(e instanceof LivingEntity)) break;
                    value = String.valueOf(((LivingEntity) e).getHealth());
                    break;
                case "IP":
                    if (!(e instanceof Player)) break;
                    final Player p = ((Player) e);
                    final InetSocketAddress address = p.getAddress();
                    if (address == null) value = "Unknown";
                    else value = address.getAddress().getHostAddress();
                    break;
                case "LOCWORLD":
                    value = e.getWorld().getName();
                    break;
                case "LOCX":
                    value = String.valueOf(e.getLocation().getBlockX());
                    break;
                case "LOCY":
                    value = String.valueOf(e.getLocation().getBlockY());
                    break;
                case "LOCZ":
                    value = String.valueOf(e.getLocation().getBlockZ());
                    break;
                case "LASTDEATHCAUSE":
                    final EntityDamageEvent event = e.getLastDamageCause();
                    if (event == null) value = "Unknown";
                    else value = event.getCause().name();
                    break;
                case "LASTDEATHBY":
                    if (!(e instanceof LivingEntity)) break;
                    final Player killer = ((LivingEntity) e).getKiller();
                    if (killer == null) value = "Unknown";
                    else value = killer.getName();
                    break;
                default: continue;
            }
            replace("{" + (fieldName == null ? "" : fieldName + ".") + arg + "}", value);
        }
    }

    /**
     * Serializing an inventory
     * @param iv the inventory view
     * @param playerLocation location of the player (for MC<1.13)
     */
    protected void serialize(@NotNull final InventoryView iv, @NotNull final Location playerLocation) {
        final List<String> args = loggedEvent.getConditions("ARGS");
        if (args == null) return;

        // Serializing an Inventory
        final Location loc;
        if (pl.getLogConfig().isBukkitNewApi()) loc = iv.getTopInventory().getLocation();
        else loc = playerLocation.clone();
        for (final String arg : args) {
            String value = null;
            switch (arg) {
                case "NAME": value = iv.getTitle(); break;
                case "TYPE": value = (iv.getType() == InventoryType.CRAFTING ? "INVENTORY" : iv.getType().name()); break;
                case "LOCWORLD": if(loc != null && loc.getWorld() != null) value = loc.getWorld().getName(); break;
                case "LOCX": value = loc == null ? "?" : String.valueOf(loc.getBlockX()); break;
                case "LOCY": value = loc == null ? "?" : String.valueOf(loc.getBlockY()); break;
                case "LOCZ": value = loc == null ? "?" : String.valueOf(loc.getBlockZ()); break;
                case "ITEMS":
                    final StringBuilder message = new StringBuilder();
                    final ItemStack[] storage = iv.getType() == InventoryType.CRAFTING ? iv.getBottomInventory().getContents() : iv.getTopInventory().getContents();
                    for (final ItemStack item : storage) {
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
            replace("{" + arg + "}", value);
        }
    }

    /**
     * Serializing a plugin event
     * @param plugin plugin
     */
    protected void serialize(@NotNull final Plugin plugin) {
        final List<String> args = loggedEvent.getConditions("ARGS");
        if (args == null) return;

        for (final String arg : args) {
            final String value;
            switch(arg) {
                case "NAME": value = plugin.getName(); break;
                case "DESCRIPTION": value = plugin.getDescription().getDescription(); break;
                case "AUTHOR": value = String.join(",", plugin.getDescription().getAuthors()); break;
                case "VERSION": value = plugin.getDescription().getVersion(); break;
                default: continue;
            }
            replace("{" + arg + "}", value);
        }
    }

    /**
     * Serializing a weather/world event
     */
    protected void serialize(@NotNull final World world) {
        final List<String> args = loggedEvent.getConditions("ARGS");
        if (args != null && args.contains("WORLD")) replace("{WORLD}", world.getName());
    }

    /**
     * Serializing a chunk event
     */
    protected void serialize(@NotNull final Chunk c) {
        final List<String> args = loggedEvent.getConditions("ARGS");
        if (args == null) return;
        for (final String arg : args) {
            final String value;
            switch (arg) {
                case "LOCWORLD": value = c.getWorld().getName(); break;
                case "LOCX": value = String.valueOf(c.getX()); break;
                case "LOCZ": value = String.valueOf(c.getZ()); break;
                case "SLIME": value = String.valueOf(c.isSlimeChunk()); break;
                default: continue;
            }
            replace("{" + arg + "}", value);
        }
    }

    /**
     * The event
     * @return the event
     */
    @NotNull
    public Event getEvent() {
        return event;
    }

    /**
     * Get the log message
     * @return the log message
     */
    @NotNull
    public String getMessage() {
        return message;
    }

    /**
     * Get the player involved into this event, if one is involved
     * @return the player involved into this event, null if no player is involved
     */
    @Nullable
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Set the player related to this event
     * @param playerName the player name
     */
    public void setPlayerName(@NotNull final String playerName) {
        this.playerName = playerName;
    }

    /**
     * Get the event type of this event
     * @return the event type
     */
    @NotNull
    public String getEventType() {
        return eventType;
    }

    /**
     * Set the event type
     * @param eventType event type
     */
    public void setEventType(@NotNull final String eventType) {
        this.eventType = eventType;
    }
}
