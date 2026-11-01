package io.github.julz0711.rushhour;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.util.*;

/** Durable pre-game state. The file is removed only after successful restoration. */
final class PlayerBackup {
    private final RushHourPlugin plugin;
    PlayerBackup(RushHourPlugin plugin) { this.plugin = plugin; }
    private File file(Player player) { return new File(new File(plugin.getDataFolder(), "recovery"), player.getUniqueId() + ".yml"); }
    boolean has(Player p) { return file(p).isFile(); }
    void capture(Player p) {
        if (has(p)) return;
        var c = new YamlConfiguration();
        c.set("location", p.getLocation()); c.set("mode", p.getGameMode().name());
        c.set("contents", Arrays.asList(p.getInventory().getContents()));
        c.set("ender-chest", Arrays.asList(p.getEnderChest().getContents()));
        c.set("cursor", p.getItemOnCursor());
        c.set("exp", p.getExp()); c.set("level", p.getLevel()); c.set("total-exp", p.getTotalExperience());
        c.set("health", p.getHealth()); c.set("food", p.getFoodLevel()); c.set("saturation", p.getSaturation());
        c.set("flight", p.getAllowFlight()); c.set("flying", p.isFlying());
        c.set("effects", new ArrayList<>(p.getActivePotionEffects()));
        c.set("respawn", p.getRespawnLocation());
        var serializer = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson();
        if (p.playerListName() != null) c.set("tab-name", serializer.serialize(p.playerListName()));
        if (p.playerListHeader() != null) c.set("tab-header", serializer.serialize(p.playerListHeader()));
        if (p.playerListFooter() != null) c.set("tab-footer", serializer.serialize(p.playerListFooter()));
        List<String> criteria = new ArrayList<>();
        Bukkit.advancementIterator().forEachRemaining(a -> p.getAdvancementProgress(a).getAwardedCriteria()
                .forEach(criterion -> criteria.add(a.getKey() + "|" + criterion)));
        c.set("advancements", criteria);
        try {
            File f = file(p);
            if (!f.getParentFile().isDirectory() && !f.getParentFile().mkdirs()) throw new IOException("Cannot create recovery directory");
            c.save(f);
        } catch (IOException e) { throw new IllegalStateException("Cannot back up " + p.getName() + "; round cancelled", e); }
    }
    void resetAdvancements(Player p) {
        Bukkit.advancementIterator().forEachRemaining(a -> {
            var progress = p.getAdvancementProgress(a);
            for (String criterion : new ArrayList<>(progress.getAwardedCriteria())) progress.revokeCriteria(criterion);
        });
    }
    boolean restore(Player p) {
        File f = file(p);
        if (!f.isFile()) return false;
        var c = YamlConfiguration.loadConfiguration(f);
        Location location = c.getLocation("location");
        if (location != null && location.getWorld() != null) p.teleport(location);
        p.setGameMode(GameMode.valueOf(c.getString("mode", "SURVIVAL")));
        p.getInventory().clear();
        List<?> contents = c.getList("contents", List.of());
        for (int i = 0; i < Math.min(contents.size(), p.getInventory().getSize()); i++) {
            if (contents.get(i) instanceof ItemStack stack) p.getInventory().setItem(i, stack);
        }
        if (c.contains("ender-chest")) {
            p.getEnderChest().clear(); List<?> ender = c.getList("ender-chest", List.of());
            for (int i = 0; i < Math.min(ender.size(), p.getEnderChest().getSize()); i++) if (ender.get(i) instanceof ItemStack stack) p.getEnderChest().setItem(i, stack);
        }
        p.setItemOnCursor(c.getItemStack("cursor", new ItemStack(Material.AIR)));
        p.setTotalExperience(c.getInt("total-exp")); p.setLevel(c.getInt("level")); p.setExp((float) c.getDouble("exp"));
        p.setHealth(Math.min(c.getDouble("health", 20), p.getMaxHealth()));
        p.setFoodLevel(c.getInt("food", 20)); p.setSaturation((float) c.getDouble("saturation", 5));
        p.setAllowFlight(c.getBoolean("flight")); p.setFlying(c.getBoolean("flying") && p.getAllowFlight());
        p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
        for (Object effect : c.getList("effects", List.of())) if (effect instanceof org.bukkit.potion.PotionEffect potion) p.addPotionEffect(potion);
        p.setRespawnLocation(c.getLocation("respawn"), true);
        var serializer = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson();
        if (c.contains("tab-name")) p.playerListName(serializer.deserialize(c.getString("tab-name")));
        p.sendPlayerListHeaderAndFooter(serializer.deserialize(c.getString("tab-header", "{\"text\":\"\"}")), serializer.deserialize(c.getString("tab-footer", "{\"text\":\"\"}")));
        resetAdvancements(p);
        for (String line : c.getStringList("advancements")) {
            int split = line.indexOf('|'); if (split < 0) continue;
            var a = Bukkit.getAdvancement(NamespacedKey.fromString(line.substring(0, split)));
            if (a != null) p.getAdvancementProgress(a).awardCriteria(line.substring(split + 1));
        }
        p.setFireTicks(0); p.setFreezeTicks(0); p.setFallDistance(0);
        if (!f.delete()) plugin.getLogger().warning("Restored " + p.getName() + " but could not remove " + f);
        return true;
    }
}
