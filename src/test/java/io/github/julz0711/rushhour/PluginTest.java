package io.github.julz0711.rushhour;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
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
        var backup = new PlayerBackup(plugin);
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
        var p = server.addPlayer();
        var survivalLocation = p.getLocation().add(10, 0, 0);
        p.teleport(survivalLocation);
        p.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 7));
        server.getScheduler().performTicks(2);
        assertEquals(survivalLocation, p.getLocation());
        assertEquals(GameMode.SURVIVAL, p.getGameMode());
        assertFalse(new PlayerBackup(plugin).has(p));
        var destination = survivalLocation.clone().add(1, 0, 0);
        var move = new PlayerMoveEvent(p, survivalLocation, destination);
        server.getPluginManager().callEvent(move);
        assertFalse(move.isCancelled());
        assertEquals(destination, move.getTo());
        var mine = new BlockBreakEvent(survivalLocation.getBlock(), p);
        server.getPluginManager().callEvent(mine);
        assertFalse(mine.isCancelled());

        plugin.getConfig().set("dimensions", List.of("OVERWORLD"));
        plugin.getConfig().set("countdown-seconds", 1);
        plugin.getConfig().set("world.spread-radius", 0);
        plugin.getConfig().set("catalog.whitelist", true);
        plugin.getConfig().set("catalog.overrides.ITEM:dirt", true);
        plugin.getConfig().set("catalog.overrides.ITEM:oak_log", true);
        plugin.game().start();
        assertEquals(GameManager.Phase.PREPARING, plugin.game().phase());
        server.getScheduler().performTicks(2);
        assertEquals(GameManager.Phase.COUNTDOWN, plugin.game().phase());
        assertEquals(GameMode.SURVIVAL, p.getGameMode());
        assertFalse(new PlayerBackup(plugin).has(p));
        p.getInventory().setItem(1, new ItemStack(Material.IRON_INGOT, 5));
        server.getScheduler().performTicks(22);
        assertEquals(GameManager.Phase.PLAYING, plugin.game().phase());
        var entry = plugin.game().entry(p).orElseThrow();
        p.getInventory().addItem(new ItemStack(Material.matchMaterial(entry.targets.getFirst().key())));
        plugin.game().scan(p); assertEquals(1, entry.score);
        plugin.game().pause(); assertThrows(IllegalStateException.class, () -> plugin.game().skip(p, 0));
        assertThrows(IllegalStateException.class, () -> plugin.game().reloadSettings());
        plugin.game().resume(); plugin.game().stop(); assertEquals(GameManager.Phase.RECAP, plugin.game().phase());
        assertEquals(server.getWorld("world").getSpawnLocation(), p.getLocation());
        plugin.game().recap("skip"); assertEquals(GameManager.Phase.LOBBY, plugin.game().phase());
        assertEquals(7, p.getInventory().getItem(0).getAmount()); assertEquals(Material.DIAMOND, p.getInventory().getItem(0).getType());
        assertEquals(new ItemStack(Material.IRON_INGOT, 5), p.getInventory().getItem(1));
        assertEquals(survivalLocation, p.getLocation());
        assertTrue(p.getInventory().contains(Material.WRITTEN_BOOK));
    }
    @Test void hostPermissionControlsSetupAndCatalogMenusDoNotTransferItems() {
        var p = server.addPlayer(); server.getScheduler().performTicks(2);
        server.dispatchCommand(p, "rush setup");
        assertTrue(p.getOpenInventory().getTopInventory() == null || !(p.getOpenInventory().getTopInventory().getHolder() instanceof Menus.Menu));
        // MockBukkit lacks Paper's dynamic dialog provider. The catalog still exercises the inventory path.
        p.setOp(true); server.dispatchCommand(p, "rush catalog diamond");
        assertTrue(p.getOpenInventory().getTopInventory().getHolder() instanceof Menus.Menu);
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
    @Test void rightClickingSkipBarriersUpdatesBothPlayersAndCannotSupplyABarrierObjective() {
        var alice = server.addPlayer("Alice");
        var bob = server.addPlayer("Bob");
        server.getScheduler().performTicks(2);
        plugin.getConfig().set("mode", "TEAMS");
        plugin.getConfig().set("dimensions", List.of("OVERWORLD"));
        plugin.getConfig().set("countdown-seconds", 0);
        plugin.getConfig().set("world.spread-radius", 0);
        plugin.game().team(alice, 1);
        plugin.game().team(bob, 1);
        plugin.game().start();
        server.getScheduler().performTicks(4);
        var team = plugin.game().entry(alice).orElseThrow();
        var token = alice.getInventory().getItem(8);
        assertNotNull(token);
        assertEquals(Material.BARRIER, token.getType());
        assertEquals(3, token.getAmount());
        assertEquals(3, bob.getInventory().getItem(8).getAmount());

        team.targets.set(0, plugin.catalog().get("ITEM:barrier"));
        plugin.game().scan(alice);
        assertEquals(0, team.score);
        var use = new PlayerInteractEvent(alice, Action.RIGHT_CLICK_AIR, token, null, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(use);
        assertTrue(use.isCancelled());
        assertEquals(2, team.skips);
        assertEquals(2, alice.getInventory().getItem(8).getAmount());
        assertEquals(2, bob.getInventory().getItem(8).getAmount());
        assertNotEquals("ITEM:barrier", team.targets.getFirst().id());

        plugin.game().backpack(alice);
        var transfer = new InventoryClickEvent(alice.getOpenInventory(), InventoryType.SlotType.CONTAINER, 0,
                ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 8);
        server.getPluginManager().callEvent(transfer);
        assertTrue(transfer.isCancelled());

        plugin.game().skip(alice, 0);
        plugin.game().skip(bob, 0);
        assertNull(alice.getInventory().getItem(8));
        assertNull(bob.getInventory().getItem(8));
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
