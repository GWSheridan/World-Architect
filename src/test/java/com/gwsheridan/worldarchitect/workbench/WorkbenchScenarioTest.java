package com.gwsheridan.worldarchitect.workbench;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkbenchScenarioTest {
    @Test void sharedClientServerAndNodeContract()throws Exception{
        try(var reader=new InputStreamReader(getClass().getResourceAsStream("/workbench/scenario-contract.json"),StandardCharsets.UTF_8)){
            var corpus=JsonParser.parseReader(reader).getAsJsonObject();
            for(var value:corpus.getAsJsonArray("cases")){
                var test=value.getAsJsonObject();var candidate=corpus.getAsJsonObject("base").deepCopy();
                if(test.has("path")){
                    var path=test.getAsJsonArray("path");JsonElement parent=candidate;
                    for(int i=0;i<path.size()-1;i++)parent=parent.isJsonArray()?parent.getAsJsonArray().get(path.get(i).getAsInt()):parent.getAsJsonObject().get(path.get(i).getAsString());
                    var last=path.get(path.size()-1);
                    if(parent.isJsonArray())parent.getAsJsonArray().set(last.getAsInt(),test.get("value").deepCopy());
                    else if(test.has("remove"))parent.getAsJsonObject().remove(last.getAsString());
                    else parent.getAsJsonObject().add(last.getAsString(),test.get("value").deepCopy());
                }
                if(test.get("valid").getAsBoolean())assertDoesNotThrow(()->WorkbenchScenario.validate(candidate),test.get("name").getAsString());
                else assertThrows(IllegalArgumentException.class,()->WorkbenchScenario.validate(candidate),test.get("name").getAsString());
            }
        }
    }
    @Test void aggregateChunkBudgetAndExactBooleansApplyBeforeAnyExport(){
        var doc=WorkbenchCommands.scenario("demo.zip","a".repeat(64),18,java.util.List.of("minecraft:overworld"));
        var job=doc.getAsJsonArray("runs").get(0).getAsJsonObject();var area=job.getAsJsonArray("regions").get(0).getAsJsonObject();area.addProperty("width",16);area.addProperty("depth",16);
        for(int i=1;i<9;i++){var clone=job.deepCopy();clone.addProperty("name","run_"+i);doc.getAsJsonArray("runs").add(clone);}
        assertThrows(IllegalArgumentException.class,()->WorkbenchScenario.validate(doc));
    }
}
