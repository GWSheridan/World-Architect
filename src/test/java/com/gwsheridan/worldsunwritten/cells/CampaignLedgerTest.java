package com.gwsheridan.worldsunwritten.cells;

import static org.junit.jupiter.api.Assertions.*;

import com.gwsheridan.worldsunwritten.api.CellLayout;
import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator.Key;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class CampaignLedgerTest {
    @TempDir Path root;
    private final CellLayout layout = new CellLayout(CellLayout.Shape.SQUARE, 16, 0, 0);

    private GrowthRules.Goal goal(String id, List<String> requires, boolean any, int reward) {
        return new GrowthRules.Goal(
                id,
                id,
                GrowthRules.Trigger.SIGNAL,
                Identifier.parse("pack:" + id),
                requires,
                any,
                List.of(),
                reward);
    }

    private GrowthRules rules(boolean choose, List<GrowthRules.Goal> goals) {
        return new GrowthRules(GrowthRules.Scope.TEAM, 2, 4, 4096, choose, goals);
    }

    private CellLedger open(Path path, GrowthRules rules, boolean fresh) throws Exception {
        return new CellLedger(
                path, layout, rules, "pin", fresh, 2, (slot, cell) -> cell.q() < 0 ? 5 : 1);
    }

    @Test
    void legacyOpeningDoesNotRewriteAndCampaignCannotRelabelIt() throws Exception {
        var path = root.resolve("old.json");
        var rules = rules(true, List.of());
        new CellLedger(path, layout, rules, "pin", true);
        var before = Files.readAllBytes(path);
        new CellLedger(path, layout, rules, "pin", false);
        assertArrayEquals(before, Files.readAllBytes(path));
        assertThrows(java.io.IOException.class, () -> open(path, rules, false));
        assertArrayEquals(before, Files.readAllBytes(path));
    }

    @Test
    void automaticExpansionSkipsUnaffordableCellsInStableOrder() throws Exception {
        var rules = rules(false, List.of(goal("start", List.of(), false, 3)));
        var path = root.resolve("cost.json");
        var ledger = open(path, rules, true);
        int slot = ledger.claim("team:builders");
        ledger.observe(slot, GrowthRules.Trigger.SIGNAL, "pack:start");
        assertEquals(0, ledger.credits(slot));
        assertNull(ledger.status(new Key(slot, new Cell(-1, 0))));
        assertEquals(5, ledger.cells().size());
        var before = ledger.cells();
        assertEquals(before, open(path, rules, false).cells());
    }

    @Test
    void choicesChargeTheSavedCostOnlyOnce() throws Exception {
        var rules = rules(true, List.of(goal("start", List.of(), false, 6)));
        var ledger = open(root.resolve("choice.json"), rules, true);
        int slot = ledger.claim("team:builders");
        ledger.observe(slot, GrowthRules.Trigger.SIGNAL, "pack:start");
        ledger.choose(slot, new Cell(-1, 0));
        assertEquals(1, ledger.credits(slot));
        assertThrows(IllegalArgumentException.class, () -> ledger.choose(slot, new Cell(-1, 0)));
        assertEquals(1, ledger.credits(slot));
    }

    @Test
    void deliveryClaimsExcludeConcurrentTeammatesAndSurviveEveryJournalBoundary() throws Exception {
        var rules = rules(true, List.of(goal("delivery", List.of(), false, 2)));
        var path = root.resolve("delivery.json");
        var ledger = open(path, rules, true);
        int slot = ledger.claim("team:builders");
        var player = UUID.randomUUID();
        var claim = ledger.beginDelivery(slot, "delivery", player, "a".repeat(64));
        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.beginDelivery(slot, "delivery", UUID.randomUUID(), "a".repeat(64)));
        var reopened = open(path, rules, false);
        assertEquals(claim, reopened.pendingDeliveries().getFirst());
        reopened.observe(slot, GrowthRules.Trigger.SIGNAL, "pack:delivery");
        assertEquals(2, reopened.credits(slot));
        var afterReward = open(path, rules, false);
        assertEquals(List.of(), afterReward.completeDelivery(claim));
        assertEquals(2, afterReward.credits(slot));
        assertTrue(afterReward.pendingDeliveries().isEmpty());
        assertEquals(List.of(), afterReward.completeDelivery(claim));
        assertThrows(IllegalArgumentException.class, () -> afterReward.abortDelivery(claim));
        assertThrows(
                IllegalArgumentException.class,
                () -> afterReward.beginDelivery(slot, "delivery", player, "b".repeat(64)));
    }

    @Test
    void unconsumedClaimsCanAbortWithoutPayingOrBlockingAnotherPlayer() throws Exception {
        var rules = rules(true, List.of(goal("delivery", List.of(), false, 1)));
        var ledger = open(root.resolve("abort.json"), rules, true);
        var claim = ledger.beginDelivery(0, "delivery", UUID.randomUUID(), "a".repeat(64));
        ledger.abortDelivery(claim);
        assertTrue(ledger.pendingDeliveries().isEmpty());
        assertEquals(0, ledger.credits(0));
        assertNotEquals(
                claim.transaction(),
                ledger.beginDelivery(0, "delivery", UUID.randomUUID(), "b".repeat(64))
                        .transaction());
    }

    @Test
    void graphSimulationRetainsEarlyObservationsAndAnyBranches() {
        var rules =
                rules(
                        true,
                        List.of(
                                goal("wood", List.of(), false, 1),
                                goal("stone", List.of("wood"), false, 1),
                                goal("choice", List.of("wood", "stone"), true, 1)));
        assertEquals(
                Set.of("wood", "choice"),
                ProgressionGraph.simulate(rules, Set.of("wood", "choice")));
        assertEquals(Set.of(), ProgressionGraph.simulate(rules, Set.of("stone")));
        assertEquals(3, ProgressionGraph.layers(rules).size());
    }

    @Test
    void latticeTransformsRoundTripWithoutChangingTemplateOrientations() {
        for (var shape : CellLayout.Shape.values())
            for (int q = -8; q <= 8; q++)
                for (int r = -8; r <= 8; r++) {
                    var original = new Cell(q, r);
                    var cell = original;
                    for (int turn = 0; turn < (shape == CellLayout.Shape.SQUARE ? 4 : 6); turn++)
                        cell = CellCanvas.transform(cell, shape, false);
                    assertEquals(original, cell);
                    assertEquals(
                            original,
                            CellCanvas.transform(
                                    CellCanvas.transform(original, shape, true), shape, true));
                }
    }

    @Test
    void connectionRecoveryRetainsItsFingerprintAndChecksLifecycle() throws Exception {
        var rules = rules(true, List.of());
        var path = root.resolve("connection.json");
        var ledger = open(path, rules, true);
        String hash = "b".repeat(64);
        ledger.connection(0, "route", null, CellLedger.Status.QUEUED, hash, "");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ledger.connection(
                                0,
                                "route",
                                CellLedger.Status.QUEUED,
                                CellLedger.Status.READY,
                                hash,
                                ""));
        ledger.connection(
                0, "route", CellLedger.Status.QUEUED, CellLedger.Status.APPLYING, hash, "");
        ledger.connection(
                0,
                "route",
                CellLedger.Status.APPLYING,
                CellLedger.Status.CONFLICT,
                hash,
                "A player build is preserved");
        var reopened = open(path, rules, false);
        assertTrue(reopened.connections().get("0:route").resume());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        reopened.connection(
                                0,
                                "route",
                                CellLedger.Status.CONFLICT,
                                CellLedger.Status.APPLYING,
                                "c".repeat(64),
                                ""));
        reopened.connection(
                0, "route", CellLedger.Status.CONFLICT, CellLedger.Status.APPLYING, hash, "");
        reopened.connection(
                0, "route", CellLedger.Status.APPLYING, CellLedger.Status.READY, hash, "");
        var before = Files.readAllBytes(path);
        var loaded = open(path, rules, false);
        assertEquals(CellLedger.Status.READY, loaded.connections().get("0:route").status());
        assertArrayEquals(before, Files.readAllBytes(path));
    }

    @Test
    void corruptedPaidDeliveryFailsBeforeRewritingAnyJournal() throws Exception {
        var rules = rules(true, List.of(goal("delivery", List.of(), false, 2)));
        var path = root.resolve("corrupt.json");
        var ledger = open(path, rules, true);
        ledger.beginDelivery(0, "delivery", UUID.randomUUID(), "a".repeat(64));
        var json = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        json.getAsJsonObject("deliveries")
                .getAsJsonObject("0:delivery")
                .addProperty("state", "PAID");
        Files.writeString(path, json.toString());
        var corrupt = Files.readAllBytes(path);
        assertThrows(java.io.IOException.class, () -> open(path, rules, false));
        assertArrayEquals(corrupt, Files.readAllBytes(path));
    }
}
