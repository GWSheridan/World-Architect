package com.gwsheridan.worldarchitect.config;

import com.gwsheridan.worldarchitect.api.CellLayout;
import com.gwsheridan.worldarchitect.api.CellLayout.Cell;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CellLayoutTest {
    @Test void parsedCoordinatesCannotOverflowTheTerritoryDistance(){assertThrows(IllegalArgumentException.class,()->Cell.parse("-2147483648,0"));assertEquals(new Cell(1,-2),Cell.parse(" 1, -2 "));}
    @Test void minecraftChunksAndNegativeCoordinates(){var grid=new CellLayout(CellLayout.Shape.SQUARE,16,0,0);assertEquals(new Cell(0,0),grid.at(0,15));assertEquals(new Cell(-1,-1),grid.at(-1,-16));assertEquals(new Cell(-2,0),grid.at(-17,0));assertEquals(1,grid.bounds(Cell.ORIGIN).chunks());}
    @Test void hexCentresAndSixReciprocalNeighbours(){for(var shape:new CellLayout.Shape[]{CellLayout.Shape.POINTY_HEX,CellLayout.Shape.FLAT_HEX}){var grid=new CellLayout(shape,32,2,1);for(int q=-30;q<=30;q++)for(int r=-30;r<=30;r++){var cell=new Cell(q,r);var p=grid.centre(cell);assertEquals(cell,grid.atPoint(p.x(),p.z()));assertEquals(6,grid.neighbours(cell).size());for(var adjacent:grid.neighbours(cell))assertTrue(grid.neighbours(adjacent).contains(cell));}}}
    @Test void hexPartitionHasNoHolesAndGapsAreExplicit(){for(var shape:CellLayout.Shape.values()){var grid=new CellLayout(shape,16,0,0);for(int x=-100;x<=100;x++)for(int z=-100;z<=100;z++){var cell=grid.at(x,z);assertTrue(grid.contains(cell,x,z),shape+" "+x+","+z);var b=grid.bounds(cell);assertTrue(x>=b.minX()&&x<b.maxX()&&z>=b.minZ()&&z<b.maxZ());}}var gap=new CellLayout(CellLayout.Shape.SQUARE,16,2,1);assertFalse(gap.contains(Cell.ORIGIN,0,0));assertTrue(gap.contains(Cell.ORIGIN,8,8));assertTrue(gap.rim(Cell.ORIGIN,2,8));}
    @Test void ringsAreBoundedAndDeterministic(){var grid=new CellLayout(CellLayout.Shape.POINTY_HEX,64,0,0);assertEquals(7,grid.within(1).size());assertEquals(19,grid.within(2).size());assertEquals(Cell.ORIGIN,grid.within(2).getFirst());assertThrows(IllegalArgumentException.class,()->grid.within(16));assertTrue(new CellLayout(CellLayout.Shape.SQUARE,17,0,0).validate().error().isPresent());}
}
