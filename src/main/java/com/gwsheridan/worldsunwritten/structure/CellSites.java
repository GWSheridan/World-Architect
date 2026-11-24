package com.gwsheridan.worldsunwritten.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.io.IOException;
import java.util.*;

/**
 * Uses the production modular planner, then applies the stricter island/cell footprint contract.
 */
public final class CellSites {
    private CellSites() {}

    public static void validate(
            Structure definition, net.minecraft.core.HolderLookup.Provider registries)
            throws IOException {
        if (!(definition instanceof ModularStructure assembly))
            throw new IOException("A cell site requires an embedded modular assembly.");
        assembly.validateCellSite();
        assembly.validateTemplates(registries);
        for (long seed = 0; seed < 8; seed++) {
            var sample = plan(assembly, seed);
            if (sample.isPresent()) sample.get().checked(registries);
        }
    }

    /** Small original two-room pool used by the editable campaign starters. */
    public static ModularStructure starterRuin(
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome)
            throws IOException {
        var modules = new ArrayList<ModularStructure.Module>();
        for (String name : List.of("courtyard", "room")) {
            var cells = new ArrayList<NativeTemplate.Cell>();
            for (int z = 0; z < 5; z++)
                for (int x = 0; x < 5; x++)
                    cells.add(
                            new NativeTemplate.Cell(
                                    new BlockPos(x, 3, z),
                                    (name.equals("room")
                                                    ? net.minecraft.world.level.block.Blocks
                                                            .MOSSY_STONE_BRICKS
                                                    : net.minecraft.world.level.block.Blocks
                                                            .STONE_BRICKS)
                                            .defaultBlockState(),
                                    null));
            cells.add(
                    new NativeTemplate.Cell(
                            new BlockPos(4, 4, 4),
                            net.minecraft.world.level.block.Blocks.CHISELED_STONE_BRICKS
                                    .defaultBlockState(),
                            null));
            var tag = new CompoundTag();
            tag.putString("id", "minecraft:chest");
            tag.putString("CustomName", "Ruin cache");
            cells.add(
                    new NativeTemplate.Cell(
                            new BlockPos(0, 4, 4),
                            net.minecraft.world.level.block.Blocks.CHEST
                                    .defaultBlockState()
                                    .setValue(
                                            net.minecraft.world.level.block.ChestBlock.FACING,
                                            net.minecraft.core.Direction.EAST),
                            tag));
            var entry =
                    LibraryEntry.read(
                                    new NativeTemplate(new BlockPos(5, 7, 5), cells),
                                    new CompoundTag(),
                                    "Ruin " + name,
                                    "Campaigns")
                            .withMarker(
                                    "entry",
                                    new LibraryEntry.Marker(
                                            new BlockPos(2, 4, 2),
                                            net.minecraft.core.Direction.NORTH))
                            .withMarker(
                                    "west",
                                    new LibraryEntry.Marker(
                                            new BlockPos(0, 4, 2),
                                            net.minecraft.core.Direction.WEST))
                            .withMarker(
                                    "east",
                                    new LibraryEntry.Marker(
                                            new BlockPos(4, 4, 2),
                                            net.minecraft.core.Direction.EAST));
            modules.add(new ModularStructure.Module(name, BlockPos.ZERO, entry));
        }
        var pool =
                new ProceduralPool(
                        "courtyard",
                        2,
                        32,
                        194723,
                        List.of(
                                new ProceduralPool.Port(
                                        "courtyard",
                                        "east",
                                        new BlockPos(4, 4, 2),
                                        net.minecraft.core.Direction.EAST,
                                        "rooms",
                                        "door"),
                                new ProceduralPool.Port(
                                        "room",
                                        "west",
                                        new BlockPos(0, 4, 2),
                                        net.minecraft.core.Direction.WEST,
                                        "rooms",
                                        "door")),
                        List.of(new ProceduralPool.Choice("rooms", "room", 1, true)));
        var settings =
                new Structure.StructureSettings(
                        net.minecraft.core.HolderSet.direct(biome),
                        Map.of(),
                        net.minecraft.world.level.levelgen.GenerationStep.Decoration
                                .SURFACE_STRUCTURES,
                        net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE);
        return new ModularStructure(settings, modules, 0, 4, false, Optional.of(pool));
    }

    public record Sample(Optional<IslandBuild> build, String reason) {}

    private static final class Rejected extends IOException {
        Rejected(String reason) {
            super(reason);
        }
    }

    public static Sample inspect(Structure definition, long seed) throws IOException {
        try {
            return new Sample(
                    Optional.of(flatten(definition, seed)),
                    "Complete assembly fits the 64-block site limit.");
        } catch (Rejected error) {
            return new Sample(Optional.empty(), error.getMessage());
        }
    }

    public static Optional<IslandBuild> plan(Structure definition, long seed) throws IOException {
        return inspect(definition, seed).build();
    }

    private static IslandBuild flatten(Structure definition, long seed) throws IOException {
        if (!(definition instanceof ModularStructure assembly))
            throw new IOException("Only embedded modular assemblies can generate cell sites.");
        assembly.validateCellSite();
        var layout = assembly.previewLayout(seed);
        if (layout.isEmpty())
            throw new Rejected(
                    "Assembly planner rejected this seed: "
                            + String.join("; ", assembly.previewReport(seed).reasons()));
        int maxX = 1, maxY = 3, maxZ = 1;
        var cells = new LinkedHashMap<BlockPos, IslandBuild.Cell>();
        var markers = new TreeMap<String, BlockPos>();
        for (var module : layout.get()) {
            var template = module.entry().template();
            maxX = Math.max(maxX, module.offset().getX() + template.size().getX());
            maxY = Math.max(maxY, module.offset().getY() + template.size().getY());
            maxZ = Math.max(maxZ, module.offset().getZ() + template.size().getZ());
            if (Math.max(maxX, Math.max(maxY, maxZ)) > IslandBuild.MAX_SIDE)
                throw new Rejected("The complete assembly exceeds 64 blocks on one axis.");
            for (var cell : template.blocks()) {
                if (cell.state().isAir()) continue;
                var p = module.offset().offset(cell.pos());
                var data = IslandBuild.importMetadata(cell.metadata(), false);
                if (data != null && cell.metadata() != null && cell.metadata().contains("id"))
                    data.putString("id", cell.metadata().getStringOr("id", ""));
                if (cells.putIfAbsent(
                                p, new IslandBuild.Cell(p, cell.state(), Optional.ofNullable(data)))
                        != null) throw new IOException("A cell site contains overlapping modules.");
                if (cells.size() > IslandBuild.MAX_CELLS)
                    throw new Rejected("The assembly exceeds 65,536 occupied positions.");
            }
            for (var marker : module.entry().markers().entrySet()) {
                String name = (module.name() + "_" + marker.getKey());
                if (name.length() > 32
                        || markers.size() >= 32
                        || markers.putIfAbsent(
                                        name, module.offset().offset(marker.getValue().pos()))
                                != null)
                    throw new IOException("Cell site marker names exceed the island limits.");
            }
        }
        if (cells.isEmpty()) throw new Rejected("The assembly has no occupied cells.");
        var size = new BlockPos(maxX, maxY, maxZ);
        IslandBuild result;
        if (markers.isEmpty())
            result = IslandAssets.withAutomaticSpawn(size, List.copyOf(cells.values()));
        else {
            String spawn =
                    markers.keySet().stream()
                            .filter(n -> n.endsWith("_spawn") || n.endsWith("_entry"))
                            .findFirst()
                            .orElse(markers.firstKey());
            result = new IslandBuild(size, markers, spawn, List.copyOf(cells.values()));
        }
        var rotation = assembly.cellSiteRotation(seed);
        if (rotation != net.minecraft.world.level.block.Rotation.NONE) {
            var transform =
                    new TemplatePlacement(
                            BlockPos.ZERO, rotation, net.minecraft.world.level.block.Mirror.NONE);
            var transformedMarkers = new TreeMap<String, BlockPos>();
            var previous = result;
            previous.markers()
                    .forEach(
                            (name, pos) ->
                                    transformedMarkers.put(
                                            name, transform.position(previous.size(), pos)));
            result =
                    new IslandBuild(
                            transform.size(previous.size()),
                            transformedMarkers,
                            previous.spawnMarker(),
                            previous.cells().stream()
                                    .map(
                                            c ->
                                                    new IslandBuild.Cell(
                                                            transform.position(
                                                                    previous.size(), c.pos()),
                                                            transform.state(c.state()),
                                                            c.data()))
                                    .toList());
        }
        if (result.validate().error().isPresent())
            throw new Rejected(result.validate().error().orElseThrow().message());
        return result;
    }
}
