package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.structure.PlacementPreviewPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;

public final class ClientStructurePreview {
    private static CompoundTag ghost;
    private static int remaining;
    private ClientStructurePreview() {}
    public static void receive(PlacementPreviewPayload payload) {
            clear(); Minecraft.getInstance().gui.setScreen(new StructurePlacementScreen(payload.visual()));
    }
    static void showGhost(CompoundTag data) {
        ghost = data.copy(); remaining = 200;
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Ghost lasts 10 seconds. Reopen with /worldsunwritten structure stage " + data.getStringOr("id", "") + " "
                        + java.util.Arrays.stream(data.getIntArray("origin").orElse(new int[3])).mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(" "))
                        + " " + data.getStringOr("rotation", "none") + " " + data.getStringOr("mirror", "none")));
    }
    static void clear() { ghost = null; remaining = 0; }
    public static void tick() {
        if (ghost == null) return;
        var mc = Minecraft.getInstance();
        if (--remaining <= 0 || mc.level == null || !mc.level.dimension().identifier().toString().equals(ghost.getStringOr("dimension", ""))) { clear(); return; }
        if (remaining % 5 != 0) return;
        int[] cells = ghost.getIntArray("cells").orElse(new int[0]), origin = ghost.getIntArray("origin").orElse(new int[3]);
        var particle = new DustParticleOptions(ghost.getBooleanOr("valid", false) ? 0x66CCFF : 0xFF7755, .65f);
        int stride = Math.max(1, (cells.length / 2 + 511) / 512);
        for (int i = 0; i < cells.length / 2; i += stride) {
            int p = cells[i*2]; mc.level.addParticle(particle, origin[0]+(p&31)+.5, origin[1]+(p>>5&31)+.5, origin[2]+(p>>10&31)+.5, 0,0,0);
        }
        for (var value : ghost.getListOrEmpty("markers")) if (value instanceof CompoundTag marker) {
            int[] p = marker.getIntArray("pos").orElse(new int[3]);
            mc.level.addParticle(new DustParticleOptions(0xFFD560, 1.2f), origin[0]+p[0]+.5, origin[1]+p[1]+.5, origin[2]+p[2]+.5, 0,0,0);
        }
    }
}
