package com.gwsheridan.worldsunwritten.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class NativeTemplateCoordinatesTest {
    @Test
    void embeddedJsonCoordinatesDecodeAfterNumericTagConversion() throws IOException {
        var input = JsonParser.parseString("{\"pos\":[0,16,31]}");
        var tag = CompoundTag.CODEC.parse(JsonOps.INSTANCE, input).getOrThrow();
        assertEquals(new BlockPos(0, 16, 31), NativeTemplate.readPosition(tag.getListOrEmpty("pos")));
    }

    @Test
    void coordinatesRejectFractionsAndOutOfRangeValues() {
        for (double value : new double[] {0.5, Double.NaN, (double) Integer.MAX_VALUE + 1}) {
            var coordinates = new ListTag();
            coordinates.add(DoubleTag.valueOf(value));
            coordinates.add(DoubleTag.valueOf(0));
            coordinates.add(DoubleTag.valueOf(0));
            assertThrows(IOException.class, () -> NativeTemplate.readPosition(coordinates));
        }
    }
}
