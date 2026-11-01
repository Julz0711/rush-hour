package io.github.julz0711.rushhour;

import io.github.julz0711.rushhour.game.*;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;
import static io.github.julz0711.rushhour.game.Objective.*;

public final class Settings {
    public final long deadline;
    public final int countdown, teamCount, teamLimit, skips, rowBonus, spread, pregen, beat, idleSeconds;
    public final boolean teams, giveSkipped, chaos, hidden, tiersEnabled, ramp, fresh, keepInventory, pvp;
    public final String lobby, seed, lateJoin, deathPenalty;
    public final double catchUp;
    public final List<String> kit;
    public final Round.Mode gameType;
    public final EnumSet<Dimension> dimensions = EnumSet.noneOf(Dimension.class);
    public final EnumMap<Type, Integer> typeWeights = new EnumMap<>(Type.class);
    public final EnumMap<Tier, Integer> tierWeights = new EnumMap<>(Tier.class);
    public final EnumMap<Tier, Integer> points = new EnumMap<>(Tier.class);

    public Settings(FileConfiguration c) {
        deadline = c.getLong("deadline-seconds", 3600);
        if (deadline != 0 && (deadline < 300 || deadline > 21600)) throw new IllegalArgumentException("deadline-seconds must be 0 or 300..21600");
        countdown = range(c, "countdown-seconds", 10, 0, 60);
        String mode = c.getString("mode", "SOLO").toUpperCase(Locale.ROOT);
        if (!Set.of("SOLO", "TEAMS").contains(mode)) throw new IllegalArgumentException("mode must be SOLO or TEAMS");
        teams = mode.equals("TEAMS");
        gameType = Round.Mode.valueOf(c.getString("game-type", "CLASSIC").toUpperCase(Locale.ROOT));
        teamCount = range(c, "teams.count", 2, 2, 8); teamLimit = range(c, "teams.size-limit", 4, 1, 100);
        skips = range(c, "skip-tokens", 3, 0, 10);
        rowBonus = range(c, "bingo-row-bonus", 5, 0, 1000);
        giveSkipped = c.getBoolean("give-skipped-item"); chaos = c.getBoolean("chaos"); hidden = c.getBoolean("hidden-leaderboard");
        tiersEnabled = c.getBoolean("difficulty.enabled", true); ramp = tiersEnabled && c.getBoolean("difficulty.ramp", true);
        for (String d : c.getStringList("dimensions")) dimensions.add(Dimension.valueOf(d.toUpperCase(Locale.ROOT)));
        if (dimensions.isEmpty()) throw new IllegalArgumentException("Enable at least one dimension");
        for (Type t : Type.values()) typeWeights.put(t, range(c, "target-weights." + t, t == Type.ITEM ? 60 : 0, 0, 1000));
        for (Tier t : Tier.values()) {
            tierWeights.put(t, tiersEnabled ? range(c, "difficulty.weights." + t, 1, 0, 1000) : 1);
            points.put(t, tiersEnabled ? range(c, "difficulty.points." + t, 1, 1, 1000) : 1);
        }
        if (typeWeights.values().stream().mapToInt(Integer::intValue).sum() == 0) throw new IllegalArgumentException("Enable at least one target type");
        if (tierWeights.values().stream().mapToInt(Integer::intValue).sum() == 0) throw new IllegalArgumentException("Enable at least one tier");
        fresh = c.getBoolean("world.fresh"); lobby = c.getString("world.lobby", ""); seed = c.getString("world.seed", "");
        spread = range(c, "world.spread-radius", 128, 0, 2048); pregen = range(c, "world.pregen-radius-chunks", 2, 0, 16);
        beat = range(c, "recap.beat-ticks", 20, 2, 100);
        idleSeconds = range(c, "late-join.idle-spectate-seconds", 0, 0, 21600);
        keepInventory = c.getBoolean("rules.keep-inventory", true); pvp = c.getBoolean("rules.pvp");
        kit = List.copyOf(c.getStringList("rules.starter-kit"));
        lateJoin = c.getString("late-join.mode", "SPECTATE").toUpperCase(Locale.ROOT);
        deathPenalty = c.getString("rules.death-penalty", "NONE").toUpperCase(Locale.ROOT);
        if (!Set.of("PLAY", "SPECTATE").contains(lateJoin)) throw new IllegalArgumentException("late-join.mode must be PLAY or SPECTATE");
        if (!Set.of("NONE", "POINT", "TARGET").contains(deathPenalty)) throw new IllegalArgumentException("Unknown death penalty");
        catchUp = c.getDouble("late-join.catch-up-points", 0);
        if (!Double.isFinite(catchUp) || catchUp < 0 || catchUp > 100) throw new IllegalArgumentException("catch-up-points must be 0..100");
    }
    private static int range(FileConfiguration c, String path, int fallback, int min, int max) {
        int value = c.getInt(path, fallback);
        if (value < min || value > max) throw new IllegalArgumentException(path + " must be " + min + ".." + max);
        return value;
    }
}
