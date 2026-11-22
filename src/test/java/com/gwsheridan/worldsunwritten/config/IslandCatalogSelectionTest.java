package com.gwsheridan.worldsunwritten.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IslandCatalogSelectionTest {
    @Test void seededSlotsAreStableAcrossRequestOrdersAndRespectWeights(){
        var weights=List.of(1,3);var serial=IntStream.rangeClosed(1,4095).map(slot->IslandCatalog.weightedIndex(weights,194723,slot)).toArray();
        var parallel=IntStream.rangeClosed(1,4095).parallel().map(slot->IslandCatalog.weightedIndex(weights,194723,slot)).toArray();assertArrayEquals(serial,parallel);
        long first=Arrays.stream(serial).filter(index->index==0).count();assertTrue(first>900&&first<1150,"Weighted slot capacity is unexpectedly skewed.");
        for(int slot=4095;slot>=1;slot--)assertEquals(serial[slot-1],IslandCatalog.weightedIndex(weights,194723,slot));
        long changed=IntStream.rangeClosed(1,4095).filter(slot->serial[slot-1]!=IslandCatalog.weightedIndex(weights,194724,slot)).count();assertTrue(changed>800);
    }
    @Test void invalidCatalogSelectionBudgetsAreRefused(){
        for(var weights:List.of(List.<Integer>of(),List.of(0),List.of(101),Collections.nCopies(17,1)))assertThrows(IllegalArgumentException.class,()->IslandCatalog.weightedIndex(weights,1,1));
        assertThrows(IllegalArgumentException.class,()->IslandCatalog.weightedIndex(List.of(1),1,0));assertThrows(IllegalArgumentException.class,()->IslandCatalog.weightedIndex(List.of(1),1,4096));
    }
}
