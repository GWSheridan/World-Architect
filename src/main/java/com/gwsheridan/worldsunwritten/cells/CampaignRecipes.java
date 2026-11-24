package com.gwsheridan.worldsunwritten.cells;

import com.gwsheridan.worldsunwritten.api.CellLayout;
import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.config.*;
import com.gwsheridan.worldsunwritten.modpack.*;
import com.gwsheridan.worldsunwritten.structure.*;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.dimension.LevelStem;

import java.io.IOException;
import java.util.*;

/**
 * Complete original campaigns built through the same immutable authoring models as custom projects.
 */
public final class CampaignRecipes {
    public static final List<String> NAMES = List.of("Hex Frontier", "One Chunk to the End");

    private CampaignRecipes() {}

    public static PresetStudio.Draft apply(
            PresetStudio.Draft source, String name, RegistryAccess registries) throws IOException {
        if (!NAMES.contains(name)) throw new IOException("Unknown campaign starter.");
        var checked = PresetStudio.validate(source, registries);
        var dimensions = checked.preset().createWorldDimensions().dimensions();
        var ow = dimensions.get(LevelStem.OVERWORLD);
        var end = dimensions.get(LevelStem.END);
        if (ow == null || end == null)
            throw new IOException("Campaigns require the Overworld and native End.");
        var result =
                ModpackProject.dimension(
                        source,
                        LevelStem.OVERWORLD,
                        new LevelStem(
                                ow.type(),
                                new CellGenerator(
                                        new DimensionProfile(
                                                ow.type().value().minY(),
                                                ow.type().value().height(),
                                                Optional.empty()),
                                        overworld(name, registries))),
                        registries);
        var dimension =
                new DimensionProfile(
                                end.type().value().minY(),
                                end.type().value().height(),
                                Optional.empty())
                        .withEndProgression(Optional.of(EndProgression.vanillaArena(registries)));
        return ModpackProject.dimension(
                result,
                LevelStem.END,
                new LevelStem(end.type(), new CellGenerator(dimension, end(registries))),
                registries);
    }

    public static CellWorldProfile overworld(String name, RegistryAccess registries)
            throws IOException {
        boolean hex = name.equals("Hex Frontier");
        var base =
                CellRecipes.create(
                        hex ? "Resource Archipelago" : "Expanding One Chunk", registries);
        var tiles = new ArrayList<CellWorldProfile.Tile>();
        for (var tile : base.tiles()) {
            var content = tile.content();
            if (!hex)
                content =
                        content.withOres(
                                concat(
                                        content.ores(),
                                        new ContentProfile.Ore(
                                                Blocks.DIAMOND_ORE.defaultBlockState(),
                                                Optional.of(Blocks.STONE.defaultBlockState()),
                                                4,
                                                4,
                                                -48,
                                                16,
                                                List.of())));
            var template = tile.template().map(CampaignRecipes::ports);
            if (tile.id().equals(base.start()) && template.isPresent())
                template = Optional.of(supplies(template.get(), registries));
            tiles.add(
                    new CellWorldProfile.Tile(
                            tile.id(),
                            tile.title(),
                            tile.weight(),
                            tile.biome(),
                            tile.kind(),
                            tile.terrain(),
                            content,
                            template));
        }
        var start =
                tiles.stream().filter(t -> t.id().equals(base.start())).findFirst().orElseThrow();
        var portal = portalRoom();
        tiles.add(
                new CellWorldProfile.Tile(
                        "portal",
                        "End portal ruins",
                        0,
                        start.biome(),
                        start.kind(),
                        start.terrain(),
                        start.content(),
                        Optional.of(portal)));
        var ruin = com.gwsheridan.worldsunwritten.structure.CellSites.starterRuin(start.biome());
        if (hex) {
            var resource = tiles.getFirst();
            var diamonds = resource.template().orElseThrow();
            diamonds =
                    new IslandBuild(
                            diamonds.size(),
                            diamonds.markers(),
                            diamonds.spawnMarker(),
                            diamonds.cells().stream()
                                    .map(
                                            c ->
                                                    c.state().is(Blocks.COAL_ORE)
                                                            ? new IslandBuild.Cell(
                                                                    c.pos(),
                                                                    Blocks.DIAMOND_ORE
                                                                            .defaultBlockState(),
                                                                    c.data())
                                                            : c)
                                    .toList());
            tiles.add(
                    new CellWorldProfile.Tile(
                            "diamonds",
                            "Diamond island",
                            0,
                            resource.biome(),
                            resource.kind(),
                            resource.terrain(),
                            resource.content(),
                            Optional.of(diamonds)));
            tiles.add(
                    new CellWorldProfile.Tile(
                            "ruins",
                            "Adventure ruins",
                            0,
                            resource.biome(),
                            resource.kind(),
                            resource.terrain(),
                            resource.content(),
                            resource.template()));
        }
        var portalCell = hex ? new Cell(0, 2) : new Cell(3, 0);
        var goals =
                List.of(
                        goal(
                                "wood",
                                "Begin your world",
                                GrowthRules.Trigger.ADVANCEMENT,
                                "minecraft:story/root",
                                List.of(),
                                2,
                                List.of(new Cell(1, 0))),
                        goal(
                                "stone",
                                "Gather stone",
                                GrowthRules.Trigger.ADVANCEMENT,
                                "minecraft:story/mine_stone",
                                List.of("wood"),
                                2,
                                List.of(hex ? new Cell(0, 1) : new Cell(2, 0))),
                        goal(
                                "delivery",
                                "Supply timber",
                                GrowthRules.Trigger.SIGNAL,
                                "worldsunwritten:delivery/delivery",
                                List.of("wood"),
                                2,
                                List.of(hex ? new Cell(-1, 0) : new Cell(-1, 0))),
                        goal(
                                "iron",
                                "Work with iron",
                                GrowthRules.Trigger.ADVANCEMENT,
                                "minecraft:story/smelt_iron",
                                List.of("stone"),
                                3,
                                hex ? List.of(new Cell(2, 0), new Cell(-2, 0)) : List.of()),
                        goal(
                                "nether",
                                "Find the portal ruins",
                                GrowthRules.Trigger.ADVANCEMENT,
                                "minecraft:story/enter_the_nether",
                                List.of("iron"),
                                3,
                                List.of(portalCell)),
                        goal(
                                "end",
                                "Enter the End",
                                GrowthRules.Trigger.ADVANCEMENT,
                                "minecraft:story/enter_the_end",
                                List.of("nether"),
                                3,
                                List.of()));
        var layout = hex ? new CellLayout(CellLayout.Shape.POINTY_HEX, 32, 6, 0) : base.layout();
        var assigned = new ArrayList<CellWorldProfile.Assignment>();
        assigned.add(new CellWorldProfile.Assignment(portalCell, "portal"));
        if (hex) {
            assigned.add(new CellWorldProfile.Assignment(new Cell(1, 0), "iron"));
            assigned.add(new CellWorldProfile.Assignment(new Cell(2, 0), "ruins"));
            assigned.add(new CellWorldProfile.Assignment(new Cell(-2, 0), "diamonds"));
        }
        var links = new ArrayList<CellCampaign.Connection>();
        if (hex)
            for (var cell : layout.within(2))
                for (var neighbour : layout.neighbours(cell))
                    if (layout.distance(neighbour) <= 2 && cell.compareTo(neighbour) < 0) {
                        if ((cell.equals(new Cell(2, 0)) || neighbour.equals(new Cell(2, 0)))
                                && !(cell.equals(new Cell(1, 0))
                                        && neighbour.equals(new Cell(2, 0)))) continue;
                        if ((cell.equals(portalCell) || neighbour.equals(portalCell))
                                && !(cell.equals(new Cell(0, 1)) && neighbour.equals(portalCell)))
                            continue;
                        String
                                from =
                                        portName(
                                                new Cell(
                                                        neighbour.q() - cell.q(),
                                                        neighbour.r() - cell.r())),
                                to =
                                        portName(
                                                new Cell(
                                                        cell.q() - neighbour.q(),
                                                        cell.r() - neighbour.r()));
                        if (neighbour.equals(new Cell(2, 0))) to = "root_west";
                        links.add(
                                new CellCampaign.Connection(
                                        "route_" + links.size(),
                                        cell,
                                        neighbour,
                                        "path",
                                        71,
                                        1,
                                        Blocks.OAK_PLANKS.defaultBlockState(),
                                        from,
                                        to));
                    }
        var campaign =
                new CellCampaign(
                        List.of(
                                new CellCampaign.Group(
                                        "portal_route", List.of(new Cell(0, 1), portalCell))),
                        Map.of("portal", 4),
                        List.of(
                                new CellCampaign.Delivery(
                                        "delivery", Identifier.parse("minecraft:oak_log"), 8)),
                        links,
                        hex ? List.of(new CellCampaign.Site("ruins", ruin, 100)) : List.of());
        if (!hex)
            campaign =
                    campaign.withGroups(
                            List.of(
                                    new CellCampaign.Group(
                                            "portal_route",
                                            List.of(new Cell(1, 0), new Cell(2, 0), portalCell))));
        return new CellWorldProfile(
                        layout,
                        base.salt(),
                        base.start(),
                        tiles,
                        assigned,
                        base.border(),
                        false,
                        new GrowthRules(
                                GrowthRules.Scope.SHARED, hex ? 2 : 6, 64, 4096, true, goals),
                        Optional.of(campaign))
                .validate()
                .getOrThrow(IOException::new);
    }

    public static CellWorldProfile end(RegistryAccess registries) throws IOException {
        var biome = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.END_HIGHLANDS);
        var terrain =
                new LayeredProfile(
                        Blocks.END_STONE.defaultBlockState(),
                        List.of(new LayeredProfile.Layer(Blocks.END_STONE.defaultBlockState(), 4)),
                        new TerrainShape(64, 0),
                        List.of());
        var tile =
                new CellWorldProfile.Tile(
                        "end_stone",
                        "End stone",
                        1,
                        biome,
                        CellWorldProfile.Kind.TERRAIN,
                        terrain,
                        ContentProfile.EMPTY,
                        Optional.empty());
        var goals =
                List.of(
                        goal(
                                "arrival",
                                "Explore the outer End",
                                GrowthRules.Trigger.ADVANCEMENT,
                                "minecraft:story/enter_the_end",
                                List.of(),
                                2,
                                List.of()),
                        goal(
                                "dragon",
                                "Defeat the dragon",
                                GrowthRules.Trigger.SIGNAL,
                                CellWorlds.DRAGON_DEFEATED.toString(),
                                List.of(),
                                8,
                                List.of()));
        return new CellWorldProfile(
                        new CellLayout(CellLayout.Shape.SQUARE, 64, 4, 1),
                        0,
                        "end_stone",
                        List.of(tile),
                        List.of(),
                        Blocks.PURPUR_BLOCK.defaultBlockState(),
                        false,
                        new GrowthRules(GrowthRules.Scope.SHARED, 12, 64, 4096, true, goals),
                        Optional.of(CellCampaign.EMPTY))
                .validate()
                .getOrThrow(IOException::new);
    }

    private static GrowthRules.Goal goal(
            String id,
            String title,
            GrowthRules.Trigger trigger,
            String source,
            List<String> requires,
            int credits,
            List<Cell> cells) {
        return new GrowthRules.Goal(
                id, title, trigger, Identifier.parse(source), requires, false, cells, credits);
    }

    private static <T> List<T> concat(List<T> values, T value) {
        var result = new ArrayList<>(values);
        result.add(value);
        return result;
    }

    private static String portName(Cell direction) {
        if (direction.q() == 1 && direction.r() == 0) return "east";
        if (direction.q() == -1 && direction.r() == 0) return "west";
        if (direction.r() == -1) return direction.q() == 1 ? "ne" : "nw";
        return direction.q() == -1 ? "sw" : "se";
    }

    private static IslandBuild ports(IslandBuild build) {
        var markers = new TreeMap<>(build.markers());
        for (var entry :
                Map.of(
                                "east",
                                new BlockPos(5, 4, 3),
                                "west",
                                new BlockPos(3, 4, 3),
                                "ne",
                                new BlockPos(5, 4, 2),
                                "nw",
                                new BlockPos(3, 4, 2),
                                "sw",
                                new BlockPos(3, 4, 6),
                                "se",
                                new BlockPos(5, 4, 6))
                        .entrySet()) markers.put(entry.getKey(), entry.getValue());
        return new IslandBuild(build.size(), markers, build.spawnMarker(), build.cells())
                .validate()
                .getOrThrow();
    }

    private static IslandBuild portalRoom() {
        var cells = new ArrayList<IslandBuild.Cell>();
        for (int z = 0; z < 9; z++)
            for (int x = 0; x < 9; x++)
                cells.add(
                        new IslandBuild.Cell(
                                new BlockPos(x, 0, z),
                                Blocks.STONE_BRICKS.defaultBlockState(),
                                Optional.empty()));
        for (int i = 3; i <= 5; i++) {
            cells.add(frame(i, 2, Direction.SOUTH));
            cells.add(frame(i, 6, Direction.NORTH));
            cells.add(frame(2, i, Direction.EAST));
            cells.add(frame(6, i, Direction.WEST));
        }
        return new IslandBuild(
                        new BlockPos(9, 5, 9),
                        Map.of(
                                "spawn",
                                new BlockPos(1, 1, 1),
                                "east",
                                new BlockPos(7, 1, 4),
                                "west",
                                new BlockPos(1, 1, 4),
                                "ne",
                                new BlockPos(7, 1, 1),
                                "nw",
                                new BlockPos(1, 1, 1),
                                "sw",
                                new BlockPos(1, 1, 7),
                                "se",
                                new BlockPos(7, 1, 7)),
                        "spawn",
                        cells)
                .validate()
                .getOrThrow();
    }

    private static IslandBuild.Cell frame(int x, int z, Direction facing) {
        return new IslandBuild.Cell(
                new BlockPos(x, 1, z),
                Blocks.END_PORTAL_FRAME
                        .defaultBlockState()
                        .setValue(EndPortalFrameBlock.FACING, facing)
                        .setValue(EndPortalFrameBlock.HAS_EYE, false),
                Optional.empty());
    }

    private static IslandBuild supplies(IslandBuild build, RegistryAccess registries) {
        var cells = new ArrayList<IslandBuild.Cell>();
        for (var cell : build.cells()) {
            var data = cell.data();
            if (cell.state().is(Blocks.CHEST) && data.isPresent()) {
                var tag = data.get();
                var items = tag.getListOrEmpty("Items").copy();
                int slot = 7;
                for (var stack :
                        List.of(
                                new net.minecraft.world.item.ItemStack(
                                        net.minecraft.world.item.Items.OBSIDIAN, 10),
                                new net.minecraft.world.item.ItemStack(
                                        net.minecraft.world.item.Items.FLINT_AND_STEEL))) {
                    var item =
                            (net.minecraft.nbt.CompoundTag)
                                    net.minecraft.world.item.ItemStack.CODEC
                                            .encodeStart(
                                                    RegistryOps.create(
                                                            net.minecraft.nbt.NbtOps.INSTANCE,
                                                            registries),
                                                    stack)
                                            .getOrThrow();
                    item.putByte("Slot", (byte) slot++);
                    items.add(item);
                }
                tag.put("Items", items);
                data = Optional.of(tag);
            }
            cells.add(new IslandBuild.Cell(cell.pos(), cell.state(), data));
        }
        return new IslandBuild(build.size(), build.markers(), build.spawnMarker(), cells)
                .validate()
                .getOrThrow();
    }
}
