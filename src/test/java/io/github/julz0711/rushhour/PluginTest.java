package io.github.julz0711.rushhour;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.julz0711.rushhour.game.Objective.*;

class PluginTest {
    private ServerMock server;
    private RushHourPlugin plugin;
    @BeforeEach void load() {
        // MockBukkit does not implement advancement enumeration yet; this fixture has no datapack advancements.
        server = MockBukkit.mock(new ServerMock() {
            @Override public Iterator<org.bukkit.advancement.Advancement> advancementIterator() { return Collections.emptyIterator(); }
            @Override public org.bukkit.advancement.Advancement getAdvancement(NamespacedKey key) { return null; }
        });
        server.addWorld(new org.mockbukkit.mockbukkit.world.WorldMock(new WorldCreator("world")) {
            @Override public java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsync(int x, int z, boolean generate, boolean urgent) {
                return java.util.concurrent.CompletableFuture.completedFuture(getChunkAt(x, z));
            }
        });
        plugin = MockBukkit.load(RushHourPlugin.class);
    }
    @AfterEach void unload() { MockBukkit.unmock(); }
    @Test void enablesWithDefaultConfigAndVersionedCatalog() {
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.catalog().get("ITEM:blaze_rod")); assertFalse(plugin.catalog().enabled(plugin.catalog().get("ITEM:bedrock")));
        assertFalse(plugin.catalog().enabled(plugin.catalog().get("ITEM:cow_spawn_egg")));
        assertFalse(plugin.catalog().enabled(plugin.catalog().get("ITEM:chorus_plant")));
        assertTrue(plugin.catalog().pool(plugin.game().settings()).entries().size() > 100);
    }
    @Test void overworldPoolExcludesNetherAndEndTargets() {
        plugin.getConfig().set("dimensions", List.of("OVERWORLD")); plugin.game().reloadSettings();
        var pool = plugin.catalog().pool(plugin.game().settings()).entries();
        assertTrue(pool.stream().anyMatch(o -> o.key().equals("oak_log")));
        assertFalse(pool.stream().anyMatch(o -> Set.of("blaze_rod", "elytra", "nether_star").contains(o.key())));
    }
    @Test void explicitOverrideCanEnableBlockedItemAndWhitelistRejectsDefaults() {
        var bedrock = plugin.catalog().get("ITEM:bedrock"); plugin.catalog().toggle(bedrock); assertTrue(plugin.catalog().enabled(bedrock));
        plugin.getConfig().set("catalog.whitelist", true); assertFalse(plugin.catalog().enabled(plugin.catalog().get("ITEM:dirt"))); assertTrue(plugin.catalog().enabled(bedrock));
    }
    @Test void backupRestoresInventoryAndLocationAndSurvivesNewManager() {
        var p = server.addPlayer(); p.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 7)); p.setLevel(12);
        var backup = new PlayerBackup(plugin); // Existing lobby recovery is removed before this independent round.
        backup.restore(p); p.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 7)); p.setLevel(12);
        p.getEnderChest().setItem(2, new ItemStack(Material.EMERALD, 5)); p.setItemOnCursor(new ItemStack(Material.GOLD_INGOT, 3)); backup.capture(p);
        p.getInventory().clear(); p.getEnderChest().clear(); p.setItemOnCursor(null); p.setLevel(0); assertTrue(new PlayerBackup(plugin).restore(p));
        assertEquals(7, p.getInventory().getItem(0).getAmount()); assertEquals(12, p.getLevel()); assertFalse(backup.has(p));
        assertEquals(5, p.getEnderChest().getItem(2).getAmount()); assertEquals(3, p.getItemOnCursor().getAmount());
    }
    @Test void rejectsInvalidSettingsAndMenuEditsRollBack() {
        assertThrows(IllegalArgumentException.class, () -> plugin.menus().setFromText("deadline-seconds", "1"));
        assertEquals(3600, plugin.getConfig().getLong("deadline-seconds"));
        assertThrows(IllegalArgumentException.class, () -> plugin.menus().setFromText("mode", "broken"));
        assertEquals("SOLO", plugin.getConfig().getString("mode"));
    }
    @Test void catalogPresetsAreBundledAndRejectPathTraversal() throws Exception {
        plugin.catalog().preset("load", "wood-age");
        assertTrue(plugin.getConfig().getBoolean("catalog.whitelist"));
        assertTrue(plugin.catalog().enabled(plugin.catalog().get("ITEM:oak_log")));
        assertFalse(plugin.catalog().enabled(plugin.catalog().get("ITEM:diamond")));
        assertThrows(IllegalArgumentException.class, () -> plugin.catalog().preset("save", "../escape"));
    }
    @Test void startsScoresPausesAndRestoresACompleteRound() {
        var p = server.addPlayer(); server.getScheduler().performTicks(2);
        // Restore the initial lobby snapshot before setting the pre-round survival inventory.
        new PlayerBackup(plugin).restore(p);
        p.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 7));
        plugin.getConfig().set("dimensions", List.of("OVERWORLD"));
        plugin.getConfig().set("countdown-seconds", 0);
        plugin.getConfig().set("world.spread-radius", 0);
        plugin.getConfig().set("catalog.whitelist", true);
        plugin.getConfig().set("catalog.overrides.ITEM:dirt", true);
        plugin.getConfig().set("catalog.overrides.ITEM:oak_log", true);
        plugin.game().start(); server.getScheduler().performTicks(4);
        assertEquals(GameManager.Phase.PLAYING, plugin.game().phase());
        var entry = plugin.game().entry(p).orElseThrow();
        p.getInventory().addItem(new ItemStack(Material.matchMaterial(entry.targets.getFirst().key())));
        plugin.game().scan(p); assertEquals(1, entry.score);
        plugin.game().pause(); assertThrows(IllegalStateException.class, () -> plugin.game().skip(p, 0));
        assertThrows(IllegalStateException.class, () -> plugin.game().reloadSettings());
        plugin.game().resume(); plugin.game().stop(); assertEquals(GameManager.Phase.RECAP, plugin.game().phase());
        plugin.game().recap("skip"); assertEquals(GameManager.Phase.LOBBY, plugin.game().phase());
        assertEquals(7, p.getInventory().getItem(0).getAmount()); assertEquals(Material.DIAMOND, p.getInventory().getItem(0).getType());
        assertTrue(p.getInventory().contains(Material.WRITTEN_BOOK));
    }
    @Test void hostPermissionControlsSetupAndCatalogMenusDoNotTransferItems() {
        var p = server.addPlayer(); server.getScheduler().performTicks(2);
        server.dispatchCommand(p, "rush setup");
        assertTrue(p.getOpenInventory().getTopInventory() == null || !(p.getOpenInventory().getTopInventory().getHolder() instanceof Menus.Menu));
        p.setOp(true); server.dispatchCommand(p, "rush setup");
        assertTrue(p.getOpenInventory().getTopInventory().getHolder() instanceof Menus.Menu);
        plugin.menus().catalog(p, Type.ITEM, "diamond", "", 0);
        var view = p.getOpenInventory();
        var event = new org.bukkit.event.inventory.InventoryClickEvent(view, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 9,
                org.bukkit.event.inventory.ClickType.SHIFT_LEFT, org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(event); assertTrue(event.isCancelled());
    }
    @Test void hiddenHudContainsNeitherScoresNorOtherPlayersTargets() {
        var a = new TabPlayer(server, "Alice"); server.addPlayer(a); var b = server.addPlayer("Bob"); server.getScheduler().performTicks(2);
        plugin.getConfig().set("dimensions", List.of("OVERWORLD")); plugin.getConfig().set("countdown-seconds", 0);
        plugin.getConfig().set("world.spread-radius", 0); plugin.getConfig().set("hidden-leaderboard", true);
        plugin.game().start(); server.getScheduler().performTicks(4);
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertTrue(plain.serialize(b.playerListName()).contains("?"));
        String footer = plain.serialize(a.footer);
        assertTrue(footer.contains("Bob · ? · Hidden"), footer);
        assertThrows(IllegalStateException.class, () -> plugin.game().top(a));
    }
    @Test void teamsShareBackpackAndAllRoundSettingsAreLocked() {
        var a = server.addPlayer("Alice"); var b = server.addPlayer("Bob"); server.getScheduler().performTicks(2);
        plugin.getConfig().set("mode", "TEAMS"); plugin.getConfig().set("dimensions", List.of("OVERWORLD"));
        plugin.getConfig().set("countdown-seconds", 0); plugin.getConfig().set("world.spread-radius", 0); plugin.game().reloadSettings();
        plugin.game().team(a, 1); plugin.game().team(b, 1); plugin.game().start(); server.getScheduler().performTicks(4);
        assertSame(plugin.game().entry(a).orElseThrow(), plugin.game().entry(b).orElseThrow());
        plugin.game().backpack(a); var inventory = a.getOpenInventory().getTopInventory(); inventory.setItem(0, new ItemStack(Material.IRON_INGOT));
        plugin.game().backpack(b); assertSame(inventory, b.getOpenInventory().getTopInventory());
        plugin.game().pause(); assertFalse(plugin.game().canBackpack(a, inventory));
        assertThrows(IllegalStateException.class, () -> plugin.menus().setFromText("chaos", "true"));
        assertThrows(IllegalStateException.class, () -> plugin.game().team(a, 2));
    }
    @Test void stopDuringPreparationWaitsForChunkThenRestoresPlayers() {
        var p = server.addPlayer(); server.getScheduler().performTicks(2);
        plugin.getConfig().set("dimensions", List.of("OVERWORLD"));
        plugin.game().start(); assertEquals(GameManager.Phase.PREPARING, plugin.game().phase());
        plugin.game().stop(); server.getScheduler().performTicks(2);
        assertEquals(GameManager.Phase.LOBBY, plugin.game().phase()); assertNull(plugin.game().round());
        assertFalse(new PlayerBackup(plugin).has(p));
    }
    private static final class TabPlayer extends org.mockbukkit.mockbukkit.entity.PlayerMock {
        net.kyori.adventure.text.Component footer = net.kyori.adventure.text.Component.empty();
        TabPlayer(ServerMock server, String name) { super(server, name); }
        @Override public void sendPlayerListHeaderAndFooter(net.kyori.adventure.text.Component header, net.kyori.adventure.text.Component footer) { this.footer = footer; }
    }
}
