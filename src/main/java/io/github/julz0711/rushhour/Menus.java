package io.github.julz0711.rushhour;

import io.github.julz0711.rushhour.game.*;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import static io.github.julz0711.rushhour.game.Objective.*;

public final class Menus implements Listener {
    public static final class Menu implements InventoryHolder {
        private final Inventory inventory;
        private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
        Menu(String title) { inventory = Bukkit.createInventory(this, 54, Component.text(title, NamedTextColor.GOLD)); }
        @Override public Inventory getInventory() { return inventory; }
        void button(int slot, Material material, Component name, List<Component> lore, Consumer<InventoryClickEvent> action) {
            ItemStack item = new ItemStack(material); var meta = item.getItemMeta();
            meta.displayName(name); meta.lore(lore); item.setItemMeta(meta); inventory.setItem(slot, item);
            if (action != null) actions.put(slot, action);
        }
        void button(int slot, Material material, String name, String lore, Consumer<InventoryClickEvent> action) {
            button(slot, material, Component.text(name, NamedTextColor.YELLOW), List.of(Component.text(lore, NamedTextColor.GRAY)), action);
        }
    }
    private record Pending(Consumer<String> action, long expires) { }
    private final RushHourPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> ENUMS = Map.of(
            "mode", List.of("SOLO", "TEAMS"), "game-type", List.of("CLASSIC", "RACE", "BINGO"),
            "rules.death-penalty", List.of("NONE", "POINT", "TARGET"), "late-join.mode", List.of("SPECTATE", "PLAY"));
    Menus(RushHourPlugin plugin) { this.plugin = plugin; }
    public void setup(Player p, int page) {
        hostLobby(p);
        Menu menu = new Menu("Rush Hour · Setup " + (page + 1));
        var keys = plugin.getConfig().getValues(true).entrySet().stream()
                .filter(e -> !(e.getValue() instanceof org.bukkit.configuration.ConfigurationSection))
                .map(Map.Entry::getKey).filter(k -> !k.startsWith("catalog.")).sorted().toList();
        for (int i = page * 45; i < Math.min(keys.size(), (page + 1) * 45); i++) {
            String key = keys.get(i); Object value = plugin.getConfig().get(key);
            menu.button(i % 45, value instanceof Boolean b ? (b ? Material.LIME_DYE : Material.GRAY_DYE) : Material.REPEATER,
                    key + ": " + value, "Click to edit · right: decrease · shift: bigger step", event -> {
                        hostLobby(p);
                        if (value instanceof Boolean b) { change(key, !b); setup(p, page); }
                        else if (ENUMS.containsKey(key)) {
                            var values = ENUMS.get(key); int index = values.indexOf(value.toString());
                            change(key, values.get((index + 1) % values.size())); setup(p, page);
                        } else if (value instanceof Number number) {
                            if (key.equals("late-join.catch-up-points")) {
                                double result = Math.max(0, number.doubleValue() + (event.isRightClick() ? -.5 : .5)); change(key, result);
                            } else {
                                int step = key.equals("deadline-seconds") ? 300 : key.equals("world.spread-radius") ? 16 : 1;
                                if (event.isShiftClick()) step *= 10;
                                long result = Math.max(0, number.longValue() + (event.isRightClick() ? -step : step));
                                change(key, result);
                            }
                            setup(p, page);
                        } else if (key.equals("dimensions")) dimensions(p);
                        else ask(p, "Enter " + key + " in chat (comma-separated for lists, 'empty' to clear).", text -> {
                            setFromText(key, text); setup(p, page);
                        });
                    });
        }
        menu.button(45, Material.ARROW, "Previous", "", e -> setup(p, Math.max(0, page - 1)));
        menu.button(46, Material.ARROW, "Next", "", e -> setup(p, Math.min((keys.size() - 1) / 45, page + 1)));
        menu.button(48, Material.CHEST, "Objective catalog", "Items, mobs, advancements, structures and biomes", e -> catalog(p, Type.ITEM, "", "", 0));
        menu.button(49, Material.COMPASS, "Dimensions", plugin.game().settings().dimensions.toString(), e -> dimensions(p));
        menu.button(50, Material.BOOK, "Catalog presets", "Save and load named catalogs", e -> presets(p));
        menu.button(53, Material.EMERALD_BLOCK, "Start round", "Start with the current settings", e -> { p.closeInventory(); plugin.game().start(); });
        p.openInventory(menu.inventory);
    }
    public void setFromText(String key, String text) {
        if (plugin.game().locked()) throw new IllegalStateException("Settings are locked during a round");
        if (!plugin.getConfig().contains(key) || plugin.getConfig().isConfigurationSection(key) || key.startsWith("catalog.overrides.") || key.startsWith("catalog.categories.")) throw new IllegalArgumentException("Unknown editable setting: " + key);
        Object old = plugin.getConfig().get(key); Object value;
        if (old instanceof Boolean) {
            if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) throw new IllegalArgumentException("Use true or false");
            value = Boolean.parseBoolean(text);
        } else if (old instanceof Double || old instanceof Float) value = Double.parseDouble(text);
        else if (old instanceof Number) value = Long.parseLong(text);
        else if (old instanceof List<?>) value = text.equalsIgnoreCase("empty") ? List.of() : Arrays.stream(text.split(",")).map(String::trim).toList();
        else value = text.equalsIgnoreCase("empty") ? "" : ENUMS.containsKey(key) ? text.toUpperCase(Locale.ROOT) : text;
        change(key, value);
    }
    private void change(String path, Object value) {
        Object old = plugin.getConfig().get(path);
        plugin.getConfig().set(path, value);
        try { plugin.game().reloadSettings(); plugin.saveConfig(); }
        catch (RuntimeException e) { plugin.getConfig().set(path, old); plugin.game().reloadSettings(); throw e; }
    }
    private void dimensions(Player p) {
        hostLobby(p); Menu menu = new Menu("Rush Hour · Dimensions"); int slot = 20;
        for (Dimension d : Dimension.values()) {
            boolean enabled = plugin.game().settings().dimensions.contains(d);
            menu.button(slot++, enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE, d.name(), enabled ? "Enabled" : "Disabled", e -> {
                hostLobby(p); List<String> values = new ArrayList<>(plugin.getConfig().getStringList("dimensions"));
                if (!values.remove(d.name())) values.add(d.name()); change("dimensions", values); dimensions(p);
            });
        }
        menu.button(49, Material.ARROW, "Setup", "", e -> setup(p, 0)); p.openInventory(menu.inventory);
    }
    public void catalog(Player p, Type type, String query, String category, int page) {
        hostLobby(p);
        var list = plugin.catalog().all().stream().filter(o -> o.type() == type)
                .filter(o -> query.isBlank() || o.key().contains(query.toLowerCase(Locale.ROOT).replace(' ', '_')))
                .filter(o -> category.isBlank() || o.category().equals(category)).toList();
        int safePage = Math.clamp(page, 0, Math.max(0, (list.size() - 1) / 36));
        Menu menu = new Menu("Catalog · " + type + " · " + (safePage + 1));
        for (int t = 0; t < Type.values().length; t++) {
            Type tab = Type.values()[t]; menu.button(t, icon(tab), tab.name(), type == tab ? "Current tab" : "Open tab", e -> catalog(p, tab, "", "", 0));
        }
        for (int i = safePage * 36; i < Math.min(list.size(), (safePage + 1) * 36); i++) {
            Objective o = list.get(i); boolean enabled = plugin.catalog().enabled(o);
            Material material = o.type() == Type.ITEM ? Material.matchMaterial(o.key()) : icon(o.type());
            if (material == null || !material.isItem() || material == Material.AIR) material = Material.PAPER;
            menu.button(9 + i % 36, material, GameManager.objectiveName(o).color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED), List.of(
                    Component.text((enabled ? "Enabled" : "Disabled") + " · " + o.tier(), NamedTextColor.YELLOW),
                    Component.text(o.category() + " · " + o.routes(), NamedTextColor.GRAY),
                    Component.text("Click: toggle · shift: toggle category", NamedTextColor.GRAY)), e -> {
                        hostLobby(p); if (e.isShiftClick()) plugin.catalog().toggleCategory(o.category()); else plugin.catalog().toggle(o);
                        catalog(p, type, query, category, safePage);
                    });
        }
        menu.button(45, Material.ARROW, "Previous", "", e -> catalog(p, type, query, category, safePage - 1));
        menu.button(46, Material.ARROW, "Next", "", e -> catalog(p, type, query, category, safePage + 1));
        menu.button(47, Material.OAK_SIGN, "Search", query, e -> ask(p, "Search objective name ('empty' clears).", text -> catalog(p, type, text.equalsIgnoreCase("empty") ? "" : text, category, 0)));
        menu.button(48, Material.HOPPER, "Category", category.isBlank() ? "All categories" : category, e -> {
            var categories = plugin.catalog().all().stream().filter(o -> o.type() == type).map(Objective::category).distinct().sorted().toList();
            List<String> cycle = new ArrayList<>(); cycle.add(""); cycle.addAll(categories);
            catalog(p, type, query, cycle.get((cycle.indexOf(category) + 1) % cycle.size()), 0);
        });
        menu.button(49, Material.BOOK, "Presets", "Save / load / whitelist", e -> presets(p));
        menu.button(50, Material.REPEATER, "Type weight: " + plugin.game().settings().typeWeights.get(type), "Configure target-weights in setup", e -> setup(p, 0));
        menu.button(53, Material.COMPARATOR, "Setup", "", e -> setup(p, 0)); p.openInventory(menu.inventory);
    }
    private void presets(Player p) {
        hostLobby(p); Menu menu = new Menu("Rush Hour · Catalog presets");
        menu.button(20, Material.WRITABLE_BOOK, "Save catalog", "Enter a preset name", e -> ask(p, "Preset name to save:", name -> { plugin.catalog().preset("save", name); presets(p); }));
        menu.button(22, Material.BOOK, "Load catalog", "easy · no-end · wood-age, or your saved name", e -> ask(p, "Preset name to load:", name -> { plugin.catalog().preset("load", name); presets(p); }));
        menu.button(24, Material.WHITE_DYE, "Whitelist: " + plugin.getConfig().getBoolean("catalog.whitelist"), "Only explicit item toggles enter the pool", e -> { hostLobby(p); change("catalog.whitelist", !plugin.getConfig().getBoolean("catalog.whitelist")); presets(p); });
        menu.button(49, Material.ARROW, "Catalog", "", e -> catalog(p, Type.ITEM, "", "", 0)); p.openInventory(menu.inventory);
    }
    public void teams(Player p) {
        Menu menu = new Menu("Rush Hour · Pick a team");
        for (int i = 1; i <= plugin.game().settings().teamCount; i++) {
            int number = i; menu.button(9 + i, Material.LEATHER_HELMET, "Team " + i, "Join this team", e -> { plugin.game().team(p, number); p.closeInventory(); });
        }
        menu.button(49, Material.BARRIER, "Leave team", "", e -> { plugin.game().team(p, 0); p.closeInventory(); }); p.openInventory(menu.inventory);
    }
    public void board(Player p) {
        var entry = plugin.game().entry(p).orElseThrow(() -> new IllegalStateException("You are not in this round"));
        Menu menu = new Menu("Rush Hour · Objectives");
        for (int i = 0; i < entry.targets.size(); i++) {
            int index = i; Objective o = entry.targets.get(i); boolean done = plugin.game().round().mode() == Round.Mode.BINGO && entry.completed.contains(o.id());
            int slot = entry.targets.size() == 25 ? 2 + i % 5 + (i / 5) * 9 : 22;
            menu.button(slot, done ? Material.LIME_STAINED_GLASS_PANE : o.type() == Type.ITEM ? Optional.ofNullable(Material.matchMaterial(o.key())).orElse(Material.PAPER) : icon(o.type()),
                    GameManager.target(o), List.of(Component.text(done ? "Completed" : "Click to skip · tokens: " + entry.skips, NamedTextColor.YELLOW)), e -> {
                        plugin.game().skip(p, index); board(p);
                    });
        }
        menu.button(49, Material.PAPER, "Skips: " + entry.skips, "Click an unfinished square to use one token", null); p.openInventory(menu.inventory);
    }
    public void spectators(Player p) {
        Menu menu = new Menu("Rush Hour · Follow a player"); int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!plugin.game().active(target) || slot >= 45) continue;
            menu.button(slot++, Material.PLAYER_HEAD, target.getName(), "Follow", e -> { p.closeInventory(); plugin.game().follow(p, target.getUniqueId()); });
        }
        p.openInventory(menu.inventory);
    }
    private void ask(Player p, String message, ConsumerWithException action) {
        p.closeInventory(); p.sendMessage(Component.text(message + " Type cancel to cancel (60 seconds).", NamedTextColor.YELLOW));
        pending.put(p.getUniqueId(), new Pending(text -> {
            hostLobby(p);
            try { action.accept(text); } catch (Exception e) { p.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)); }
        }, System.currentTimeMillis() + 60_000));
    }
    @FunctionalInterface private interface ConsumerWithException { void accept(String text) throws Exception; }
    @EventHandler(priority = EventPriority.LOWEST) public void chat(AsyncChatEvent event) {
        Pending input = pending.remove(event.getPlayer().getUniqueId()); if (input == null || input.expires < System.currentTimeMillis()) return;
        event.setCancelled(true); String value = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (value.equalsIgnoreCase("cancel")) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            try { input.action.accept(value); } catch (RuntimeException e) { event.getPlayer().sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)); }
        });
    }
    @EventHandler public void quit(PlayerQuitEvent event) { pending.remove(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.HIGHEST) public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || event.getRawSlot() < 0 || event.getRawSlot() >= 54) return;
        var action = menu.actions.get(event.getRawSlot()); if (action == null) return;
        // Open/close inventories after the click transaction has finished.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || p.getOpenInventory().getTopInventory() != menu.inventory) return;
            try { action.accept(event); } catch (RuntimeException e) { p.sendMessage(Component.text(e.getMessage() == null ? "Could not perform action" : e.getMessage(), NamedTextColor.RED)); }
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true); }
    private void hostLobby(Player p) {
        if (!p.hasPermission("rushhour.host")) throw new IllegalStateException("You need rushhour.host");
        if (plugin.game().locked()) throw new IllegalStateException("Settings and catalog are locked during a round");
    }
    private static Material icon(Type type) { return switch (type) { case ITEM -> Material.CHEST; case MOB -> Material.IRON_SWORD; case ADVANCEMENT -> Material.EXPERIENCE_BOTTLE; case STRUCTURE -> Material.BRICKS; case BIOME -> Material.GRASS_BLOCK; }; }
}
