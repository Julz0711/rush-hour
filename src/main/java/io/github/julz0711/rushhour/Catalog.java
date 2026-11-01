package io.github.julz0711.rushhour;

import io.github.julz0711.rushhour.game.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import java.io.*;
import java.util.*;
import static io.github.julz0711.rushhour.game.Objective.*;

public final class Catalog {
    private final RushHourPlugin plugin;
    private final List<Objective> all = new ArrayList<>();
    private final Map<String, Objective> byId = new HashMap<>();
    public Catalog(RushHourPlugin plugin) {
        this.plugin = plugin;
        plugin.saveResourceIfAbsent("items.yml"); plugin.saveResourceIfAbsent("objectives.yml");
        for (String name : List.of("easy", "no-end", "wood-age")) plugin.saveResourceIfAbsent("presets/" + name + ".yml");
        var items = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "items.yml"));
        ConfigurationSection data = items.getConfigurationSection("items");
        // Every registry item is visible; unknown items require explicit review before entering the pool.
        for (Material material : Material.values()) {
            if (material.name().startsWith("LEGACY_") || material.isAir() || !material.isItem()) continue;
            String key = material.getKey().getKey();
            var section = data == null ? null : data.getConfigurationSection(key);
            add(new Objective(Type.ITEM, key, section == null ? "unreviewed" : section.getString("category", "misc"),
                    section == null ? Tier.INSANE : Tier.valueOf(section.getString("tier", "EASY")),
                    section == null ? List.of(EnumSet.allOf(Dimension.class)) : routes(section),
                    section != null && section.getBoolean("enabled", true)));
        }
        var others = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "objectives.yml"));
        for (Type type : Type.values()) {
            if (type == Type.ITEM) continue;
            var entries = others.getConfigurationSection(type.name());
            if (entries == null) continue;
            for (String key : entries.getKeys(false)) {
                var s = entries.getConfigurationSection(key);
                if (s == null || !valid(type, key)) { plugin.getLogger().warning("Unknown objective: " + type + ":" + key); continue; }
                add(new Objective(type, key, s.getString("category", type.name().toLowerCase(Locale.ROOT)),
                        Tier.valueOf(s.getString("tier", "MEDIUM")), routes(s), s.getBoolean("enabled", true)));
            }
        }
    }
    private boolean valid(Type type, String key) {
        var namespaced = NamespacedKey.minecraft(key);
        return switch (type) {
            case MOB -> { try { yield EntityType.valueOf(key.toUpperCase(Locale.ROOT)).isAlive(); } catch (IllegalArgumentException e) { yield false; } }
            case BIOME -> Registry.BIOME.get(namespaced) != null;
            case STRUCTURE -> Registry.STRUCTURE.get(namespaced) != null;
            case ADVANCEMENT -> Bukkit.getAdvancement(namespaced) != null;
            default -> true;
        };
    }
    private static List<Set<Dimension>> routes(ConfigurationSection s) {
        List<Set<Dimension>> routes = new ArrayList<>();
        for (String route : s.getStringList("routes")) {
            EnumSet<Dimension> dims = EnumSet.noneOf(Dimension.class);
            for (String d : route.split("\\+")) dims.add(Dimension.valueOf(d.trim()));
            routes.add(dims);
        }
        if (routes.isEmpty()) routes.add(EnumSet.allOf(Dimension.class));
        return routes;
    }
    private void add(Objective o) { all.add(o); byId.put(o.id(), o); }
    public List<Objective> all() { return List.copyOf(all); }
    public Objective get(String id) { return byId.get(id); }
    public boolean enabled(Objective o) {
        var c = plugin.getConfig();
        String path = "catalog.overrides." + o.id().replace('.', '_');
        if (c.contains(path)) return c.getBoolean(path);
        if (!c.getBoolean("catalog.categories." + o.category(), true)) return false;
        return !c.getBoolean("catalog.whitelist") && o.enabledByDefault();
    }
    public void toggle(Objective o) { plugin.getConfig().set("catalog.overrides." + o.id().replace('.', '_'), !enabled(o)); plugin.saveConfig(); }
    public void toggleCategory(String category) {
        boolean enable = all.stream().filter(o -> o.category().equals(category)).anyMatch(o -> !enabled(o));
        for (Objective o : all) if (o.category().equals(category)) plugin.getConfig().set("catalog.overrides." + o.id().replace('.', '_'), enable);
        plugin.saveConfig();
    }
    public ObjectivePool pool(Settings settings) {
        return new ObjectivePool(all.stream().filter(this::enabled).filter(o -> o.available(settings.dimensions)).toList(),
                settings.typeWeights, settings.tierWeights, settings.ramp, new Random());
    }
    public void preset(String action, String name) throws IOException {
        if (!name.matches("[a-zA-Z0-9_-]{1,32}")) throw new IllegalArgumentException("Preset names use 1..32 letters, digits, _ or -");
        File directory = new File(plugin.getDataFolder(), "presets");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create presets directory");
        File file = new File(directory, name + ".yml");
        if (action.equals("save")) {
            var yaml = new YamlConfiguration();
            yaml.set("catalog", plugin.getConfig().getConfigurationSection("catalog")); yaml.save(file);
        } else if (action.equals("load")) {
            if (!file.isFile()) throw new IllegalArgumentException("Unknown preset " + name);
            var yaml = YamlConfiguration.loadConfiguration(file);
            if (!yaml.isConfigurationSection("catalog")) throw new IllegalArgumentException("Invalid preset");
            plugin.getConfig().set("catalog", yaml.getConfigurationSection("catalog")); plugin.saveConfig();
        } else throw new IllegalArgumentException("Use preset save|load <name>");
    }
    public static Dimension dimension(World world) {
        return switch (world.getEnvironment()) { case NETHER -> Dimension.NETHER; case THE_END -> Dimension.END; default -> Dimension.OVERWORLD; };
    }
}
