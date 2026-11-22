package com.gwsheridan.worldsunwritten.structure;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoolConstraintsTest {
    @Test void verticalPortsSelectFormatThreeAndCannotBeReadAsAnOlderFormat(){
        var port=new ProceduralPool.Port("root","shaft",new net.minecraft.core.BlockPos(1,3,1),net.minecraft.core.Direction.UP,"rooms","shaft");
        var p=original().withPorts(List.of(port)).withConstraints(new ProceduralPool.Constraints(2,List.of()));assertEquals(3,p.format());
        var json=ProceduralPool.CODEC.encodeStart(JsonOps.INSTANCE,p).getOrThrow().getAsJsonObject();assertEquals(p,ProceduralPool.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow());
        for(int format:new int[]{1,2}){json.addProperty("format",format);assertTrue(ProceduralPool.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());}
    }
    private ProceduralPool original(){return new ProceduralPool("root",8,128,42,List.of(),List.of());}
    @Test void originalFormatKeepsItsEncodingAndRejectsNewConstraints(){
        var p=original();var json=ProceduralPool.CODEC.encodeStart(JsonOps.INSTANCE,p).getOrThrow().getAsJsonObject();
        assertEquals(1,json.get("format").getAsInt());assertFalse(json.has("constraints"));
        json.add("constraints",JsonParser.parseString("{\"max_depth\":2}"));assertTrue(ProceduralPool.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());
    }
    @Test void versionTwoSurvivesAllOrdinaryPoolEdits(){
        var c=new ProceduralPool.Constraints(3,List.of(new ProceduralPool.ModuleCount("room",1,2)));
        var p=original().withConstraints(c).withSettings("root",10,64,7).withPorts(List.of()).withChoices(List.of());
        assertEquals(2,p.format());assertEquals(c,p.constraints());
        assertEquals(p,ProceduralPool.CODEC.parse(JsonOps.INSTANCE,ProceduralPool.CODEC.encodeStart(JsonOps.INSTANCE,p).getOrThrow()).getOrThrow());
    }
    @Test void impossibleCountsAndDuplicateRequirementsAreRejected(){
        assertTrue(original().withConstraints(new ProceduralPool.Constraints(0,List.of())).validate().error().isPresent());
        assertTrue(original().withConstraints(new ProceduralPool.Constraints(2,List.of(new ProceduralPool.ModuleCount("root",0,0)))).validate().error().isPresent());
        assertTrue(original().withConstraints(new ProceduralPool.Constraints(2,List.of(new ProceduralPool.ModuleCount("room",8,8)))).validate().error().isPresent());
        assertTrue(original().withConstraints(new ProceduralPool.Constraints(2,List.of(new ProceduralPool.ModuleCount("room",1,2),new ProceduralPool.ModuleCount("room",1,2)))).validate().error().isPresent());
    }
    @Test void allCountRangesAreCheckedWithoutIntegerOverflow(){
        for(String json:List.of("{\"max_depth\":16}","{\"modules\":[{\"module\":\"room\",\"min\":3,\"max\":2}]}","{\"modules\":[{\"module\":\"room\",\"max\":2147483647}]}"))assertTrue(ProceduralPool.Constraints.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(json)).error().isPresent());
    }
}
