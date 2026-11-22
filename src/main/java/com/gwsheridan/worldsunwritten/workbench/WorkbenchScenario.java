package com.gwsheridan.worldsunwritten.workbench;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Same format-1 contract as the source runner. Validation performs no file or world operations. */
public final class WorkbenchScenario {
    public static final int MAX_BYTES=262144;
    private WorkbenchScenario(){}
    private static void require(boolean valid,String message){if(!valid)throw new IllegalArgumentException(message);}
    private static JsonObject object(JsonElement value,String... fields){
        require(value!=null&&value.isJsonObject(),"Expected scenario object.");var obj=value.getAsJsonObject();var allowed=Set.of(fields);
        require(allowed.containsAll(obj.keySet()),"Unknown scenario field.");return obj;
    }
    private static String string(JsonElement value){require(value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString(),"Expected scenario string.");return value.getAsString();}
    private static boolean bool(JsonElement value){require(value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isBoolean(),"Expected scenario boolean.");return value.getAsBoolean();}
    private static int integer(JsonElement value,int min,int max){
        require(value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber(),"Expected scenario integer.");
        try{int number=value.getAsBigDecimal().intValueExact();require(number>=min&&number<=max,"Scenario integer is outside "+min+"–"+max+".");return number;}
        catch(ArithmeticException e){throw new IllegalArgumentException("Expected bounded scenario integer.",e);}
    }
    private static JsonArray list(JsonElement value,int min,int max){require(value!=null&&value.isJsonArray(),"Expected scenario list.");var list=value.getAsJsonArray();require(list.size()>=min&&list.size()<=max,"Scenario list exceeds its bounds.");return list;}
    private static String id(JsonElement value){String text=string(value);require(text.length()<=128&&text.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"),"Invalid scenario resource ID.");return text;}
    public static JsonObject validate(JsonElement document){
        require(document!=null&&document.toString().getBytes(StandardCharsets.UTF_8).length<=MAX_BYTES,"Scenario exceeds 256 KiB.");
        var root=object(document,"format","bundle","budgets","runs");require(integer(root.get("format"),1,1)==1,"Unsupported scenario format.");
        var bundle=object(root.get("bundle"),"file","sha256");require(string(bundle.get("file")).matches("[a-zA-Z0-9_.-]{1,140}\\.zip"),"Use a local bundle ZIP filename.");require(string(bundle.get("sha256")).matches("[a-f0-9]{64}"),"Use a SHA-256 bundle fingerprint.");
        var budgets=object(root.get("budgets"),"timeout_seconds","heap_mib");integer(budgets.get("timeout_seconds"),30,900);integer(budgets.get("heap_mib"),768,4096);
        var names=new HashSet<String>();int total=0;
        for(var value:list(root.get("runs"),1,16)){
            var run=object(value,"name","seed","expect_startup","expected_error","safe_spawn","restart","dimensions","regions","probes");
            String name=string(run.get("name"));require(name.matches("[a-z0-9_-]{1,40}")&&names.add(name),"Use unique scenario run names.");
            String seed=string(run.get("seed"));require(seed.matches("-?[0-9]{1,20}"),"Seeds must be signed 64-bit strings.");try{Long.parseLong(seed);}catch(NumberFormatException e){throw new IllegalArgumentException("Seeds must fit signed 64 bits.",e);}
            String startup=string(run.get("expect_startup"));require(startup.equals("accept")||startup.equals("reject"),"Invalid startup expectation.");bool(run.get("safe_spawn"));bool(run.get("restart"));
            if(startup.equals("reject")||run.has("expected_error")){String error=string(run.get("expected_error"));require(error.length()>=8&&error.length()<=200&&!error.contains("\n")&&!error.contains("\r"),"Use a specific expected startup error substring.");}
            var dimensions=new HashSet<String>();for(var dim:list(run.get("dimensions"),1,16))require(dimensions.add(id(dim)),"Duplicate scenario dimension.");
            int chunks=0;for(var area:list(run.get("regions"),0,32)){
                var region=object(area,"dimension","chunk_x","chunk_z","width","depth");require(dimensions.contains(string(region.get("dimension"))),"Region dimension is not selected.");
                integer(region.get("chunk_x"),-1874980,1874980);integer(region.get("chunk_z"),-1874980,1874980);chunks+=integer(region.get("width"),1,16)*integer(region.get("depth"),1,16);
            }
            require(chunks<=256,"A run may generate at most 256 requested chunks.");total+=chunks;
            for(var valueProbe:list(run.get("probes"),0,128)){
                var probe=object(valueProbe,"dimension","x","y","z","block","biome","structure");require(dimensions.contains(string(probe.get("dimension"))),"Probe dimension is not selected.");
                integer(probe.get("x"),-29999680,29999680);integer(probe.get("z"),-29999680,29999680);integer(probe.get("y"),-2032,2031);
                require(probe.has("block")||probe.has("biome")||probe.has("structure"),"Empty scenario probe.");
                if(probe.has("block")){String state=string(probe.get("block"));require(state.length()<=500&&state.matches("[a-z0-9_.-]+:[a-z0-9_./-]+(?:\\[[a-z0-9_=,.-]+\\])?"),"Invalid block-state probe.");}
                for(String key:List.of("biome","structure"))if(probe.has(key))id(probe.get(key));
            }
        }
        require(total<=2048,"A scenario may request at most 2048 chunks.");return root.deepCopy();
    }
}
