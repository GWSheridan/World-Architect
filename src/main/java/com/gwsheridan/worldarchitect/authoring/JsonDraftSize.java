package com.gwsheridan.worldarchitect.authoring;

import com.google.gson.JsonElement;
import java.util.ArrayDeque;

/** Conservative object/string accounting, without relying on compact JSON byte length alone. */
public final class JsonDraftSize {
    private JsonDraftSize(){}
    public static long estimate(JsonElement value){
        var queue=new ArrayDeque<JsonElement>();queue.add(value);long bytes=0;int nodes=0;
        while(!queue.isEmpty()){
            if(++nodes>262144)return Long.MAX_VALUE;
            var next=queue.removeLast();bytes+=128;
            if(next.isJsonObject())for(var entry:next.getAsJsonObject().entrySet()){bytes+=64+entry.getKey().length()*2L;queue.add(entry.getValue());}
            else if(next.isJsonArray()){bytes+=next.getAsJsonArray().size()*8L;next.getAsJsonArray().forEach(queue::add);}
            else if(next.isJsonPrimitive()&&next.getAsJsonPrimitive().isString())bytes+=next.getAsString().length()*2L;
        }
        return bytes;
    }
}
