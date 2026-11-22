package com.gwsheridan.worldsunwritten.authoring;

import com.google.gson.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DraftHistoryTest {
    @TempDir Path root;
    @Test void copiesSnapshotsAndBranchesWithoutChangingTheOriginalHistory() {
        var value=new JsonObject();value.addProperty("n",0);
        var history=new DraftHistory<JsonObject>(value,JsonObject::deepCopy,v->10,4,40);
        value.addProperty("n",1);history.checkpoint(value);value.addProperty("n",2);history.checkpoint(value);
        var fork=history.fork();assertEquals(1,fork.undo().get("n").getAsInt());
        assertEquals(1,history.undo().get("n").getAsInt());assertEquals(0,history.undo().get("n").getAsInt());
        var result=history.redo();result.addProperty("n",99);assertEquals(2,history.redo().get("n").getAsInt());
        history.undo();value.addProperty("n",3);history.checkpoint(value);assertFalse(history.canRedo());
        assertTrue(fork.canRedo());assertEquals(2,fork.redo().get("n").getAsInt());
    }
    @Test void budgetsEvictOldCheckpointsAndRejectOversizedEditsTransactionally() {
        var history=new DraftHistory<>("a",v->v,String::length,3,6);
        history.checkpoint("bb");history.checkpoint("ccc");history.checkpoint("dd");
        assertEquals(2,history.size());assertEquals(5,history.bytes());assertEquals("ccc",history.undo());
        assertThrows(IllegalArgumentException.class,()->history.checkpoint("1234567"));assertEquals("dd",history.redo());
    }
    @Test void recoveryRequiresMatchingBaseAndNeverChangesItsSourceDocument() throws Exception {
        var doc=JsonParser.parseString("{\"draft\":[1,2]}");String base=DraftRecovery.fingerprint("saved revision 3");
        DraftRecovery.write(root,"author/draft",base,doc);
        assertEquals(doc,DraftRecovery.read(root,"author/draft",base).orElseThrow());
        assertThrows(java.io.IOException.class,()->DraftRecovery.read(root,"author/draft",DraftRecovery.fingerprint("revision 4")));
        assertEquals(doc,DraftRecovery.read(root,"author/draft",base).orElseThrow());
        DraftRecovery.discard(root,"author/draft");assertTrue(DraftRecovery.read(root,"author/draft",base).isEmpty());
    }
    @Test void sharedBudgetTrimmingKeepsTheCurrentDraftAndCanDiscardRedoBeforeRejectingIt(){
        var h=new DraftHistory<>("aaaa",v->v,String::length,8,64);h.checkpoint("bbbbb");h.checkpoint("cccccc");h.undo();
        var fork=h.fork();fork.trimTo(5);assertFalse(fork.canUndo());assertFalse(fork.canRedo());assertEquals(5,fork.bytes());
        assertTrue(h.canUndo());assertTrue(h.canRedo());assertThrows(IllegalArgumentException.class,()->h.trimTo(4));assertEquals("cccccc",h.redo());
    }
    @Test void objectAccountingIncludesStringsAndContainers(){
        assertTrue(JsonDraftSize.estimate(JsonParser.parseString("{\"long-name\":[1,2,3]}"))>128*4);
        assertTrue(JsonDraftSize.estimate(new JsonPrimitive("x".repeat(10000)))>=20000);
    }
    @Test void corruptAndOversizedRecoveryFilesAreRefusedWithoutOverwritingThem() throws Exception {
        String key="draft",base=DraftRecovery.fingerprint("base");Path file=root.resolve(DraftRecovery.fingerprint(key)+".json");
        Files.writeString(file,"not json");byte[] before=Files.readAllBytes(file);
        assertThrows(java.io.IOException.class,()->DraftRecovery.read(root,key,base));assertArrayEquals(before,Files.readAllBytes(file));
        assertThrows(java.io.IOException.class,()->DraftRecovery.write(root,key,base,new JsonPrimitive("x".repeat(DraftRecovery.MAX_BYTES))));assertArrayEquals(before,Files.readAllBytes(file));
    }
}
