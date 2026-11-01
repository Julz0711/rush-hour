package io.github.julz0711.rushhour.game;

import java.util.*;
import static io.github.julz0711.rushhour.game.Objective.*;

/** All round mutations happen on Paper's main thread. No Bukkit dependencies. */
public final class Round {
    public enum Mode { CLASSIC, RACE, BINGO }
    public enum State { RUNNING, PAUSED, FINISHED }
    public record Find(Objective objective, long second, int points, long sequence) { }
    public static final class Entry {
        public final String id;
        public final String name;
        public final Set<UUID> members = new LinkedHashSet<>();
        public final List<Objective> targets = new ArrayList<>();
        public final Set<String> completed = new HashSet<>();
        public final Set<String> used = new HashSet<>();
        public final Set<Integer> rows = new HashSet<>();
        public final List<Find> timeline = new ArrayList<>();
        public int score;
        public int skips;
        public long reachedSequence;
        public Entry(String id, String name, int skips) { this.id = id; this.name = name; this.skips = skips; }
    }
    private final ObjectivePool pool;
    private final Mode mode;
    private final Map<Tier, Integer> points;
    private final int rowBonus;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private long deadline;
    private long elapsed;
    private long sequence;
    private State state = State.RUNNING;
    private Objective shared;
    private final Set<String> raceUsed = new HashSet<>();
    private final Map<Set<String>, String> lastDrawn = new IdentityHashMap<>();

    public Round(ObjectivePool pool, Mode mode, long deadline, Map<Tier, Integer> points, int rowBonus) {
        if (pool.entries().isEmpty()) throw new IllegalArgumentException("Empty objective pool");
        if (mode == Mode.BINGO && pool.entries().size() < 25) throw new IllegalArgumentException("Bingo needs 25 unique objectives");
        this.pool = pool; this.mode = mode; this.deadline = deadline;
        this.points = Map.copyOf(points); this.rowBonus = rowBonus;
    }
    public void add(Entry entry) {
        if (entries.putIfAbsent(entry.id, entry) != null) throw new IllegalArgumentException("Duplicate entry");
        if (entry.score > 0) entry.reachedSequence = ++sequence;
        if (mode == Mode.RACE) {
            if (shared == null) shared = draw(raceUsed);
            entry.targets.add(shared);
        } else for (int i = 0; i < (mode == Mode.BINGO ? 25 : 1); i++) entry.targets.add(draw(entry.used));
    }
    private Objective draw(Set<String> used) {
        // Cycle the pool only after every objective has been used, without immediate repeats.
        if (used.size() >= pool.entries().size()) {
            used.clear();
            if (pool.entries().size() > 1 && lastDrawn.containsKey(used)) used.add(lastDrawn.get(used));
        }
        Objective objective = pool.next(used, deadline == 0 ? Math.min(1, elapsed / 3600.0) : (double) elapsed / deadline);
        used.add(objective.id());
        lastDrawn.put(used, objective.id());
        return objective;
    }
    public boolean complete(Entry entry, String objectiveId) {
        if (state != State.RUNNING || entries.get(entry.id) != entry) return false;
        Objective target = entry.targets.stream().filter(o -> o.id().equals(objectiveId)
                && (mode != Mode.BINGO || !entry.completed.contains(o.id()))).findFirst().orElse(null);
        if (target == null) return false;
        int earned = points.getOrDefault(target.tier(), 1);
        entry.completed.add(target.id());
        if (mode == Mode.BINGO) {
            for (int line = 0; line < 12; line++) {
                if (entry.rows.contains(line)) continue;
                boolean full = true;
                for (int i = 0; i < 5; i++) {
                    int index = line < 5 ? line * 5 + i : line < 10 ? i * 5 + line - 5 : line == 10 ? i * 6 : (i + 1) * 4;
                    full &= entry.completed.contains(entry.targets.get(index).id());
                }
                if (full) { entry.rows.add(line); earned += rowBonus; }
            }
        }
        entry.score += earned; entry.reachedSequence = ++sequence;
        entry.timeline.add(new Find(target, elapsed, earned, sequence));
        if (mode == Mode.RACE) {
            shared = draw(raceUsed);
            for (Entry e : entries.values()) { e.targets.clear(); e.targets.add(shared); }
        } else if (mode == Mode.CLASSIC) {
            entry.targets.clear(); entry.targets.add(draw(entry.used));
        } else if (entry.completed.size() == 25) state = State.FINISHED;
        return true;
    }
    public Objective skip(Entry entry, int slot) {
        if (state != State.RUNNING || entry.skips <= 0 || slot < 0 || slot >= entry.targets.size()) return null;
        Objective old = entry.targets.get(slot);
        if (mode == Mode.BINGO && entry.completed.contains(old.id())) return null;
        Set<String> excluded = new HashSet<>();
        if (mode == Mode.BINGO) entry.targets.forEach(o -> excluded.add(o.id()));
        else excluded.add(old.id());
        if (pool.entries().stream().noneMatch(o -> !excluded.contains(o.id()))) return null;
        Objective replacement = pool.next(excluded, deadline == 0 ? 0 : (double) elapsed / deadline);
        entry.skips--;
        if (mode == Mode.RACE) {
            shared = replacement; raceUsed.add(shared.id());
            entries.values().forEach(e -> { e.targets.clear(); e.targets.add(shared); });
        } else { entry.targets.set(slot, replacement); entry.used.add(replacement.id()); }
        return old;
    }
    public void advance(long seconds) {
        if (seconds < 0) throw new IllegalArgumentException("Negative clock delta");
        if (state != State.RUNNING) return;
        elapsed += seconds;
        if (deadline > 0 && elapsed >= deadline) state = State.FINISHED;
    }
    public void pause() { if (state != State.RUNNING) throw new IllegalStateException("Round is not running"); state = State.PAUSED; }
    public void resume() { if (state != State.PAUSED) throw new IllegalStateException("Round is not paused"); state = State.RUNNING; }
    public void addTime(long seconds) {
        if (deadline == 0) throw new IllegalStateException("This round has no deadline");
        if (seconds <= 0 || seconds > 21600) throw new IllegalArgumentException("Use 1..21600 seconds");
        deadline += seconds;
    }
    public void penalty(Entry entry) {
        if (state != State.RUNNING) return;
        entry.score = Math.max(0, entry.score - 1); entry.reachedSequence = ++sequence;
    }
    public void rerollOnDeath(Entry entry) {
        if (state != State.RUNNING || mode == Mode.BINGO) return;
        if (mode == Mode.RACE) {
            shared = draw(raceUsed);
            entries.values().forEach(e -> { e.targets.clear(); e.targets.add(shared); });
        } else { entry.targets.clear(); entry.targets.add(draw(entry.used)); }
    }
    public List<Entry> standings() {
        return entries.values().stream().sorted(Comparator.comparingInt((Entry e) -> e.score).reversed()
                .thenComparingLong(e -> e.reachedSequence).thenComparing(e -> e.id)).toList();
    }
    public Optional<Entry> entry(UUID player) { return entries.values().stream().filter(e -> e.members.contains(player)).findFirst(); }
    public Collection<Entry> entries() { return Collections.unmodifiableCollection(entries.values()); }
    public Mode mode() { return mode; }
    public State state() { return state; }
    public long elapsed() { return elapsed; }
    public long remaining() { return deadline == 0 ? -1 : Math.max(0, deadline - elapsed); }
    public long deadline() { return deadline; }
    public void finish() { state = State.FINISHED; }
}
