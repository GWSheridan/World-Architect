package com.gwsheridan.worldsunwritten.cells;

import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.config.TerrainMaterial;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.*;

/** Opt-in campaign definitions. Runtime progress belongs to the durable cell ledger. */
public record CellCampaign(
        List<Group> groups,
        Map<String, Integer> costs,
        List<Delivery> deliveries,
        List<Connection> connections,
        List<Site> sites,
        List<String> checkpoint) {
    public static final CellCampaign EMPTY = new CellCampaign(List.of(), Map.of(), List.of());

    public CellCampaign {
        groups = List.copyOf(groups);
        costs = Collections.unmodifiableMap(new TreeMap<>(costs));
        deliveries = List.copyOf(deliveries);
        connections = List.copyOf(connections);
        sites = List.copyOf(sites);
        checkpoint = checkpoint.stream().distinct().sorted().toList();
    }

    public CellCampaign(List<Group> groups, Map<String, Integer> costs, List<Delivery> deliveries) {
        this(groups, costs, deliveries, List.of(), List.of());
    }

    public CellCampaign(List<Group> groups, Map<String, Integer> costs, List<Delivery> deliveries,
            List<Connection> connections, List<Site> sites) {
        this(groups, costs, deliveries, connections, sites, List.of());
    }

    public static final Codec<CellCampaign> CODEC =
            RecordCodecBuilder.create(
                    b ->
                            b.group(
                                            Group.CODEC
                                                    .listOf(0, 32)
                                                    .optionalFieldOf("groups", List.of())
                                                    .forGetter(CellCampaign::groups),
                                            Codec.unboundedMap(Codec.STRING, Codec.intRange(1, 64))
                                                    .optionalFieldOf("costs", Map.of())
                                                    .forGetter(CellCampaign::costs),
                                            Delivery.CODEC
                                                    .listOf(0, 128)
                                                    .optionalFieldOf("deliveries", List.of())
                                                    .forGetter(CellCampaign::deliveries),
                                            Connection.CODEC
                                                    .listOf(0, 64)
                                                    .optionalFieldOf("connections", List.of())
                                                    .forGetter(CellCampaign::connections),
                                            Site.CODEC
                                                    .listOf(0, 16)
                                                    .optionalFieldOf("sites", List.of())
                                                    .forGetter(CellCampaign::sites),
                                            Codec.STRING.listOf(0, 128)
                                                    .optionalFieldOf("rehearsal_checkpoint", List.of())
                                                    .forGetter(CellCampaign::checkpoint))
                                    .apply(b, CellCampaign::new));

    public record Group(String id, List<Cell> cells) {
        public Group {
            cells = List.copyOf(cells);
        }

        public static final Codec<Group> CODEC =
                RecordCodecBuilder.create(
                        b ->
                                b.group(
                                                Codec.STRING.fieldOf("id").forGetter(Group::id),
                                                Cell.CODEC
                                                        .listOf(1, 1024)
                                                        .fieldOf("cells")
                                                        .forGetter(Group::cells))
                                        .apply(b, Group::new));
    }

    public record Delivery(String goal, Identifier item, int count) {
        public static final Codec<Delivery> CODEC =
                RecordCodecBuilder.create(
                        b ->
                                b.group(
                                                Codec.STRING
                                                        .fieldOf("goal")
                                                        .forGetter(Delivery::goal),
                                                Identifier.CODEC
                                                        .fieldOf("item")
                                                        .forGetter(Delivery::item),
                                                Codec.intRange(1, 4096)
                                                        .fieldOf("count")
                                                        .forGetter(Delivery::count))
                                        .apply(b, Delivery::new));

        public Identifier signal() {
            return Identifier.fromNamespaceAndPath("worldsunwritten", "delivery/" + goal);
        }
    }

    public record Connection(
            String id,
            Cell from,
            Cell to,
            String kind,
            int floor,
            int width,
            BlockState material,
            String fromPort,
            String toPort) {
        public static final Codec<Connection> CODEC =
                RecordCodecBuilder.create(
                        b ->
                                b.group(
                                                Codec.STRING
                                                        .fieldOf("id")
                                                        .forGetter(Connection::id),
                                                Cell.CODEC
                                                        .fieldOf("from")
                                                        .forGetter(Connection::from),
                                                Cell.CODEC.fieldOf("to").forGetter(Connection::to),
                                                Codec.STRING
                                                        .optionalFieldOf("kind", "bridge")
                                                        .forGetter(Connection::kind),
                                                Codec.INT
                                                        .optionalFieldOf("floor_y", 64)
                                                        .forGetter(Connection::floor),
                                                Codec.intRange(1, 5)
                                                        .optionalFieldOf("width", 3)
                                                        .forGetter(Connection::width),
                                                TerrainMaterial.CODEC
                                                        .fieldOf("material")
                                                        .forGetter(Connection::material),
                                                Codec.STRING
                                                        .optionalFieldOf("from_port", "")
                                                        .forGetter(Connection::fromPort),
                                                Codec.STRING
                                                        .optionalFieldOf("to_port", "")
                                                        .forGetter(Connection::toPort))
                                        .apply(b, Connection::new));
    }

    public record Site(String type, Structure assembly, int chance) {
        public static final Codec<Site> CODEC =
                RecordCodecBuilder.create(
                        b ->
                                b.group(
                                                Codec.STRING.fieldOf("type").forGetter(Site::type),
                                                Structure.DIRECT_CODEC
                                                        .fieldOf("assembly")
                                                        .forGetter(Site::assembly),
                                                Codec.intRange(1, 100)
                                                        .optionalFieldOf("chance", 100)
                                                        .forGetter(Site::chance))
                                        .apply(b, Site::new));
    }

    public Optional<String> problem(CellWorldProfile profile) {
        if (groups.size() > 32 || costs.size() > 16 || deliveries.size() > 128)
            return Optional.of("Campaign definitions exceed the saved limits.");
        if (!checkpoint.isEmpty() && (!profile.progressive() || checkpoint.size() > 128
                || !profile.growth().goals().stream().map(GrowthRules.Goal::id).toList().containsAll(checkpoint)))
            return Optional.of("A rehearsal checkpoint must use existing progressive objectives.");
        var names = new HashSet<String>();
        for (var group : groups) {
            if (!group.id().matches("[a-z][a-z0-9_-]{0,39}")
                    || !names.add(group.id())
                    || group.cells().isEmpty()
                    || group.cells().size() > 1024
                    || new HashSet<>(group.cells()).size() != group.cells().size())
                return Optional.of("Use distinct named groups with unique cell coordinates.");
            if (profile.progressive()
                    && group.cells().stream()
                            .anyMatch(
                                    c -> profile.layout().distance(c) > profile.growth().radius()))
                return Optional.of("A group extends outside its territory.");
        }
        for (var cost : costs.entrySet())
            if (profile.tiles().stream().noneMatch(t -> t.id().equals(cost.getKey()))
                    || cost.getValue() < 1
                    || cost.getValue() > 64)
                return Optional.of(
                        "Expansion costs must name existing cell types and be 1–64 credits.");
        names.clear();
        for (var delivery : deliveries) {
            var goal =
                    profile.growth().goals().stream()
                            .filter(g -> g.id().equals(delivery.goal()))
                            .findFirst();
            if (!names.add(delivery.goal())
                    || delivery.count() < 1
                    || delivery.count() > 4096
                    || goal.isEmpty()
                    || goal.get().trigger() != GrowthRules.Trigger.SIGNAL
                    || !goal.get().source().equals(delivery.signal()))
                return Optional.of(
                        "Each delivery must reference its own signal objective and a count of"
                                + " 1–4096.");
        }
        if (!profile.progressive() && (!costs.isEmpty() || !deliveries.isEmpty()))
            return Optional.of("Costs and deliveries require a progressive world.");
        if (connections.size() > 64 || sites.size() > 16)
            return Optional.of("At most 64 connections and 16 sites can be embedded.");
        if (!profile.progressive() && !connections.isEmpty())
            return Optional.of("Connections require progressive cells and reserved void gaps.");
        names.clear();
        var edges = new HashSet<String>();
        for (var link : connections) {
            String edge =
                    link.from().compareTo(link.to()) < 0
                            ? link.from().key() + "/" + link.to().key()
                            : link.to().key() + "/" + link.from().key();
            if (!link.id().matches("[a-z][a-z0-9_-]{0,39}")
                    || !names.add(link.id())
                    || !edges.add(edge)
                    || !profile.layout().neighbours(link.from()).contains(link.to())
                    || !Set.of("bridge", "path", "tunnel").contains(link.kind())
                    || link.width() < 1
                    || link.width() > 5
                    || link.width() % 2 == 0
                    || profile.layout().distance(link.from()) > profile.growth().radius()
                    || profile.layout().distance(link.to()) > profile.growth().radius()
                    || link.kind().equals("tunnel") && link.width() < 3
                    || !com.gwsheridan.worldsunwritten.config.IslandTemplate.safeFloor(
                            link.material(),
                            net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                            net.minecraft.core.BlockPos.ZERO)
                    || link.material().hasBlockEntity()
                    || !link.material().getFluidState().isEmpty()
                    || link.material().isAir()
                    || !link.fromPort().matches("(?:[a-z][a-z0-9_-]{0,31})?")
                    || !link.toPort().matches("(?:[a-z][a-z0-9_-]{0,31})?"))
                return Optional.of(
                        "Connections need distinct IDs and neighbouring cells, odd widths 1–5,"
                                + " solid materials and valid saved ports.");
        }
        names.clear();
        for (var site : sites)
            if (!names.add(site.type())
                    || profile.tiles().stream().noneMatch(t -> t.id().equals(site.type()))
                    || !(site.assembly()
                            instanceof com.gwsheridan.worldsunwritten.structure.ModularStructure)
                    || site.chance() < 1
                    || site.chance() > 100)
                return Optional.of(
                        "Each site needs a distinct existing cell type and a modular assembly.");
        return Optional.empty();
    }

    public int cost(CellWorldProfile profile, long seed, int territory, Cell cell) {
        return costs.getOrDefault(profile.tile(seed, territory, cell).id(), 1);
    }

    public CellCampaign withGroups(List<Group> value) {
        return new CellCampaign(value, costs, deliveries, connections, sites, checkpoint);
    }

    public CellCampaign withCosts(Map<String, Integer> value) {
        return new CellCampaign(groups, value, deliveries, connections, sites, checkpoint);
    }

    public CellCampaign withDeliveries(List<Delivery> value) {
        return new CellCampaign(groups, costs, value, connections, sites, checkpoint);
    }

    public CellCampaign withConnections(List<Connection> value) {
        return new CellCampaign(groups, costs, deliveries, value, sites, checkpoint);
    }

    public CellCampaign withSites(List<Site> value) {
        return new CellCampaign(groups, costs, deliveries, connections, value, checkpoint);
    }

    public CellCampaign withCheckpoint(List<String> objectives) {
        return new CellCampaign(groups, costs, deliveries, connections, sites, objectives);
    }

    public static String canonical(com.google.gson.JsonElement value) {
        return ordered(value).toString();
    }

    private static com.google.gson.JsonElement ordered(com.google.gson.JsonElement value) {
        if (value.isJsonObject()) {
            var result = new com.google.gson.JsonObject();
            new TreeMap<>(value.getAsJsonObject().asMap())
                    .forEach((key, child) -> result.add(key, ordered(child)));
            return result;
        }
        if (value.isJsonArray()) {
            var result = new com.google.gson.JsonArray();
            value.getAsJsonArray().forEach(child -> result.add(ordered(child)));
            return result;
        }
        return value.deepCopy();
    }
}
