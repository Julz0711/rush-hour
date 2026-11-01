package io.github.julz0711.rushhour.game;

import java.util.*;

/** Server independent objective; a route is a set of dimensions required together. */
public record Objective(Type type, String key, String category, Tier tier,
                        List<Set<Dimension>> routes, boolean enabledByDefault) {
    public enum Type { ITEM, MOB, ADVANCEMENT, STRUCTURE, BIOME }
    public enum Tier { EASY, MEDIUM, HARD, INSANE }
    public enum Dimension { OVERWORLD, NETHER, END }
    public Objective {
        Objects.requireNonNull(type); Objects.requireNonNull(key); Objects.requireNonNull(tier);
        routes = routes.stream().map(Set::copyOf).toList();
    }
    public String id() { return type.name() + ":" + key; }
    public boolean available(Set<Dimension> dimensions) {
        return routes.stream().anyMatch(dimensions::containsAll);
    }
    public String label() {
        String part = key.substring(key.lastIndexOf('/') + 1).replace('_', ' ');
        return part.isEmpty() ? key : Character.toUpperCase(part.charAt(0)) + part.substring(1);
    }
}
