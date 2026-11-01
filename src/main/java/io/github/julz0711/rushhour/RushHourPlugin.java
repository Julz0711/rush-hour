package io.github.julz0711.rushhour;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public class RushHourPlugin extends JavaPlugin {
    private GameManager game;
    private Catalog catalog;
    private Menus menus;
    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            new Settings(getConfig());
            catalog = new Catalog(this);
            game = new GameManager(this);
            menus = new Menus(this);
            getServer().getPluginManager().registerEvents(game, this);
            getServer().getPluginManager().registerEvents(menus, this);
            getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> RushCommands.register(this, event.registrar()));
            game.enable();
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Rush Hour could not start; check configuration", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() { if (game != null) game.shutdown(); }
    public void saveResourceIfAbsent(String name) {
        if (!new java.io.File(getDataFolder(), name).isFile()) saveResource(name, false);
    }
    public GameManager game() { return game; }
    public Catalog catalog() { return catalog; }
    public Menus menus() { return menus; }
}
