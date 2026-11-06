package io.github.julz0711.rushhour;

import io.github.julz0711.rushhour.game.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;
import java.time.Duration;
import java.util.*;
import static io.github.julz0711.rushhour.game.Objective.*;

public final class GameManager implements Listener {
    public enum Phase { LOBBY, PREPARING, COUNTDOWN, PLAYING, RECAP }
    private final RushHourPlugin plugin;
    private final PlayerBackup backups;
    private final RoundWorlds worlds;
    private final SkipTokens skipTokens;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> teams = new HashMap<>();
    private final Map<String, Inventory> backpacks = new HashMap<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Set<UUID> managed = new HashSet<>();
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Random random = new Random();
    private Phase phase = Phase.LOBBY;
    private Settings settings;
    private Round round;
    private BukkitTask ticker, countdownTask, recapTask;
    private long lastNano;
    private long generation;
    private int countdown, roundNumber;
    private List<Round.Entry> revealOrder = List.of();
    private int revealEntry, revealFind, revealScore, revealDelay;
    private boolean recapFast;
    private boolean cancellingPreparation;
    private List<Material> chaosMaterials = List.of();
    private final List<Entity> podium = new ArrayList<>();

    GameManager(RushHourPlugin plugin) {
        this.plugin = plugin;
        backups = new PlayerBackup(plugin);
        worlds = new RoundWorlds(plugin);
        skipTokens = new SkipTokens(plugin);
    }
    void enable() {
        settings = new Settings(plugin.getConfig());
        for (Player p : Bukkit.getOnlinePlayers()) backups.restore(p);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }
    public Phase phase() { return phase; }
    public Round round() { return round; }
    public Settings settings() { return settings; }
    public boolean locked() { return phase != Phase.LOBBY; }
    public void reloadSettings() { if (locked()) throw new IllegalStateException("Settings are locked until the round ends"); settings = new Settings(plugin.getConfig()); }
    record StartPlan(ObjectivePool pool, List<Material> chaosItems, int playerCount, String problem) { }

    StartPlan startPlan() {
        var pool = plugin.catalog().pool(settings);
        var players = eligiblePlayers();
        var chaosItems = !settings.chaos ? List.<Material>of() : plugin.catalog().all().stream().filter(o -> o.type() == Type.ITEM && plugin.catalog().enabled(o)
                && o.available(settings.dimensions) && settings.tierWeights.get(o.tier()) > 0)
                .map(o -> Material.matchMaterial(o.key())).filter(Objects::nonNull).toList();
        String problem = null;
        try {
            validateParticipants(players);
            if (pool.entries().isEmpty()) throw new IllegalStateException("No objectives match the catalog, worlds and weights.");
            if (settings.gameType == Round.Mode.BINGO && pool.entries().size() < 25) throw new IllegalStateException("Bingo needs at least 25 available objectives.");
            if (settings.chaos && chaosItems.isEmpty()) throw new IllegalStateException("Chaos needs at least one enabled item in the catalog.");
            validateKit();
            worlds.validate(settings);
        } catch (IllegalArgumentException | IllegalStateException e) {
            problem = e.getMessage();
        }
        return new StartPlan(pool, chaosItems, players.size(), problem);
    }

    private List<? extends Player> eligiblePlayers() {
        return Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("rushhour.play")).toList();
    }

    private void validateParticipants(List<? extends Player> players) {
        if (players.isEmpty()) throw new IllegalStateException("At least one player with rushhour.play must be online.");
        for (Player player : players) {
            if (player.isDead()) throw new IllegalStateException("Wait for " + player.getName() + " to respawn before starting.");
        }
        if (!settings.teams) return;
        if (players.size() > settings.teamCount * settings.teamLimit) {
            throw new IllegalStateException("Increase the team count or team size to fit the online players.");
        }
        int[] sizes = new int[settings.teamCount + 1];
        for (Player player : players) {
            int team = teams.getOrDefault(player.getUniqueId(), 0);
            if (team > 0 && team <= settings.teamCount && ++sizes[team] > settings.teamLimit) {
                throw new IllegalStateException("Team " + team + " is over capacity. Increase its size or run /rush balance.");
            }
        }
    }

    public void start() {
        if (locked()) throw new IllegalStateException("A round is already active");
        reloadSettings();
        StartPlan plan = startPlan();
        if (plan.problem() != null) throw new IllegalStateException(plan.problem());
        if (plan.pool().entries().size() < 30) broadcast("Small objective pool: " + plan.pool().entries().size() + " targets. Targets repeat after a full cycle.");
        phase = Phase.PREPARING; cancellingPreparation = false; long token = ++generation;
        broadcast("Preparing round worlds and spawn chunks…");
        try {
            worlds.prepare(settings).whenComplete((v, error) -> {
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                if (generation != token || phase != Phase.PREPARING) return;
                if (cancellingPreparation) { abort("Round cancelled"); return; }
                if (error != null) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "World preparation failed", error); abort("World preparation failed; players restored."); return; }
                phase = Phase.COUNTDOWN; countdown = settings.countdown;
                countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (countdown <= 0) { countdownTask.cancel(); countdownTask = null; begin(plan); return; }
                    broadcast("Starting in " + countdown-- + "…");
                }, 0, 20);
                });
            });
        } catch (RuntimeException e) { abort("Round setup failed: " + e.getMessage()); throw e; }
    }
    private void begin(StartPlan plan) {
        try {
            var players = eligiblePlayers();
            validateParticipants(players);
            if (settings.teams) balance(players);
            round = new Round(plan.pool(), settings.gameType, settings.deadline, settings.points, settings.rowBonus);
            chaosMaterials = plan.chaosItems();
            spectators.clear();
            backpacks.clear();
            // Snapshot only when the round begins, so survival progress during preparation is kept.
            for (Player player : players) {
                backups.capture(player);
                managed.add(player.getUniqueId());
            }
            Map<String, Round.Entry> entries = new LinkedHashMap<>();
            for (Player player : players) {
                String id = settings.teams ? "team-" + teams.get(player.getUniqueId()) : player.getUniqueId().toString();
                Round.Entry entry = entries.computeIfAbsent(id, key -> {
                    String name = settings.teams ? "Team " + teams.get(player.getUniqueId()) : player.getName();
                    var created = new Round.Entry(key, name, settings.skips);
                    round.add(created);
                    return created;
                });
                entry.members.add(player.getUniqueId());
            }
            worlds.applyRules(settings); roundNumber++;
            for (Round.Entry entry : round.entries()) {
                Location spawn = worlds.spawn(worlds.first(settings), settings.spread);
                for (UUID id : entry.members) {
                    Player p = Bukkit.getPlayer(id); if (p == null) continue;
                    p.closeInventory();
                    p.closeDialog();
                    p.getInventory().clear(); p.getEnderChest().clear(); p.setItemOnCursor(null); p.setLevel(0); p.setExp(0); p.setTotalExperience(0);
                    p.teleport(spawn); p.setGameMode(GameMode.SURVIVAL);
                    p.setRespawnLocation(spawn, true);
                    p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
                    p.setHealth(p.getMaxHealth()); p.setFoodLevel(20); p.setSaturation(10); p.setFireTicks(0); p.setFallDistance(0);
                    if (settings.typeWeights.get(Type.ADVANCEMENT) > 0) backups.resetAdvancements(p);
                    syncSkipTokens(p, entry);
                    giveKit(p); lastActivity.put(id, System.currentTimeMillis());
                    p.showTitle(Title.title(Component.text("RUSH HOUR", NamedTextColor.GOLD), Component.text("Go!")));
                }
            }
            phase = Phase.PLAYING; lastNano = System.nanoTime();
            String tip = "";
            if (settings.skips > 0) {
                tip = round.mode() == Round.Mode.BINGO
                        ? " Right-click a Skip Token to pick a square."
                        : " Right-click a Skip Token to reroll.";
            }
            broadcast("Round " + roundNumber + " started!" + tip + " /rush board · /rush top");
            updateHud();
        } catch (RuntimeException e) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Round start failed", e); abort("Could not start; original player state restored."); }
    }
    private void validateKit() {
        for (String item : settings.kit) kitStack(item);
    }
    private ItemStack kitStack(String specification) {
        String[] parts = specification.split(":");
        Material m = Material.matchMaterial(parts[0]);
        int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        if (m == null || !m.isItem() || m.isAir() || amount < 1 || amount > m.getMaxStackSize()) throw new IllegalArgumentException("Invalid starter kit entry: " + specification);
        return new ItemStack(m, amount);
    }
    private void giveKit(Player p) { settings.kit.forEach(s -> p.getInventory().addItem(kitStack(s))); }
    public void team(Player p, int number) {
        if (locked()) throw new IllegalStateException("Teams are locked during a round");
        if (number == 0) { teams.remove(p.getUniqueId()); return; }
        if (number < 1 || number > settings.teamCount) throw new IllegalArgumentException("Team must be 1.." + settings.teamCount);
        long size = teams.entrySet().stream().filter(e -> e.getValue() == number && !e.getKey().equals(p.getUniqueId()) && Bukkit.getPlayer(e.getKey()) != null).count();
        if (size >= settings.teamLimit) throw new IllegalStateException("That team is full");
        teams.put(p.getUniqueId(), number); p.sendMessage(text("Joined Team " + number));
    }
    public void balance() { if (locked()) throw new IllegalStateException("Teams are locked"); teams.clear(); balance(new ArrayList<>(Bukkit.getOnlinePlayers())); }
    private void balance(List<? extends Player> players) {
        Set<UUID> online = new HashSet<>(); players.forEach(p -> online.add(p.getUniqueId()));
        teams.entrySet().removeIf(e -> !online.contains(e.getKey()) || e.getValue() > settings.teamCount);
        for (Player p : players) {
            if (teams.containsKey(p.getUniqueId())) continue;
            int team = 1; long minimum = Long.MAX_VALUE;
            for (int t = 1; t <= settings.teamCount; t++) {
                final int value = t; long count = teams.values().stream().filter(i -> i == value).count();
                if (count < minimum) { minimum = count; team = t; }
            }
            if (minimum >= settings.teamLimit) throw new IllegalStateException("Teams are full");
            teams.put(p.getUniqueId(), team);
        }
        for (int t = 1; t <= settings.teamCount; t++) {
            final int value = t;
            if (teams.values().stream().filter(i -> i == value).count() > settings.teamLimit) throw new IllegalStateException("Team " + t + " exceeds the size limit");
        }
    }
    public Optional<Round.Entry> entry(Player p) { return round == null ? Optional.empty() : round.entry(p.getUniqueId()); }
    public boolean active(Player p) {
        return phase == Phase.PLAYING && round.state() == Round.State.RUNNING && !spectators.contains(p.getUniqueId())
                && p.getGameMode() == GameMode.SURVIVAL && worlds.contains(p.getWorld()) && entry(p).isPresent();
    }
    private void clock() {
        if (phase != Phase.PLAYING || round.state() != Round.State.RUNNING) return;
        long now = System.nanoTime(), seconds = Math.max(0, (now - lastNano) / 1_000_000_000L);
        if (seconds > 0) { round.advance(seconds); lastNano += seconds * 1_000_000_000L; }
        if (round.state() == Round.State.FINISHED) end();
    }
    private void tick() {
        clock();
        if (phase != Phase.PLAYING) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!active(p)) continue;
            if (settings.idleSeconds > 0 && System.currentTimeMillis() - lastActivity.getOrDefault(p.getUniqueId(), System.currentTimeMillis()) >= settings.idleSeconds * 1000L) { spectate(p); continue; }
            var e = entry(p).orElseThrow();
            syncSkipTokens(p, e);
            scan(p); if (!active(p)) continue;
            var targets = List.copyOf(e.targets);
            String biome = p.getLocation().getBlock().getBiome().getKey().getKey();
            for (Objective o : targets) {
                if (o.type() == Type.BIOME && o.key().equals(biome)) complete(p, o.id());
                if (o.type() == Type.STRUCTURE) {
                    for (var structure : p.getWorld().getStructures(p.getLocation().getBlockX() >> 4, p.getLocation().getBlockZ() >> 4)) {
                        var key = Registry.STRUCTURE.getKey(structure.getStructure());
                        if (key != null && key.getKey().equals(o.key()) && structure.getBoundingBox().contains(p.getLocation().toVector())) { complete(p, o.id()); break; }
                    }
                }
            }
        }
        if (phase == Phase.PLAYING) updateHud();
    }
    public void scan(Player p) {
        clock(); if (!active(p)) return;
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (ItemStack item : p.getInventory().getStorageContents()) {
            if (item != null && !skipTokens.isToken(item)) materials.add(item.getType());
        }
        // Snapshot prevents a single inventory scan from chasing newly rolled objectives.
        for (Objective o : List.copyOf(entry(p).orElseThrow().targets)) {
            if (o.type() != Type.ITEM) continue;
            Material m = Material.matchMaterial(o.key());
            if (materials.contains(m)) complete(p, o.id());
        }
    }
    private void scanNext(Player p) { Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) scan(p); }); }
    private void complete(Player p, String id) {
        clock(); if (!active(p)) return;
        Round.Entry e = entry(p).orElseThrow();
        if (!round.complete(e, id)) return;
        Objective o = plugin.catalog().get(id);
        for (UUID member : e.members) {
            Player teammate = Bukkit.getPlayer(member); if (teammate == null) continue;
            teammate.playSound(teammate.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.5f);
            teammate.sendMessage(Component.text("✓ ", NamedTextColor.GREEN).append(objectiveName(o)).append(Component.text(" · " + (settings.hidden ? "score hidden" : e.score + " points"), NamedTextColor.GOLD)));
        }
        if (round.state() == Round.State.FINISHED) end(); else updateHud();
    }
    public void skip(Player p, int slot) {
        clock(); if (!active(p)) throw new IllegalStateException("You must be playing an unpaused round");
        Round.Entry entry = entry(p).orElseThrow();
        Objective old = round.skip(entry, slot);
        if (old == null) throw new IllegalStateException("No skips left, completed square, or no replacement target available");
        for (UUID member : entry.members) {
            Player teammate = Bukkit.getPlayer(member);
            if (teammate != null && !spectators.contains(member) && !teammate.isDead()) syncSkipTokens(teammate, entry);
        }
        if (settings.giveSkipped && old.type() == Type.ITEM) {
            Material material = Material.matchMaterial(old.key());
            if (material != null) p.getInventory().addItem(new ItemStack(material)).values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
        }
        p.sendMessage(text("Target skipped · " + entry.skips + " tokens left")); updateHud();
    }
    private void syncSkipTokens(Player player, Round.Entry entry) {
        skipTokens.sync(player, entry.skips, round.mode() == Round.Mode.BINGO, settings.teams);
    }
    public void pause() { clock(); if (phase != Phase.PLAYING) throw new IllegalStateException("No active round"); round.pause(); broadcast("Round paused"); updateHud(); }
    public void resume() { if (phase != Phase.PLAYING) throw new IllegalStateException("No active round"); round.resume(); lastNano = System.nanoTime(); broadcast("Round resumed"); updateHud(); }
    public void addTime(int seconds) { clock(); if (phase != Phase.PLAYING) throw new IllegalStateException("No active round"); round.addTime(seconds); broadcast("Added " + seconds + " seconds"); updateHud(); }
    public void stop() {
        if (phase == Phase.LOBBY) throw new IllegalStateException("No round to stop");
        if (phase == Phase.PREPARING) {
            cancellingPreparation = true; worlds.cancelPreparation();
            broadcast("Cancelling preparation; waiting for the current chunk to finish…");
        } else if (phase == Phase.COUNTDOWN) abort("Round cancelled");
        else if (phase == Phase.RECAP) finishRecap(); else end();
    }
    public void backpack(Player p) {
        if (!active(p) || !settings.teams) throw new IllegalStateException("The backpack is available during team rounds");
        Round.Entry e = entry(p).orElseThrow();
        p.openInventory(backpacks.computeIfAbsent(e.id, k -> Bukkit.createInventory(null, 54, Component.text(e.name + " backpack"))));
    }
    public boolean isBackpack(Inventory inventory) { return backpacks.containsValue(inventory); }
    public boolean canBackpack(Player p, Inventory inventory) { return active(p) && settings.teams && entry(p).map(e -> backpacks.get(e.id) == inventory).orElse(false); }
    public void top(Player p) {
        if (round == null) throw new IllegalStateException("No round yet");
        if (settings.hidden && phase != Phase.LOBBY) throw new IllegalStateException("Standings will be revealed at the end");
        int place = 1; for (Round.Entry e : round.standings()) p.sendMessage(text(place++ + ". " + e.name + " · " + e.score));
    }
    private void spectate(Player p) {
        spectators.add(p.getUniqueId()); p.closeInventory(); p.getInventory().clear(); p.getInventory().addItem(new ItemStack(Material.COMPASS));
        p.setGameMode(GameMode.SPECTATOR); p.sendMessage(text("Spectating · /rush spectate opens the player selector"));
    }
    public void follow(Player p, UUID id) {
        if (!spectators.contains(p.getUniqueId())) throw new IllegalStateException("You are not spectating");
        Player target = Bukkit.getPlayer(id);
        if (target == null || !active(target)) throw new IllegalStateException("That player is not playing");
        p.teleport(target.getLocation()); p.setSpectatorTarget(target);
    }
    private void updateHud() {
        if (round == null) return;
        String time = round.state() == Round.State.PAUSED ? "PAUSED" : formatTime(round.remaining());
        for (Player listed : Bukkit.getOnlinePlayers()) {
            var e = entry(listed).orElse(null); if (e == null || !managed.contains(listed.getUniqueId())) continue;
            Component row = Component.text(listed.getName() + (settings.teams ? " [" + e.name + "]" : ""), NamedTextColor.WHITE);
            if (settings.hidden) row = row.append(Component.text(" · ?", NamedTextColor.GRAY));
            else row = row.append(Component.text(" · ")).append(settings.gameType == Round.Mode.BINGO ? Component.text(e.completed.size() + "/25") : objectiveName(e.targets.getFirst()))
                    .append(Component.text(" · " + e.score, NamedTextColor.GOLD));
            listed.playerListName(row);
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!managed.contains(viewer.getUniqueId())) continue;
            var mine = entry(viewer).orElse(null);
            Component target = mine == null ? Component.text("Spectating") : settings.gameType == Round.Mode.BINGO
                    ? Component.text("Bingo " + mine.completed.size() + "/25 · /rush board") : target(mine.targets.getFirst());
            BossBar bar = bars.computeIfAbsent(viewer.getUniqueId(), k -> {
                var b = BossBar.bossBar(Component.empty(), 1, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS); viewer.showBossBar(b); return b;
            });
            bar.name(target.append(Component.text(" · " + time, NamedTextColor.GOLD)));
            bar.progress(round.deadline() == 0 ? 1 : (float) Math.clamp((double) round.remaining() / round.deadline(), 0, 1));
            Component footer = Component.empty();
            // A viewer-specific footer provides private targets; the shared vanilla row name never leaks them.
            for (Round.Entry e : round.entries()) {
                String score = settings.hidden ? "?" : Integer.toString(e.score);
                footer = footer.append(Component.text("\n" + e.name + " · " + score + " · ", NamedTextColor.GRAY));
                footer = footer.append(!settings.hidden || e == mine ? target(e.targets.getFirst()) : Component.text("Hidden"));
            }
            viewer.sendPlayerListHeaderAndFooter(Component.text("RUSH HOUR · Round " + roundNumber + " · " + time, NamedTextColor.GOLD), footer);
        }
    }
    private void end() {
        if (phase != Phase.PLAYING) return;
        round.finish(); phase = Phase.RECAP; closeGameInventories();
        Location recapLocation = worlds.lobby(settings).getSpawnLocation();
        for (Player p : Bukkit.getOnlinePlayers()) if (managed.contains(p.getUniqueId())) {
            p.setGameMode(GameMode.ADVENTURE); p.teleport(recapLocation); p.setFireTicks(0); p.setFallDistance(0);
        }
        revealOrder = new ArrayList<>(round.standings()); Collections.reverse(revealOrder);
        revealEntry = 0; revealFind = -1; revealScore = 0; revealDelay = 0; recapFast = false;
        persistResults(); broadcast("Time! Revealing finds from last place to first…");
        recapTask = Bukkit.getScheduler().runTaskTimer(plugin, this::revealBeat, 0, 2);
    }
    public void recap(String action) {
        if (phase != Phase.RECAP) throw new IllegalStateException("No recap is playing");
        if (action.equals("skip")) finishRecap(); else if (action.equals("speed")) recapFast = true; else throw new IllegalArgumentException("Use recap speed|skip");
    }
    private void revealBeat() {
        if (revealDelay > 0) { revealDelay -= 2; return; }
        revealDelay = recapFast ? 2 : settings.beat;
        if (revealEntry >= revealOrder.size()) { finishRecap(); return; }
        Round.Entry e = revealOrder.get(revealEntry);
        int place = revealOrder.size() - revealEntry;
        if (revealFind == -1) {
            showAll(Component.text("#" + place + " · " + e.name, NamedTextColor.GOLD), Component.text("The finds…")); revealFind = 0; return;
        }
        if (revealFind < e.timeline.size()) {
            var find = e.timeline.get(revealFind++); revealScore += find.points();
            showAll(objectiveName(find.objective()), Component.text(e.name + " · " + formatTime(find.second()) + " · " + revealScore + " points"));
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1.1f);
            return;
        }
        showAll(Component.text("#" + place + " · " + e.name, NamedTextColor.GOLD), Component.text(e.score + " points"));
        broadcast("#" + place + " " + e.name + " · " + e.score + " points");
        if (place == 1) celebrate(e);
        revealEntry++; revealFind = -1; revealScore = 0; revealDelay *= 2;
    }
    private void celebrate(Round.Entry winner) {
        World lobby = worlds.lobby(settings); Location center = lobby.getSpawnLocation();
        // Display entities make a temporary podium without changing the lobby's blocks.
        int i = 0;
        for (Round.Entry e : round.standings().stream().limit(3).toList()) {
            Location base = center.clone().add((i - 1) * 2.0, 0, 3);
            int height = 3 - i++;
            for (int y = 0; y < height; y++) {
                var display = lobby.spawn(base.clone().add(0, y, 0), BlockDisplay.class);
                display.setBlock((height == 3 ? Material.GOLD_BLOCK : height == 2 ? Material.IRON_BLOCK : Material.COPPER_BLOCK).createBlockData()); podium.add(display);
            }
            var label = lobby.spawn(base.clone().add(.5, height + .5, .5), TextDisplay.class);
            label.text(Component.text(e.name + " · " + e.score)); label.setBillboard(Display.Billboard.CENTER); podium.add(label);
            for (UUID member : e.members) {
                Player player = Bukkit.getPlayer(member); if (player == null) continue;
                player.setAllowFlight(true); player.setFlying(true);
                player.teleport(base.clone().add(.5, height, .5)); player.setFallDistance(0);
            }
        }
        for (UUID id : winner.members) {
            Player p = Bukkit.getPlayer(id); if (p == null) continue;
            var firework = p.getWorld().spawn(p.getLocation(), Firework.class);
            var meta = firework.getFireworkMeta(); meta.setPower(1); meta.addEffect(FireworkEffect.builder().withColor(Color.YELLOW).with(FireworkEffect.Type.BALL_LARGE).build()); firework.setFireworkMeta(meta);
            firework.getPersistentDataContainer().set(new NamespacedKey(plugin, "celebration"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        }
    }
    private void finishRecap() {
        if (recapTask != null) { recapTask.cancel(); recapTask = null; }
        broadcast("Final standings:"); for (Round.Entry e : round.standings()) broadcast(e.name + " · " + e.score + " points");
        restorePlayers();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission("rushhour.play")) giveBooks(p);
        podium.forEach(Entity::remove); podium.clear(); worlds.cleanup(); phase = Phase.LOBBY;
    }
    private void persistResults() {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("round", roundNumber); yaml.set("duration-seconds", round.elapsed());
        for (Round.Entry e : round.standings()) {
            String path = "entries." + e.id; yaml.set(path + ".name", e.name); yaml.set(path + ".score", e.score);
            yaml.set(path + ".finds", e.timeline.stream().map(f -> f.second() + " | " + f.objective().id() + " | +" + f.points()).toList());
        }
        try { yaml.save(new File(plugin.getDataFolder(), "results/" + System.currentTimeMillis() + ".yml")); }
        catch (IOException e) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save round timeline", e); }
    }
    private void giveBooks(Player p) {
        List<Component> pages = new ArrayList<>();
        for (Round.Entry e : round.standings()) {
            StringBuilder page = new StringBuilder(e.name + " · " + e.score + " points\n\n"); int lines = 0;
            for (var find : e.timeline) {
                page.append(formatTime(find.second())).append(" ").append(find.objective().label()).append(" +").append(find.points()).append("\n");
                if (++lines == 9) { pages.add(Component.text(page.toString())); page = new StringBuilder(e.name + " continued\n\n"); lines = 0; }
            }
            if (page.length() > 0) pages.add(Component.text(page.toString()));
        }
        if (pages.isEmpty()) pages.add(Component.text("No finds this round."));
        for (int from = 0; from < pages.size(); from += 100) {
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK); BookMeta meta = (BookMeta) book.getItemMeta();
            meta.setTitle("Rush Hour · Round " + roundNumber); meta.setAuthor("Rush Hour");
            meta.setPages(pages.subList(from, Math.min(pages.size(), from + 100)).stream()
                    .map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize).toList()); book.setItemMeta(meta);
            p.getInventory().addItem(book).values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
        }
    }
    private void abort(String message) {
        generation++; if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        closeGameInventories(); restorePlayers(); worlds.cleanup(); round = null; phase = Phase.LOBBY; broadcast(message);
    }
    private void closeGameInventories() { for (Player p : Bukkit.getOnlinePlayers()) if (managed.contains(p.getUniqueId())) p.closeInventory(); backpacks.clear(); }
    private void restorePlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            var bar = bars.remove(p.getUniqueId()); if (bar != null) p.hideBossBar(bar);
            if (managed.contains(p.getUniqueId())) { p.closeInventory(); backups.restore(p); }
        }
        bars.clear(); managed.clear(); spectators.clear();
    }
    void shutdown() {
        worlds.cancelPreparation();
        generation++; if (ticker != null) ticker.cancel(); if (countdownTask != null) countdownTask.cancel(); if (recapTask != null) recapTask.cancel();
        phase = Phase.LOBBY; closeGameInventories(); restorePlayers(); podium.forEach(Entity::remove); podium.clear(); worlds.cleanup();
    }
    private boolean frozen(Player p) {
        return managed.contains(p.getUniqueId()) && !spectators.contains(p.getUniqueId())
                && (phase == Phase.RECAP || phase == Phase.PLAYING && round.state() == Round.State.PAUSED);
    }
    private boolean protectedPlayer(Player p) { return managed.contains(p.getUniqueId()) && (!active(p) || spectators.contains(p.getUniqueId())); }
    @EventHandler public void join(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (phase == Phase.LOBBY || phase == Phase.PREPARING || phase == Phase.COUNTDOWN) {
                backups.restore(p);
                return;
            }
            if (round != null && round.entry(p.getUniqueId()).isPresent()) {
                managed.add(p.getUniqueId());
                if (phase == Phase.PLAYING && !spectators.contains(p.getUniqueId())) {
                    p.setGameMode(GameMode.SURVIVAL);
                    if (!worlds.contains(p.getWorld())) p.teleport(worlds.spawn(worlds.first(settings), 0));
                    syncSkipTokens(p, round.entry(p.getUniqueId()).orElseThrow());
                } else if (spectators.contains(p.getUniqueId())) p.setGameMode(GameMode.SPECTATOR);
                updateHud(); return;
            }
            if (!p.hasPermission("rushhour.play")) return;
            backups.capture(p); managed.add(p.getUniqueId());
            if (phase == Phase.PLAYING && settings.lateJoin.equals("PLAY")) {
                Round.Entry e;
                if (settings.teams) {
                    e = round.entries().stream().filter(t -> t.members.size() < settings.teamLimit).min(Comparator.comparingInt(t -> t.members.size())).orElse(null);
                    if (e == null) { spectate(p); return; }
                } else {
                    e = new Round.Entry(p.getUniqueId().toString(), p.getName(), settings.skips);
                    e.score = Math.min(round.standings().getFirst().score, (int) (round.elapsed() / 60.0 * settings.catchUp)); round.add(e);
                }
                e.members.add(p.getUniqueId()); p.getInventory().clear(); p.getEnderChest().clear(); p.setItemOnCursor(null);
                syncSkipTokens(p, e);
                giveKit(p);
                if (settings.typeWeights.get(Type.ADVANCEMENT) > 0) backups.resetAdvancements(p);
                p.teleport(worlds.spawn(worlds.first(settings), settings.spread)); p.setGameMode(GameMode.SURVIVAL); lastActivity.put(p.getUniqueId(), System.currentTimeMillis());
            } else spectate(p);
            updateHud();
        });
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        var bar = bars.remove(event.getPlayer().getUniqueId()); if (bar != null) event.getPlayer().hideBossBar(bar);
        // Recovery files remain for offline participants and reconnects preserve their round inventory.
    }
    @EventHandler(ignoreCancelled = true) public void move(PlayerMoveEvent event) {
        if (event.hasChangedPosition()) {
            if (frozen(event.getPlayer())) { event.setTo(event.getFrom().setDirection(event.getTo().getDirection())); return; }
            lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && protectedPlayer(p)) event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent hit) {
            if (hit.getDamager() instanceof Player p && protectedPlayer(p)) event.setCancelled(true);
            if (hit.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p && protectedPlayer(p)) event.setCancelled(true);
        }
        if (event instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Firework fw
                && fw.getPersistentDataContainer().has(new NamespacedKey(plugin, "celebration"))) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void hunger(FoodLevelChangeEvent event) { if (event.getEntity() instanceof Player p && protectedPlayer(p)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void breakBlock(BlockBreakEvent event) { if (protectedPlayer(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void placeBlock(BlockPlaceEvent event) {
        if (protectedPlayer(event.getPlayer()) || skipTokens.isToken(event.getItemInHand())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH) public void useSkipToken(PlayerInteractEvent event) {
        // Paper can pre-cancel right-click air because vanilla has no action there.
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !skipTokens.isToken(event.getItem())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        try {
            clock();
            if (!active(player)) throw new IllegalStateException("You must be playing an unpaused round");
            if (round.mode() == Round.Mode.BINGO) plugin.menus().board(player);
            else skip(player, 0);
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
    }
    @EventHandler(ignoreCancelled = true) public void interact(PlayerInteractEvent event) {
        if (protectedPlayer(event.getPlayer())) event.setCancelled(true);
        if (spectators.contains(event.getPlayer().getUniqueId()) && event.getItem() != null && event.getItem().getType() == Material.COMPASS) plugin.menus().spectators(event.getPlayer());
    }
    @EventHandler(ignoreCancelled = true) public void drop(PlayerDropItemEvent event) {
        if (protectedPlayer(event.getPlayer()) || skipTokens.isToken(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void tokenSpawn(ItemSpawnEvent event) {
        if (skipTokens.isToken(event.getEntity().getItemStack())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void pickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p) { if (protectedPlayer(p)) event.setCancelled(true); else scanNext(p); }
    }
    @EventHandler(ignoreCancelled = true) public void inventory(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (skipTokens.blocksTransfer(p, event)) { event.setCancelled(true); return; }
        Inventory top = event.getView().getTopInventory();
        if (isBackpack(top) && !canBackpack(p, top)) { event.setCancelled(true); return; }
        if (protectedPlayer(p) && !(top.getHolder() instanceof Menus.Menu)) { event.setCancelled(true); return; }
        scanNext(p); lastActivity.put(p.getUniqueId(), System.currentTimeMillis());
    }
    @EventHandler(ignoreCancelled = true) public void drag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (skipTokens.isToken(event.getOldCursor()) || protectedPlayer(p)
                || (isBackpack(event.getView().getTopInventory()) && !canBackpack(p, event.getView().getTopInventory()))) event.setCancelled(true);
        else scanNext(p);
    }
    @EventHandler(ignoreCancelled = true) public void consume(PlayerItemConsumeEvent event) { if (protectedPlayer(event.getPlayer())) event.setCancelled(true); else scanNext(event.getPlayer()); }
    private void bucket(PlayerBucketEvent event) { if (protectedPlayer(event.getPlayer())) event.setCancelled(true); else scanNext(event.getPlayer()); }
    @EventHandler(ignoreCancelled = true) public void fillBucket(PlayerBucketFillEvent event) { bucket(event); }
    @EventHandler(ignoreCancelled = true) public void emptyBucket(PlayerBucketEmptyEvent event) { bucket(event); }
    @EventHandler public void smelt(FurnaceExtractEvent event) { scanNext(event.getPlayer()); }
    @EventHandler public void advancement(PlayerAdvancementDoneEvent event) { complete(event.getPlayer(), Type.ADVANCEMENT + ":" + event.getAdvancement().getKey().getKey()); }
    @EventHandler public void death(PlayerDeathEvent event) {
        event.getDrops().removeIf(skipTokens::isToken);
        Player p = event.getEntity(); if (!active(p)) return;
        var e = entry(p).orElseThrow();
        if (settings.deathPenalty.equals("POINT")) round.penalty(e);
        if (settings.deathPenalty.equals("TARGET")) round.rerollOnDeath(e);
    }
    @EventHandler public void respawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer(); if (!managed.contains(p.getUniqueId()) || phase != Phase.PLAYING) return;
        if (!worlds.contains(event.getRespawnLocation().getWorld())) event.setRespawnLocation(worlds.spawn(worlds.first(settings), 0));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && phase == Phase.PLAYING && !spectators.contains(p.getUniqueId())) {
                entry(p).ifPresent(entry -> syncSkipTokens(p, entry));
            }
        });
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void teleport(PlayerTeleportEvent event) {
        if (event instanceof PlayerPortalEvent) return;
        Player p = event.getPlayer();
        if (phase != Phase.PLAYING || !managed.contains(p.getUniqueId()) || event.getTo() == null) return;
        if (!settings.dimensions.contains(Catalog.dimension(event.getTo().getWorld())) || (!spectators.contains(p.getUniqueId()) && !worlds.contains(event.getTo().getWorld()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void portal(PlayerPortalEvent event) {
        if (phase != Phase.PLAYING || !managed.contains(event.getPlayer().getUniqueId())) return;
        Dimension destination = event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL
                ? (event.getFrom().getWorld().getEnvironment() == World.Environment.THE_END ? Dimension.OVERWORLD : Dimension.END)
                : (event.getFrom().getWorld().getEnvironment() == World.Environment.NETHER ? Dimension.OVERWORLD : Dimension.NETHER);
        if (!settings.dimensions.contains(destination)) { event.setCancelled(true); event.getPlayer().sendMessage(text("That dimension is disabled this round")); return; }
        if (settings.fresh || event.getTo() == null || !worlds.contains(event.getTo().getWorld())) { event.setTo(worlds.spawn(worlds.get(destination), 0)); event.setCanCreatePortal(false); }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void blockDrops(BlockDropItemEvent event) {
        if (phase != Phase.PLAYING || !settings.chaos || !active(event.getPlayer())) return;
        var choices = chaosItems(); if (choices.isEmpty()) return;
        event.getItems().forEach(item -> item.setItemStack(new ItemStack(choices.get(random.nextInt(choices.size())))));
    }
    @EventHandler(priority = EventPriority.HIGH) public void mobDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) complete(killer, Type.MOB + ":" + event.getEntityType().getKey().getKey());
        if (phase != Phase.PLAYING || round.state() != Round.State.RUNNING || !settings.chaos || !worlds.contains(event.getEntity().getWorld()) || event.getEntity() instanceof Player) return;
        var choices = chaosItems(); if (choices.isEmpty()) return;
        int count = Math.max(1, event.getDrops().size()); event.getDrops().clear();
        for (int i = 0; i < count; i++) event.getDrops().add(new ItemStack(choices.get(random.nextInt(choices.size()))));
    }
    private List<Material> chaosItems() {
        return chaosMaterials;
    }
    private void showAll(Component title, Component subtitle) {
        for (Player p : Bukkit.getOnlinePlayers()) if (managed.contains(p.getUniqueId())) p.showTitle(Title.title(title, subtitle,
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(100))));
    }
    public static Component objectiveName(Objective objective) {
        if (objective == null) return Component.text("Unknown target");
        if (objective.type() == Type.ITEM) {
            Material m = Material.matchMaterial(objective.key()); if (m != null) return Component.translatable(m.translationKey());
        }
        if (objective.type() == Type.MOB) return Component.translatable("entity.minecraft." + objective.key());
        if (objective.type() == Type.BIOME) return Component.translatable("biome.minecraft." + objective.key());
        if (objective.type() == Type.ADVANCEMENT) {
            var advancement = Bukkit.getAdvancement(NamespacedKey.minecraft(objective.key()));
            if (advancement != null && advancement.getDisplay() != null) return advancement.getDisplay().title();
        }
        return Component.text(objective.label());
    }
    public static Component target(Objective o) {
        String verb = switch (o.type()) { case ITEM -> "Find: "; case MOB -> "Kill: "; case ADVANCEMENT -> "Earn: "; case STRUCTURE -> "Visit: "; case BIOME -> "Reach: "; };
        return Component.text(verb).append(objectiveName(o));
    }
    public static String formatTime(long seconds) {
        if (seconds < 0) return "No limit";
        return seconds >= 3600 ? "%d:%02d:%02d".formatted(seconds / 3600, seconds / 60 % 60, seconds % 60) : "%d:%02d".formatted(seconds / 60, seconds % 60);
    }
    private static Component text(String message) { return Component.text("Rush Hour · " + message, NamedTextColor.YELLOW); }
    private void broadcast(String message) { Bukkit.broadcast(text(message)); }
}
