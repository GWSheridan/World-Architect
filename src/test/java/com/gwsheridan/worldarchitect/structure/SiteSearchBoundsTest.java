package com.gwsheridan.worldarchitect.structure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SiteSearchBoundsTest {
    @Test void searchBoundsRejectOverflowAndExcessWork(){assertDoesNotThrow(()->new SiteSearch.Bounds(-128,-128,128,128,256,32));for(int[] b:new int[][]{{0,0,257,1,256,32},{1,0,0,1,1,1},{0,0,1,1,257,1},{0,0,1,1,1,33},{Integer.MIN_VALUE,0,Integer.MAX_VALUE,1,1,1},{0,0,2_000_000,1,1,1},{0,0,1,1,0,1}})assertThrows(IllegalArgumentException.class,()->new SiteSearch.Bounds(b[0],b[1],b[2],b[3],b[4],b[5]));}
}
