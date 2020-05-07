package fr.andross.superlog.Log;

import fr.andross.superlog.Log.Utils.LogEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LogSerializer {
    private final LogEvent logEvent;
    private String message;

    protected LogSerializer(final LogEvent logEvent) {
        this.logEvent = logEvent;
        message = logEvent.getDefaultMessage();
    }

    protected String getMessage() {
        return message;
    }
    protected LogEvent getLogEvent() {
        return logEvent;
    }

    protected void replace(final String arg, final String value){
        message = message.replace(arg, value);
    }

    protected boolean isAnIgnoredEvent(String condition, String fieldName) {
        if (condition == null) return false;
        condition = condition.toUpperCase();
        if (fieldName != null) fieldName = fieldName.toUpperCase();
        boolean isIgnored = false;

        // Checking ignored type
        final Set<String> ignored = logEvent.getConditions((fieldName == null ? "IGNORED" : fieldName + "-IGNORED"));
        if (ignored != null) isIgnored = ignored.stream().anyMatch(condition::equalsIgnoreCase);

        // Checking logged type
        if (!isIgnored) {
            final Set<String> logged = logEvent.getConditions((fieldName == null ? "LOGGED" : fieldName + "-LOGGED"));
            if (logged != null) isIgnored = logged.stream().noneMatch(condition::equalsIgnoreCase);
        }

        return isIgnored;
    }

    @SuppressWarnings("unchecked")
    protected boolean serializeFields(final Event e) {
        final Field[] fields = logEvent.getFields();
        if(fields == null) return false;

        for(final Field f : fields){
            Object o;
            try {
                o = f.get(e);
            } catch (Exception ex){
                Log.LOGGER.info("Unknown field '" + f.getName() + "' for event '" + e.getEventName() + "'.");
                continue;
            }
            if (o == null) continue;

            final String fieldName = f.getName().toUpperCase();
            if (o instanceof String) {
                if (isAnIgnoredEvent((String)o, fieldName)) return true;
                replace("{" + fieldName + "}", (String)o);
            } else if (o instanceof Integer || o instanceof Boolean) replace("{" + fieldName + "}", String.valueOf(o));
            else if (o instanceof Advancement) replace("{" + fieldName + "}", ((Advancement)o).getKey().getKey());
            else if (o instanceof AnimalTamer) replace("{" + fieldName + "}", ((AnimalTamer)o).getName());
            else if (o instanceof ItemStack || o instanceof Item) {
                final ItemStack item;
                if (o instanceof ItemStack) item = (ItemStack)o; else item = ((Item)o).getItemStack();
                if (isAnIgnoredEvent(item.getType().name(), fieldName)) return true;
                replace("{" + fieldName + ".NAME}", item.getType().name());
                replace("{" + fieldName + ".AMOUNT}", String.valueOf(item.getAmount()));
            } else if (o instanceof Material) {
                final String materialName = ((Material)o).name();
                if(isAnIgnoredEvent(materialName, fieldName)) return true;
                replace("{" + fieldName + "}", materialName);
            } else if (o instanceof World) {
                final String worldName = ((World) o).getName();
                if (isAnIgnoredEvent(worldName, fieldName)) return true;
                replace("{" + fieldName + "}", worldName);
            } else if (o instanceof Block) {
                final Block b = (Block)o;
                if (isAnIgnoredEvent(b.getType().name(), fieldName)) return true;
                serialize((Block)o, fieldName);
            } else if (o instanceof Location) {
                final Location location = (Location)o;
                replace("{" + fieldName + ".LOCWORLD}", location.getWorld().getName());
                replace("{" + fieldName + ".LOCX}", String.valueOf(location.getBlockX()));
                replace("{" + fieldName + ".LOCY}", String.valueOf(location.getBlockY()));
                replace("{" + fieldName + ".LOCZ}", String.valueOf(location.getBlockZ()));
            } else if (o instanceof Entity) {
                final Entity entity = (Entity)o;
                if(isAnIgnoredEvent(entity.getType().name(), fieldName)) return true;
                serialize((Entity)o, fieldName);
            } else if (o instanceof Inventory) replace("{" + fieldName + ".TYPE}", ((Inventory)o).getType().name());
            else if (o instanceof CommandSender) replace("{" + fieldName + ".NAME}", ((CommandSender)o).getName());
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
                } catch (Exception ex) {
                    Log.LOGGER.info("Unsupported list '" + fieldName + "' for '" + e.getEventName() + "'.");
                }
            } else if(o instanceof Map) {
                try {
                    final Map<Enchantment, Integer> enchants = (Map<Enchantment, Integer>)o;
                    final StringBuilder message = new StringBuilder();
                    for (Map.Entry<Enchantment, Integer> map : enchants.entrySet()) {
                        message.append("[Enchantement: ");
                        message.append(map.getKey().getKey());
                        message.append("; Level: ");
                        message.append(map.getValue());
                        message.append("]");
                    }
                    replace("{" + fieldName + "}", message.toString());
                } catch (Exception ex) {
                    Log.LOGGER.info("Invalid map for '" + e.getEventName() + "'. Only Map<Enchantement, Integer> is supported");
                }
            } else if (o instanceof MerchantRecipe) replace("{" + fieldName + "}", ((MerchantRecipe)o).getResult().getType().name());
            else if(o instanceof Enum) {
                final String enums = String.valueOf(o);
                if (isAnIgnoredEvent(enums, fieldName)) return true;
                replace("{" + fieldName + "}", enums);
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    protected void serialize(final Block b, final String fieldName) {
        final Set<String> args = logEvent.getConditions("ARGS");
        if (args == null) return;

        // Serializing a Block
        for (final String arg : args) {
            final String value;
            switch(arg) {
                case "NAME": value = logEvent.isVersionBiggerThan1_13() ? b.getBlockData().getAsString() : b.getType().name() + ":" + b.getData(); break;
                case "LOCWORLD": value = b.getLocation().getWorld().getName(); break;
                case "LOCX": value = String.valueOf(b.getLocation().getBlockX()); break;
                case "LOCY": value = String.valueOf(b.getLocation().getBlockY()); break;
                case "LOCZ": value = String.valueOf(b.getLocation().getBlockZ()); break;
                default: continue;
            }
            replace("{" + (fieldName == null ? "" : fieldName + ".") + arg + "}", value);
        }
    }

    protected void serialize(final Entity e, final String fieldName) {
        final Set<String> args = logEvent.getConditions("ARGS");
        if(args == null) return;

        // Serializing an Entity
        for (final String arg : args) {
            String value = null;
            switch (arg) {
                case "NAME":
                    value = e.getName();
                    break;
                case "TYPE":
                    value = e.getType().name();
                    break;
                case "HEALTH":
                    if (!(e instanceof LivingEntity)) break;
                    value = String.valueOf(((LivingEntity) e).getHealth());
                    break;
                case "IP":
                    if (!(e instanceof Player)) break;
                    Player p = ((Player) e);
                    try {
                        value = p.getAddress().getAddress().getHostAddress();
                    } catch (Exception ex) {
                        value = "Unknown";
                    }
                    break;
                case "LOCWORLD":
                    value = e.getLocation().getWorld().getName();
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
                    try {
                        value = e.getLastDamageCause().getCause().name();
                    } catch (Exception ex) {
                        value = "Unknown";
                    }
                    break;
                case "LASTDEATHBY":
                    try {
                        value = ((Player) e).getKiller().getName();
                    } catch (Exception ex) {
                        value = "Unknown";
                    }
                    break;
                default:
                    continue;
            }
            replace("{" + (fieldName == null ? "" : fieldName + ".") + arg + "}", value);
        }
    }

}
