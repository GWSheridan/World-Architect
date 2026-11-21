package com.gwsheridan.worldarchitect.authoring;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;

class PackedDraftTest {
    @Test void ordinaryDocumentsKeepTheirExistingShape(){var source=JsonParser.parseString("{\"name\":\"island\",\"value\":17}");assertEquals(source,PackedDraft.pack(source));assertEquals(source,PackedDraft.unpack(source));}
    @Test void largeSparseDocumentsRecoverWithoutExceedingTheNodeBudget(){var cells=new JsonArray();for(int i=0;i<270000;i++)cells.add(i%256);var source=new JsonObject();source.add("cells",cells);var packed=PackedDraft.pack(source);assertTrue(packed.getAsJsonObject().has("packed_draft"));assertTrue(JsonDraftSize.estimate(packed)<4*1024*1024);assertEquals(source,PackedDraft.unpack(packed));assertEquals(packed,PackedDraft.pack(source));}
    @Test void corruptOrUnknownRecoveryIsRefused(){assertThrows(IllegalArgumentException.class,()->PackedDraft.unpack(JsonParser.parseString("{\"packed_draft\":2,\"gzip\":\"\"}")));assertThrows(IllegalArgumentException.class,()->PackedDraft.unpack(JsonParser.parseString("{\"packed_draft\":1,\"gzip\":\"AAAA\"}")));}
}
