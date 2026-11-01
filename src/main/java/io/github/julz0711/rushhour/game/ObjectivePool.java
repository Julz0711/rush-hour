package io.github.julz0711.rushhour.game;

import java.util.*;
import java.util.random.RandomGenerator;
import static io.github.julz0711.rushhour.game.Objective.*;

/** Chooses a type, then a tier, then an objective so category size never changes weights. */
public final class ObjectivePool {
    private final List<Objective> entries;
    private final Map<Type, Integer> types;
    private final Map<Tier, Integer> tiers;
    private final boolean ramp;
    private final RandomGenerator random;

    public ObjectivePool(List<Objective> entries, Map<Type, Integer> types,
                         Map<Tier, Integer> tiers, boolean ramp, RandomGenerator random) {
        this.types = Map.copyOf(types); this.tiers = Map.copyOf(tiers);
        this.entries = entries.stream().filter(o -> types.getOrDefault(o.type(), 0) > 0
                && tiers.getOrDefault(o.tier(), 0) > 0).toList();
        this.ramp = ramp; this.random = random;
    }
    public List<Objective> entries() { return entries; }
    public Objective next(Set<String> excluded, double progress) {
        var candidates = entries.stream().filter(o -> !excluded.contains(o.id())).toList();
        if (candidates.isEmpty()) throw new IllegalStateException("No unused objectives remain");
        Type type = choose(candidates.stream().map(Objective::type).distinct().toList(),
                t -> types.getOrDefault(t, 0));
        var byType = candidates.stream().filter(o -> o.type() == type).toList();
        Tier tier = choose(byType.stream().map(Objective::tier).distinct().toList(), t -> {
            double factor = !ramp ? 1 : switch (t) {
                case EASY -> 2 - Math.clamp(progress, 0, 1);
                case MEDIUM -> 1;
                case HARD -> .25 + Math.clamp(progress, 0, 1) * 2;
                case INSANE -> .1 + Math.clamp(progress, 0, 1) * 3;
            };
            return tiers.getOrDefault(t, 0) * factor;
        });
        var matches = byType.stream().filter(o -> o.tier() == tier).toList();
        return matches.get(random.nextInt(matches.size()));
    }
    private <T> T choose(List<T> values, java.util.function.ToDoubleFunction<T> weight) {
        double total = values.stream().mapToDouble(weight).sum();
        double roll = random.nextDouble(total);
        for (T value : values) { roll -= weight.applyAsDouble(value); if (roll < 0) return value; }
        return values.getLast();
    }
}
