package io.github.julz0711.rushhour.game;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.julz0711.rushhour.game.Objective.*;

class RoundTest {
    private Objective objective(int index) { return new Objective(Type.ITEM, "item_" + index, "test", Tier.EASY, List.of(Set.of(Dimension.OVERWORLD)), true); }
    private ObjectivePool pool(int count) { return new ObjectivePool(java.util.stream.IntStream.range(0, count).mapToObj(this::objective).toList(), Map.of(Type.ITEM, 1), Map.of(Tier.EASY, 1), false, new Random(42)); }
    private Round round(Round.Mode mode, int count) { return new Round(pool(count), mode, 300, Map.of(Tier.EASY, 1), 5); }
    private Round.Entry add(Round round, String name) { var e = new Round.Entry(name, name, 3); e.members.add(UUID.randomUUID()); round.add(e); return e; }
    @Test void classicAwardsOnePointAndRollsDifferentTarget() {
        var r = round(Round.Mode.CLASSIC, 10); var e = add(r, "a"); String old = e.targets.getFirst().id();
        assertTrue(r.complete(e, old)); assertEquals(1, e.score); assertNotEquals(old, e.targets.getFirst().id()); assertFalse(r.complete(e, old));
    }
    @Test void ignoresWrongEntryAndWrongObjective() {
        var r = round(Round.Mode.CLASSIC, 10); var e = add(r, "a");
        assertFalse(r.complete(e, "ITEM:nope")); assertFalse(r.complete(new Round.Entry("a", "impostor", 1), e.targets.getFirst().id())); assertEquals(0, e.score);
    }
    @Test void skipsConsumeTokenWithoutScoringAndCannotRunWhenPaused() {
        var r = round(Round.Mode.CLASSIC, 2); var e = add(r, "a");
        for (int i = 0; i < 3; i++) assertNotNull(r.skip(e, 0));
        assertEquals(0, e.score); assertNull(r.skip(e, 0)); r.pause(); assertNull(r.skip(e, 0));
    }
    @Test void raceAwardsOnlyFirstFinderAndRollsForEveryone() {
        var r = round(Round.Mode.RACE, 10); var a = add(r, "a"); var b = add(r, "b");
        String id = a.targets.getFirst().id(); assertEquals(id, b.targets.getFirst().id());
        assertTrue(r.complete(a, id)); assertFalse(r.complete(b, id)); assertEquals(a.targets, b.targets); assertEquals(0, b.score);
    }
    @Test void raceSkipIsSharedAndUsesOnlyRequestersToken() {
        var r = round(Round.Mode.RACE, 10); var a = add(r, "a"); var b = add(r, "b");
        var before = a.targets.getFirst(); assertEquals(before, r.skip(a, 0)); assertEquals(a.targets, b.targets); assertEquals(2, a.skips); assertEquals(3, b.skips);
    }
    @Test void pauseDoesNotAdvanceOrAwardAndDeadlineStopsScoring() {
        var r = round(Round.Mode.CLASSIC, 10); var e = add(r, "a"); r.advance(42); r.pause(); r.advance(1000);
        assertEquals(258, r.remaining()); assertFalse(r.complete(e, e.targets.getFirst().id()));
        r.resume(); r.addTime(60); r.advance(318); assertEquals(Round.State.FINISHED, r.state()); assertFalse(r.complete(e, e.targets.getFirst().id()));
    }
    @Test void unlimitedClockAndTimeValidation() {
        var r = new Round(pool(1), Round.Mode.CLASSIC, 0, Map.of(Tier.EASY, 1), 0); add(r, "a"); r.advance(999999);
        assertEquals(-1, r.remaining()); assertEquals(Round.State.RUNNING, r.state()); assertThrows(IllegalStateException.class, () -> r.addTime(1)); assertThrows(IllegalArgumentException.class, () -> r.advance(-1));
    }
    @Test void tiesUseOrderScoreWasReachedEvenWithinSameSecond() {
        var r = round(Round.Mode.CLASSIC, 10); var a = add(r, "a"); var b = add(r, "b");
        r.complete(b, b.targets.getFirst().id()); r.complete(a, a.targets.getFirst().id()); assertEquals(b, r.standings().getFirst());
    }
    @Test void lateJoinCatchUpNeverWinsAnExistingScoreTie() {
        var r = round(Round.Mode.CLASSIC, 10); var leader = add(r, "leader"); r.complete(leader, leader.targets.getFirst().id());
        var late = new Round.Entry("late", "Late", 3); late.score = 1; r.add(late);
        assertEquals(leader, r.standings().getFirst());
    }
    @Test void bingoCardHas25UniqueTargetsAndOnlyAwardsSquareOnce() {
        var r = round(Round.Mode.BINGO, 30); var e = add(r, "a");
        assertEquals(25, e.targets.stream().map(Objective::id).distinct().count());
        String id = e.targets.getFirst().id(); assertTrue(r.complete(e, id)); assertFalse(r.complete(e, id)); assertNull(r.skip(e, 0));
    }
    @Test void bingoAwardsLineBonusExactlyOnceAndFinishesOnFullCard() {
        var r = round(Round.Mode.BINGO, 30); var e = add(r, "a");
        for (int i = 0; i < 5; i++) r.complete(e, e.targets.get(i).id());
        assertEquals(10, e.score); assertEquals(Set.of(0), e.rows);
        for (int i = 5; i < 25; i++) r.complete(e, e.targets.get(i).id());
        assertEquals(25 + 12 * 5, e.score); assertEquals(Round.State.FINISHED, r.state());
    }
    @Test void bingoSkipCannotDuplicateCardAndRequiresSpareObjective() {
        var r = round(Round.Mode.BINGO, 26); var e = add(r, "a"); assertNotNull(r.skip(e, 0)); assertEquals(25, e.targets.stream().distinct().count());
        var exact = round(Round.Mode.BINGO, 25); var x = add(exact, "a"); assertNull(exact.skip(x, 0)); assertEquals(3, x.skips);
    }
    @Test void tinyPoolsCycleAndSingleTargetCanBeScoredAgain() {
        var r = round(Round.Mode.CLASSIC, 2); var e = add(r, "a"); String previous = "";
        for (int i = 0; i < 100; i++) { String id = e.targets.getFirst().id(); assertNotEquals(previous, id); assertTrue(r.complete(e, id)); previous = id; }
        var one = round(Round.Mode.CLASSIC, 1); var x = add(one, "x"); assertTrue(one.complete(x, x.targets.getFirst().id())); assertNull(one.skip(x, 0));
    }
    @Test void teamsShareOneScoreAndTokenBank() {
        var r = round(Round.Mode.CLASSIC, 10); var e = add(r, "team"); UUID teammate = UUID.randomUUID(); e.members.add(teammate);
        assertSame(e, r.entry(teammate).orElseThrow()); r.complete(e, e.targets.getFirst().id()); assertEquals(1, r.entry(teammate).orElseThrow().score);
    }
    @Test void deathPenaltyDoesNotProduceNegativeScoreOrSpendSkip() {
        var r = round(Round.Mode.CLASSIC, 10); var e = add(r, "a"); r.penalty(e); assertEquals(0, e.score);
        String old = e.targets.getFirst().id(); r.rerollOnDeath(e); assertNotEquals(old, e.targets.getFirst().id()); assertEquals(3, e.skips);
    }
    @Test void emptyPoolsAndSmallBingoCardsRejected() {
        assertThrows(IllegalArgumentException.class, () -> round(Round.Mode.CLASSIC, 0)); assertThrows(IllegalArgumentException.class, () -> round(Round.Mode.BINGO, 24));
    }
}
