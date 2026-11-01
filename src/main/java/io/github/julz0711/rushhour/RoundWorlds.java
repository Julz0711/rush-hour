package io.github.julz0711.rushhour;

import org.bukkit.*;
import org.bukkit.block.Block;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static io.github.julz0711.rushhour.game.Objective.Dimension;

final class RoundWorlds {
    private final RushHourPlugin plugin;
    private final Map<Dimension, World> worlds = new EnumMap<>(Dimension.class);
    private final List<World> owned = new ArrayList<>();
    private final Map<World, Boolean> oldPvp = new HashMap<>();
    private final Map<World, Boolean> oldKeep = new HashMap<>();
    private volatile boolean preparationCancelled;
    private CompletableFuture<Void> preparation = CompletableFuture.completedFuture(null);
    RoundWorlds(RushHourPlugin plugin) { this.plugin = plugin; }
    World lobby(Settings s) {
        World world = s.lobby.isBlank() ? Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NORMAL).findFirst().orElse(Bukkit.getWorlds().getFirst()) : Bukkit.getWorld(s.lobby);
        if (world == null) throw new IllegalArgumentException("Lobby world is not loaded: " + s.lobby);
        return world;
    }
    CompletableFuture<Void> prepare(Settings s) {
        preparationCancelled = false;
        worlds.clear(); owned.clear();
        long seed = s.seed.isBlank() ? new Random().nextLong() : parseSeed(s.seed);
        String base = "rushhour_" + UUID.randomUUID().toString().replace("-", "");
        for (Dimension d : s.dimensions) {
            World.Environment env = environment(d);
            World w;
            if (s.fresh) {
                w = Bukkit.createWorld(new WorldCreator(base + "_" + d.name().toLowerCase(Locale.ROOT)).environment(env).seed(seed));
                if (w == null) throw new IllegalStateException("World creation failed for " + d);
                owned.add(w);
                try { Files.writeString(w.getWorldFolder().toPath().resolve(".rushhour-owned"), "RushHour temporary round world\n"); }
                catch (IOException e) { throw new IllegalStateException("Cannot mark temporary world", e); }
            } else {
                w = Bukkit.getWorlds().stream().filter(world -> world.getEnvironment() == env).findFirst().orElse(null);
                if (w == null) throw new IllegalStateException("No loaded " + d + " world. Enable it in server.properties or use fresh worlds.");
            }
            worlds.put(d, w);
        }
        // Bounded sequential async pre-generation avoids filling the chunk task queue.
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (World w : worlds.values()) {
            int cx = w.getSpawnLocation().getBlockX() >> 4, cz = w.getSpawnLocation().getBlockZ() >> 4;
            int radius = s.fresh ? s.pregen : 0;
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                int chunkX = cx + x, chunkZ = cz + z;
                future = future.thenCompose(v -> preparationCancelled
                        ? CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("Preparation cancelled"))
                        : w.getChunkAtAsync(chunkX, chunkZ, true).thenApply(c -> null));
            }
        }
        preparation = future;
        return preparation;
    }
    void cancelPreparation() { preparationCancelled = true; }
    void applyRules(Settings s) {
        for (World w : worlds.values()) {
            oldPvp.put(w, w.getPVP()); oldKeep.put(w, w.getGameRuleValue(GameRules.KEEP_INVENTORY));
            w.setPVP(s.pvp); w.setGameRule(GameRules.KEEP_INVENTORY, s.keepInventory);
        }
    }
    World first(Settings s) { return worlds.get(s.dimensions.iterator().next()); }
    World get(Dimension dimension) { return worlds.get(dimension); }
    boolean contains(World world) { return worlds.containsValue(world); }
    Location spawn(World w, int radius) {
        Random r = new Random(); Location center = w.getSpawnLocation();
        for (int attempt = 0; attempt < 80; attempt++) {
            int x = center.getBlockX() + (radius == 0 ? 0 : r.nextInt(-radius, radius + 1));
            int z = center.getBlockZ() + (radius == 0 ? 0 : r.nextInt(-radius, radius + 1));
            int top = w.getEnvironment() == World.Environment.NETHER ? 110 : w.getHighestBlockYAt(x, z);
            int bottom = w.getEnvironment() == World.Environment.NETHER ? 32 : Math.max(w.getMinHeight(), top - 4);
            for (int y = top; y >= bottom; y--) {
                Block floor = w.getBlockAt(x, y, z);
                if (floor.getType().isSolid() && !Set.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE).contains(floor.getType())
                        && w.getBlockAt(x, y + 1, z).isEmpty() && w.getBlockAt(x, y + 2, z).isEmpty())
                    return new Location(w, x + .5, y + 1, z + .5);
            }
        }
        // Last resort is a small safe landing platform, never a teleport into lava/void.
        int x = center.getBlockX(), z = center.getBlockZ(), y = w.getEnvironment() == World.Environment.NETHER ? 80 : Math.max(70, center.getBlockY());
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            w.getBlockAt(x + dx, y - 1, z + dz).setType(Material.OBSIDIAN);
            for (int dy = 0; dy <= 2; dy++) w.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
        }
        return new Location(w, x + .5, y, z + .5);
    }
    void cleanup() {
        if (!preparation.isDone()) {
            preparationCancelled = true;
            plugin.getLogger().warning("Chunk preparation is still stopping; temporary worlds are retained for server shutdown.");
            return;
        }
        oldPvp.forEach(World::setPVP); oldKeep.forEach((w, keep) -> w.setGameRule(GameRules.KEEP_INVENTORY, keep));
        oldPvp.clear(); oldKeep.clear(); worlds.clear();
        for (World w : new ArrayList<>(owned)) {
            if (!w.getPlayers().isEmpty() || !Bukkit.unloadWorld(w, false)) { plugin.getLogger().warning("Keeping occupied world " + w.getName()); continue; }
            Path directory = w.getWorldFolder().toPath().toAbsolutePath().normalize();
            deleteOwned(directory);
        }
        owned.clear();
    }
    private void deleteOwned(Path directory) {
        try {
            Path container = Bukkit.getWorldContainer().toPath().toRealPath();
            Path real = directory.toRealPath();
            if (!real.startsWith(container) || real.equals(container) || !real.getFileName().toString().matches("rushhour_[a-f0-9]{32}_(overworld|nether|end)")
                    || !Files.isRegularFile(real.resolve(".rushhour-owned"), LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) return;
            Files.walkFileTree(real, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException { if (error != null) throw error; Files.delete(dir); return FileVisitResult.CONTINUE; }
            });
        } catch (IOException e) { plugin.getLogger().warning("Could not clean up " + directory + ": " + e.getMessage()); }
    }
    private static long parseSeed(String seed) { try { return Long.parseLong(seed); } catch (NumberFormatException e) { return seed.hashCode(); } }
    private static World.Environment environment(Dimension d) { return switch (d) { case NETHER -> World.Environment.NETHER; case END -> World.Environment.THE_END; default -> World.Environment.NORMAL; }; }
}
