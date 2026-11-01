package io.github.julz0711.rushhour.game;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.julz0711.rushhour.game.Objective.*;

class ObjectivePoolTest {
    @Test void dimensionRoutesSupportAlternativesAndJointRequirements() {
        var rod = new Objective(Type.ITEM, "blaze_rod", "nether", Tier.HARD, List.of(Set.of(Dimension.NETHER)), true);
        assertFalse(rod.available(Set.of(Dimension.OVERWORLD))); assertTrue(rod.available(Set.of(Dimension.NETHER)));
        var pearl = new Objective(Type.ITEM, "ender_pearl", "misc", Tier.MEDIUM, List.of(Set.of(Dimension.OVERWORLD), Set.of(Dimension.NETHER)), true);
        assertTrue(pearl.available(Set.of(Dimension.NETHER))); assertFalse(pearl.available(Set.of(Dimension.END)));
        var eye = new Objective(Type.ITEM, "ender_eye", "misc", Tier.HARD, List.of(Set.of(Dimension.OVERWORLD, Dimension.NETHER)), true);
        assertFalse(eye.available(Set.of(Dimension.OVERWORLD))); assertTrue(eye.available(Set.of(Dimension.OVERWORLD, Dimension.NETHER)));
    }
    @Test void zeroWeightTypesAndTiersNeverSelected() {
        var item = new Objective(Type.ITEM, "dirt", "nature", Tier.EASY, List.of(Set.of(Dimension.OVERWORLD)), true);
        var mob = new Objective(Type.MOB, "warden", "mobs", Tier.INSANE, List.of(Set.of(Dimension.OVERWORLD)), true);
        var pool = new ObjectivePool(List.of(item, mob), Map.of(Type.ITEM, 1, Type.MOB, 0), Map.of(Tier.EASY, 1, Tier.INSANE, 0), true, new Random(1));
        for (int i = 0; i < 100; i++) assertEquals(item, pool.next(Set.of(), 1));
        assertThrows(IllegalStateException.class, () -> pool.next(Set.of(item.id()), 0));
    }
    @Test void typeWeightIsIndependentOfCatalogSize() {
        List<Objective> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) list.add(new Objective(Type.ITEM, "item" + i, "misc", Tier.EASY, List.of(Set.of(Dimension.OVERWORLD)), true));
        list.add(new Objective(Type.MOB, "cow", "mobs", Tier.EASY, List.of(Set.of(Dimension.OVERWORLD)), true));
        var pool = new ObjectivePool(list, Map.of(Type.ITEM, 1, Type.MOB, 1), Map.of(Tier.EASY, 1), false, new Random(1));
        long mobs = java.util.stream.IntStream.range(0, 2000).mapToObj(i -> pool.next(Set.of(), 0)).filter(o -> o.type() == Type.MOB).count();
        assertTrue(mobs > 850 && mobs < 1150, "Weights should be roughly 50/50, actual " + mobs);
    }
}
