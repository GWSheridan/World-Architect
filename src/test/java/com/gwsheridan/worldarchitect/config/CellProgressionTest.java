package com.gwsheridan.worldarchitect.config;

import com.gwsheridan.worldarchitect.api.CellLayout;
import com.gwsheridan.worldarchitect.api.CellLayout.Cell;
import com.gwsheridan.worldarchitect.cells.*;
import com.gwsheridan.worldarchitect.worldgen.CellGenerator.Key;
import java.nio.file.*;
import java.util.*;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CellProgressionTest {
    @TempDir Path directory;
    private final CellLayout layout=new CellLayout(CellLayout.Shape.SQUARE,16,0,0);
    private GrowthRules.Goal goal(String id,String source,List<String> requires,boolean any,int credits){return new GrowthRules.Goal(id,id,GrowthRules.Trigger.SIGNAL,Identifier.parse("pack:"+source),requires,any,List.of(),credits);}
    private GrowthRules rules(List<GrowthRules.Goal> goals,boolean choice){return new GrowthRules(GrowthRules.Scope.PLAYER,4,8,4096,choice,goals);}
    @Test void rewardsWaitForPrerequisitesAndPayOnceAcrossRestarts()throws Exception{
        var rules=rules(List.of(goal("start","start",List.of(),false,1),goal("tools","tools",List.of("start"),false,2)),false);assertTrue(rules.validate().isSuccess());
        var path=directory.resolve("progress.json");var ledger=new CellLedger(path,layout,rules,"pin",true);int slot=ledger.claim("player:one");
        assertEquals(List.of(),ledger.observe(slot,GrowthRules.Trigger.SIGNAL,"pack:tools"));assertEquals(2,ledger.cells().size());
        assertEquals(List.of("start","tools"),ledger.observe(slot,GrowthRules.Trigger.SIGNAL,"pack:start"));assertEquals(0,ledger.credits(slot));assertEquals(5,ledger.cells().size());
        var reopened=new CellLedger(path,layout,rules,"pin",false);assertEquals(slot,reopened.claim("player:one"));assertEquals(List.of(),reopened.observe(slot,GrowthRules.Trigger.SIGNAL,"pack:start"));assertEquals(5,reopened.cells().size());assertEquals(Set.of("start","tools"),reopened.completed(slot));
    }
    @Test void independentOwnersDoNotShareRewardsAndChoiceSpendsOneCredit()throws Exception{
        var rules=rules(List.of(goal("start","start",List.of(),false,2)),true);var ledger=new CellLedger(directory.resolve("progress.json"),layout,rules,"pin",true);int a=ledger.claim("team:red"),b=ledger.claim("team:blue");assertNotEquals(a,b);
        ledger.observe(a,GrowthRules.Trigger.SIGNAL,"pack:start");assertEquals(2,ledger.credits(a));assertEquals(0,ledger.credits(b));var cell=new Cell(1,0);ledger.choose(a,cell);assertEquals(1,ledger.credits(a));assertEquals(CellLedger.Status.QUEUED,ledger.status(new Key(a,cell)));assertNull(ledger.status(new Key(b,cell)));assertThrows(IllegalArgumentException.class,()->ledger.choose(a,cell));assertEquals(1,ledger.credits(a));
    }
    @Test void interruptedTransitionsAndConflictsSurviveAndCannotBeResetAsNew()throws Exception{
        var rules=rules(List.of(),false);var path=directory.resolve("progress.json");var ledger=new CellLedger(path,layout,rules,"pin",true);var key=new Key(0,new Cell(1,0));ledger.queue(key);ledger.transition(key,CellLedger.Status.QUEUED,CellLedger.Status.APPLYING);
        ledger=new CellLedger(path,layout,rules,"pin",false);assertEquals(CellLedger.Status.APPLYING,ledger.status(key));ledger.transition(key,CellLedger.Status.APPLYING,CellLedger.Status.CONFLICT);ledger.retry(key);assertEquals(CellLedger.Status.APPLYING,ledger.status(key));assertThrows(java.io.IOException.class,()->new CellLedger(path,layout,rules,"other pin",true));Files.delete(path);assertThrows(java.io.IOException.class,()->new CellLedger(path,layout,rules,"pin",false));
    }
    @Test void objectiveGraphRejectsCyclesAndMissingPrerequisites(){assertTrue(rules(List.of(goal("a","a",List.of("b"),false,1),goal("b","b",List.of("a"),false,1)),false).validate().error().isPresent());assertTrue(rules(List.of(goal("a","a",List.of("absent"),false,1)),false).validate().error().isPresent());assertTrue(goal("a","a",List.of("b","c"),true,1).prerequisites(Set.of("c")));assertFalse(goal("a","a",List.of("b","c"),false,1).prerequisites(Set.of("c")));}
    @Test void reassignmentPreservesOldTerritoriesInsteadOfRecyclingThem()throws Exception{
        var rules=rules(List.of(),false);var path=directory.resolve("progress.json");var ledger=new CellLedger(path,layout,rules,"pin",true);assertEquals(1,ledger.claim("player:first"));ledger.reassign("player:first",3);assertEquals(2,ledger.claim("player:second"));assertEquals(4,ledger.claim("player:third"));assertNotNull(ledger.status(new Key(1,Cell.ORIGIN)));assertThrows(IllegalArgumentException.class,()->ledger.reassign("player:second",3));
        var reopened=new CellLedger(path,layout,rules,"pin",false);assertEquals(3,reopened.assigned("player:first"));assertEquals(5,reopened.claim("player:fourth"));
    }
    @Test void pendingOrderAndReadOnlyViewsSurviveReopening()throws Exception{
        var rules=rules(List.of(),false);var path=directory.resolve("progress.json");var ledger=new CellLedger(path,layout,rules,"pin",true);var first=new Key(0,new Cell(2,0));var second=new Key(0,new Cell(-1,0));ledger.queue(first);ledger.queue(second);var bytes=Files.readAllBytes(path);assertEquals(List.of(first,second),ledger.pending());assertTrue(ledger.completed(7).isEmpty());assertEquals(0,ledger.credits(7));assertArrayEquals(bytes,Files.readAllBytes(path));assertThrows(UnsupportedOperationException.class,()->ledger.cells().clear());
        var reopened=new CellLedger(path,layout,rules,"pin",false);assertEquals(List.of(first,second),reopened.pending());assertEquals(3,reopened.territoryCells(0).size());reopened.transition(first,CellLedger.Status.QUEUED,CellLedger.Status.APPLYING);reopened.transition(first,CellLedger.Status.APPLYING,CellLedger.Status.READY);assertEquals(List.of(second),reopened.pending());assertTrue(reopened.ready().contains(first));
    }
}
