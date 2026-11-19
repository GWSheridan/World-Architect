package com.gwsheridan.worldarchitect.worldgen;

import com.gwsheridan.worldarchitect.config.EndProgression;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EndProgressionTest {
    @Test void reservationIsChunkAlignedAtBothNegativeAndPositiveEdges() {
        assertTrue(EndProgression.contains(-256, -256));
        assertTrue(EndProgression.contains(255, 255));
        assertFalse(EndProgression.contains(-257, 0));
        assertFalse(EndProgression.contains(256, 0));
        assertFalse(EndProgression.contains(0, -257));
        assertFalse(EndProgression.contains(0, 256));
        assertTrue(EndProgression.intersects(-300, 0, -256, 10));
        assertTrue(EndProgression.intersects(255, 255, 400, 400));
        assertFalse(EndProgression.intersects(256, 0, 400, 40));
        assertFalse(EndProgression.intersects(-400, -400, -257, -257));
    }

    @Test void generationCannotSpillAcrossEitherSideOfTheReservation() {
        assertFalse(EndProgression.allowsGenerationWrite(256, 0, 255, 0));
        assertFalse(EndProgression.allowsGenerationWrite(255, 0, 256, 0));
        assertFalse(EndProgression.allowsGenerationWrite(-257, 0, -256, 0));
        assertFalse(EndProgression.allowsGenerationWrite(-256, 0, -257, 0));
        assertTrue(EndProgression.allowsGenerationWrite(0, 0, 255, 255));
        assertTrue(EndProgression.allowsGenerationWrite(256, 0, 512, 0));
    }

}
