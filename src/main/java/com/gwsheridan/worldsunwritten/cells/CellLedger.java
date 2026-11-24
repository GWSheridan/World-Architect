package com.gwsheridan.worldsunwritten.cells;

import com.google.gson.*;
import com.gwsheridan.worldsunwritten.api.CellLayout;
import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator.Key;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Server-thread journal. Every reward and ownership decision is committed before it becomes
 * visible.
 */
public final class CellLedger {
    public enum Status {
        QUEUED,
        APPLYING,
        READY,
        CONFLICT
    }

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final CellLayout layout;
    private final GrowthRules growth;
    private final int format;
    private final Set<Cell> reserved;
    private final java.util.function.ToIntBiFunction<Integer, Cell> cost;
    private JsonObject data;
    private Map<Key, Status> indexed = Map.of();
    private Map<Integer, Map<Cell, Status>> territories = Map.of();
    private List<Key> pending = List.of();
    private Set<Key> ready = Set.of();

    public CellLedger(Path file, CellWorldProfile profile, String pin, boolean fresh)
            throws IOException {
        this(file, profile, pin, fresh, 0L);
    }

    public CellLedger(Path file, CellWorldProfile profile, String pin, boolean fresh, long seed)
            throws IOException {
        this(file, profile, pin, fresh, seed, Set.of());
    }

    public CellLedger(
            Path file,
            CellWorldProfile profile,
            String pin,
            boolean fresh,
            long seed,
            Set<Cell> reserved)
            throws IOException {
        this(
                file,
                profile.layout(),
                profile.growth(),
                pin,
                fresh,
                profile.version(),
                (slot, cell) ->
                        profile.campaign().map(c -> c.cost(profile, seed, slot, cell)).orElse(1),
                reserved);
        if (format == 2)
            for (var entry : data.getAsJsonObject("deliveries").entrySet()) {
                var claim = delivery(entry.getValue().getAsJsonObject());
                if (profile.campaign().orElseThrow().deliveries().stream()
                        .noneMatch(d -> d.goal().equals(claim.goal()))) {
                    throw new IOException("A saved delivery definition is missing.");
                }
            }
        for (var entry : connections().entrySet()) {
            var parts = entry.getKey().split(":", -1);
            var link =
                    profile.campaign().orElse(CellCampaign.EMPTY).connections().stream()
                            .filter(c -> parts.length == 2 && c.id().equals(parts[1]))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IOException(
                                                    "A saved connection definition is missing."));
            int slot = Integer.parseInt(parts[0]);
            if (entry.getValue().status() == Status.READY
                    && (status(new Key(slot, link.from())) != Status.READY
                            || status(new Key(slot, link.to())) != Status.READY))
                throw new IOException("A ready connection has an unavailable endpoint.");
        }
    }

    public CellLedger(Path file, CellLayout layout, GrowthRules growth, String pin, boolean fresh)
            throws IOException {
        this(file, layout, growth, pin, fresh, 1, (slot, cell) -> 1);
    }

    CellLedger(
            Path file,
            CellLayout layout,
            GrowthRules growth,
            String pin,
            boolean fresh,
            int format,
            java.util.function.ToIntBiFunction<Integer, Cell> cost)
            throws IOException {
        this(file, layout, growth, pin, fresh, format, cost, Set.of());
    }

    private CellLedger(
            Path file,
            CellLayout layout,
            GrowthRules growth,
            String pin,
            boolean fresh,
            int format,
            java.util.function.ToIntBiFunction<Integer, Cell> cost,
            Set<Cell> reserved)
            throws IOException {
        this.file = file;
        this.layout = layout;
        this.growth = growth;
        this.format = format;
        this.cost = cost;
        this.reserved = Set.copyOf(reserved);
        if (Files.notExists(file)) {
            if (!fresh)
                throw new IOException(
                        "The saved cell progression journal is missing. Restore it from the world"
                                + " backup.");
            var root = new JsonObject();
            root.addProperty("format", format);
            root.addProperty("generation_sha256", pin);
            root.add("owners", new JsonObject());
            root.add("regions", new JsonObject());
            root.add("cells", new JsonObject());
            if (format == 2) {
                root.add("deliveries", new JsonObject());
                root.add("connections", new JsonObject());
                root.add("reserved", array(reserved.stream().map(Cell::key).sorted().toList()));
                for (var cell : reserved)
                    root.getAsJsonObject("cells")
                            .addProperty(new Key(0, cell).id(), Status.READY.name());
            }
            root.getAsJsonObject("cells")
                    .addProperty(new Key(0, Cell.ORIGIN).id(), Status.READY.name());
            commit(root);
        } else {
            if (Files.size(file) > 32 * 1024 * 1024)
                throw new IOException("Cell journal exceeds its size limit.");
            try {
                data = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (data.get("format").getAsInt() != format
                        || !data.get("generation_sha256").getAsString().equals(pin))
                    throw new IllegalArgumentException("Generation pin or journal version changed");
                validate();
                index();
            } catch (RuntimeException error) {
                throw new IOException(
                        "Invalid cell progression journal. Restore the original journal: "
                                + error.getMessage(),
                        error);
            }
        }
    }

    private void validate() {
        if (format == 2) {
            if (!data.has("reserved")
                    || !strings(data.getAsJsonArray("reserved"))
                            .equals(new TreeSet<>(reserved.stream().map(Cell::key).toList())))
                throw new IllegalArgumentException("Native End reservations changed");
            for (var c : reserved)
                if (status(new Key(0, c)) != Status.READY)
                    throw new IllegalArgumentException("A permanent End arena cell is unavailable");
        }
        var occupied = new HashSet<Integer>();
        for (var entry : data.getAsJsonObject("owners").entrySet()) {
            int slot = entry.getValue().getAsInt();
            if (entry.getKey().length() > 100
                    || slot < 1
                    || slot > growth.territories()
                    || !occupied.add(slot))
                throw new IllegalArgumentException("Invalid territory ownership");
        }
        for (var entry : data.getAsJsonObject("cells").entrySet()) {
            var key = Key.parse(entry.getKey());
            if (key.territory() < 0
                    || key.territory() > growth.territories()
                    || layout.distance(key.cell()) > growth.radius())
                throw new IllegalArgumentException("Cell outside saved territory");
            Status.valueOf(entry.getValue().getAsString());
        }
        if (status(new Key(0, Cell.ORIGIN)) != Status.READY)
            throw new IllegalArgumentException("The original starting cell is missing");
        if (data.has("conflict_resume"))
            for (var entry : data.getAsJsonObject("conflict_resume").entrySet())
                if (status(Key.parse(entry.getKey())) != Status.CONFLICT
                        || !entry.getValue().getAsBoolean())
                    throw new IllegalArgumentException("Invalid conflict recovery state");
        for (var entry : data.getAsJsonObject("regions").entrySet()) {
            int slot = Integer.parseInt(entry.getKey());
            if (slot < 0 || slot > growth.territories())
                throw new IllegalArgumentException("Invalid progress region");
            var region = entry.getValue().getAsJsonObject();
            if (region.get("credits").getAsInt() < 0 || region.get("credits").getAsInt() > 8192)
                throw new IllegalArgumentException("Invalid expansion credits");
            var known = growth.goals().stream().map(GrowthRules.Goal::id).toList();
            for (var completed : strings(region.getAsJsonArray("completed")))
                if (!known.contains(completed))
                    throw new IllegalArgumentException("Unknown completed objective");
        }
        if (format == 2) {
            if (!data.has("deliveries") || !data.has("connections"))
                throw new IllegalArgumentException("Campaign journal sections are missing");
            var transactions = new HashSet<String>();
            for (var e : data.getAsJsonObject("deliveries").entrySet()) {
                var claim = delivery(e.getValue().getAsJsonObject());
                checkKey(new Key(claim.territory(), Cell.ORIGIN));
                UUID.fromString(claim.player());
                UUID.fromString(claim.transaction());
                if (!transactions.add(claim.transaction())
                        || !e.getKey().equals(claim.territory() + ":" + claim.goal())
                        || !claim.beforeInventory().matches("[a-f0-9]{64}")
                        || growth.goals().stream().noneMatch(g -> g.id().equals(claim.goal()))
                        || !Set.of("PREPARED", "PAID").contains(claim.state())
                        || claim.state().equals("PAID")
                                && !completed(claim.territory()).contains(claim.goal()))
                    throw new IllegalArgumentException("Invalid delivery claim");
            }
            for (var e : connections().entrySet()) {
                var parts = e.getKey().split(":", -1);
                if (parts.length != 2
                        || !parts[1].matches("[a-z][a-z0-9_-]{0,39}")
                        || !e.getValue().fingerprint().matches("[a-f0-9]{64}")
                        || e.getValue().problem().length() > 512
                        || e.getValue().status() != Status.CONFLICT
                                && (!e.getValue().problem().isEmpty() || e.getValue().resume()))
                    throw new IllegalArgumentException("Invalid saved connection");
                checkKey(new Key(Integer.parseInt(parts[0]), Cell.ORIGIN));
            }
        }
    }

    public Map<Key, Status> cells() {
        return indexed;
    }

    public Map<Cell, Status> territoryCells(int slot) {
        return territories.getOrDefault(slot, Map.of());
    }

    public Set<Key> ready() {
        return ready;
    }

    public List<Key> pending() {
        return pending;
    }

    private void index() {
        var all = new TreeMap<Key, Status>(Comparator.comparing(Key::id));
        var regions = new HashMap<Integer, Map<Cell, Status>>();
        var queue = new ArrayList<Key>();
        var active = new HashSet<Key>();
        for (var entry : data.getAsJsonObject("cells").entrySet()) {
            var key = Key.parse(entry.getKey());
            var status = Status.valueOf(entry.getValue().getAsString());
            all.put(key, status);
            regions.computeIfAbsent(key.territory(), ignored -> new HashMap<>())
                    .put(key.cell(), status);
            if (status == Status.READY) active.add(key);
            if (status == Status.QUEUED || status == Status.APPLYING) queue.add(key);
        }
        var saved = new HashMap<Integer, Map<Cell, Status>>();
        regions.forEach((slot, cells) -> saved.put(slot, Map.copyOf(cells)));
        indexed = Collections.unmodifiableMap(all);
        territories = Map.copyOf(saved);
        pending = List.copyOf(queue);
        ready = Set.copyOf(active);
    }

    public Status status(Key key) {
        var value = data.getAsJsonObject("cells").get(key.id());
        return value == null ? null : Status.valueOf(value.getAsString());
    }

    public int assigned(String owner) {
        var value = data.getAsJsonObject("owners").get(owner);
        return value == null ? 0 : value.getAsInt();
    }

    public int claim(String owner) throws IOException {
        if (owner.isBlank() || owner.length() > 100)
            throw new IllegalArgumentException("Invalid territory owner");
        int existing = assigned(owner);
        if (existing > 0) return existing;
        var used = new HashSet<Integer>();
        data.getAsJsonObject("owners").entrySet().forEach(e -> used.add(e.getValue().getAsInt()));
        cells().keySet().forEach(key -> used.add(key.territory()));
        int slot = 1;
        while (used.contains(slot)) slot++;
        if (slot > growth.territories())
            throw new IllegalStateException("All saved territories are assigned.");
        var draft = data.deepCopy();
        draft.getAsJsonObject("owners").addProperty(owner, slot);
        queue(draft, new Key(slot, Cell.ORIGIN));
        commit(draft);
        return slot;
    }

    public void reassign(String owner, int slot) throws IOException {
        if (owner.isBlank() || owner.length() > 100 || slot < 1 || slot > growth.territories())
            throw new IllegalArgumentException("Invalid territory reassignment");
        for (var entry : data.getAsJsonObject("owners").entrySet())
            if (!entry.getKey().equals(owner) && entry.getValue().getAsInt() == slot)
                throw new IllegalArgumentException("That territory already has an owner.");
        var draft = data.deepCopy();
        draft.getAsJsonObject("owners").addProperty(owner, slot);
        queue(draft, new Key(slot, Cell.ORIGIN));
        commit(draft);
    }

    public void transition(Key key, Status expected, Status next) throws IOException {
        if (status(key) != expected)
            throw new IllegalStateException("Cell journal transition changed");
        var draft = data.deepCopy();
        draft.getAsJsonObject("cells").addProperty(key.id(), next.name());
        if (!draft.has("conflict_resume")) draft.add("conflict_resume", new JsonObject());
        var resume = draft.getAsJsonObject("conflict_resume");
        if (next == Status.CONFLICT && expected == Status.APPLYING)
            resume.addProperty(key.id(), true);
        else if (next != Status.CONFLICT) resume.remove(key.id());
        if (!growth.chooseFrontier() && (expected == Status.CONFLICT || next == Status.READY))
            spendAutomatically(draft, key.territory());
        commit(draft);
    }

    public void retry(Key key) throws IOException {
        boolean resume =
                data.has("conflict_resume")
                        && data.getAsJsonObject("conflict_resume").has(key.id())
                        && data.getAsJsonObject("conflict_resume").get(key.id()).getAsBoolean();
        transition(key, Status.CONFLICT, resume ? Status.APPLYING : Status.QUEUED);
    }

    public void queue(Key key) throws IOException {
        checkKey(key);
        if (status(key) != null) return;
        var draft = data.deepCopy();
        queue(draft, key);
        commit(draft);
    }

    private void queue(JsonObject draft, Key key) {
        checkKey(key);
        var cells = draft.getAsJsonObject("cells");
        if (!cells.has(key.id())) cells.addProperty(key.id(), Status.QUEUED.name());
    }

    private void checkKey(Key key) {
        if (key.territory() < 0
                || key.territory() > growth.territories()
                || growth.scope() == GrowthRules.Scope.SHARED && key.territory() != 0
                || layout.distance(key.cell()) > growth.radius())
            throw new IllegalArgumentException("Cell is outside the saved territory.");
    }

    private JsonObject progress(int slot) {
        var regions = data.getAsJsonObject("regions");
        if (regions.has(Integer.toString(slot)))
            return regions.getAsJsonObject(Integer.toString(slot));
        var value = new JsonObject();
        value.addProperty("credits", 0);
        value.add("completed", new JsonArray());
        return value;
    }

    public Set<String> completed(int slot) {
        return strings(progress(slot).getAsJsonArray("completed"));
    }

    public int credits(int slot) {
        return progress(slot).get("credits").getAsInt();
    }

    public record DeliveryClaim(
            String transaction,
            String player,
            int territory,
            String goal,
            String beforeInventory,
            String state) {}

    private static DeliveryClaim delivery(JsonObject value) {
        return new DeliveryClaim(
                value.get("transaction").getAsString(),
                value.get("player").getAsString(),
                value.get("territory").getAsInt(),
                value.get("goal").getAsString(),
                value.get("before_inventory").getAsString(),
                value.get("state").getAsString());
    }

    public List<DeliveryClaim> deliveries(UUID player) {
        if (format != 2) return List.of();
        return data.getAsJsonObject("deliveries").entrySet().stream()
                .map(e -> delivery(e.getValue().getAsJsonObject()))
                .filter(d -> d.player().equals(player.toString()))
                .toList();
    }

    public List<DeliveryClaim> pendingDeliveries() {
        if (format != 2) return List.of();
        return data.getAsJsonObject("deliveries").entrySet().stream()
                .map(e -> delivery(e.getValue().getAsJsonObject()))
                .filter(d -> d.state().equals("PREPARED"))
                .toList();
    }

    public record ConnectionState(
            Status status, String fingerprint, String problem, boolean resume) {}

    public Map<String, ConnectionState> connections() {
        if (format != 2) return Map.of();
        var result = new TreeMap<String, ConnectionState>();
        for (var e : data.getAsJsonObject("connections").entrySet()) {
            var v = e.getValue().getAsJsonObject();
            result.put(
                    e.getKey(),
                    new ConnectionState(
                            Status.valueOf(v.get("status").getAsString()),
                            v.get("sha256").getAsString(),
                            v.get("problem").getAsString(),
                            v.has("resume") && v.get("resume").getAsBoolean()));
        }
        return Collections.unmodifiableMap(result);
    }

    public void connection(
            int slot, String id, Status expected, Status next, String hash, String problem)
            throws IOException {
        if (format != 2
                || !id.matches("[a-z][a-z0-9_-]{0,39}")
                || !hash.matches("[a-f0-9]{64}")
                || problem.length() > 512)
            throw new IllegalArgumentException("Invalid connection journal record.");
        checkKey(new Key(slot, Cell.ORIGIN));
        String key = slot + ":" + id;
        var before = connections().get(key);
        if ((before == null ? null : before.status()) != expected
                || before != null && !before.fingerprint().equals(hash))
            throw new IllegalArgumentException("Connection state or generation plan changed.");
        boolean valid =
                expected == null && (next == Status.QUEUED || next == Status.CONFLICT)
                        || expected == Status.QUEUED
                                && (next == Status.APPLYING || next == Status.CONFLICT)
                        || expected == Status.APPLYING
                                && (next == Status.READY || next == Status.CONFLICT)
                        || expected == Status.CONFLICT
                                && before != null
                                && next == (before.resume() ? Status.APPLYING : Status.QUEUED);
        if (!valid || next != Status.CONFLICT && !problem.isEmpty())
            throw new IllegalArgumentException("Invalid connection lifecycle transition.");
        var draft = data.deepCopy();
        var value = new JsonObject();
        value.addProperty("status", next.name());
        value.addProperty("sha256", hash);
        value.addProperty("problem", problem);
        value.addProperty(
                "resume",
                next == Status.CONFLICT
                        && (expected == Status.APPLYING || before != null && before.resume()));
        draft.getAsJsonObject("connections").add(key, value);
        commit(draft);
    }

    public Set<Integer> allocatedTerritories() {
        return territories.keySet();
    }

    public DeliveryClaim beginDelivery(int slot, String id, UUID player, String beforeInventory)
            throws IOException {
        if (format != 2)
            throw new IllegalArgumentException("This world has no campaign deliveries.");
        checkKey(new Key(slot, Cell.ORIGIN));
        var goal =
                growth.goals().stream()
                        .filter(g -> g.id().equals(id))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown objective"));
        if (completed(slot).contains(id))
            throw new IllegalArgumentException("This objective is already completed.");
        if (!goal.prerequisites(completed(slot)))
            throw new IllegalArgumentException("Complete the prerequisites first.");
        if (!beforeInventory.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid inventory checkpoint.");
        String key = slot + ":" + id;
        if (data.getAsJsonObject("deliveries").has(key))
            throw new IllegalArgumentException(
                    "A delivery is already pending for this territory and objective.");
        var value = new JsonObject();
        value.addProperty("transaction", UUID.randomUUID().toString());
        value.addProperty("player", player.toString());
        value.addProperty("territory", slot);
        value.addProperty("goal", id);
        value.addProperty("before_inventory", beforeInventory);
        value.addProperty("state", "PREPARED");
        var draft = data.deepCopy();
        draft.getAsJsonObject("deliveries").add(key, value);
        commit(draft);
        return delivery(value);
    }

    private DeliveryClaim checked(DeliveryClaim claim) {
        var value =
                data.getAsJsonObject("deliveries")
                        .getAsJsonObject(claim.territory() + ":" + claim.goal());
        if (value == null
                || !delivery(value).transaction().equals(claim.transaction())
                || !delivery(value).player().equals(claim.player()))
            throw new IllegalArgumentException("Delivery claim changed.");
        return delivery(value);
    }

    public void abortDelivery(DeliveryClaim claim) throws IOException {
        if (!checked(claim).state().equals("PREPARED")
                || completed(claim.territory()).contains(claim.goal()))
            throw new IllegalArgumentException("A paid delivery cannot be aborted.");
        var draft = data.deepCopy();
        draft.getAsJsonObject("deliveries").remove(claim.territory() + ":" + claim.goal());
        commit(draft);
    }

    public List<String> completeDelivery(DeliveryClaim claim) throws IOException {
        if (checked(claim).state().equals("PAID")) return List.of();
        var goal =
                growth.goals().stream()
                        .filter(g -> g.id().equals(claim.goal()))
                        .findFirst()
                        .orElseThrow();
        var awarded = observe(claim.territory(), goal.trigger(), goal.source().toString());
        var draft = data.deepCopy();
        draft.getAsJsonObject("deliveries")
                .getAsJsonObject(claim.territory() + ":" + claim.goal())
                .addProperty("state", "PAID");
        commit(draft);
        return awarded;
    }

    /**
     * Signal observation survives prerequisites arriving later; a goal pays once per physical
     * territory.
     */
    public List<String> observe(int slot, GrowthRules.Trigger trigger, String source)
            throws IOException {
        if (source.length() > 200) throw new IllegalArgumentException("Signal identifier too long");
        String observation = trigger.name() + ":" + source;
        var existing = data.getAsJsonObject("regions").getAsJsonObject(Integer.toString(slot));
        if (existing != null && strings(existing.getAsJsonArray("observed")).contains(observation))
            return List.of();
        checkKey(new Key(slot, Cell.ORIGIN));
        var draft = data.deepCopy();
        var region = region(draft, slot);
        var observed = strings(region.getAsJsonArray("observed"));
        if (source.length() > 200) throw new IllegalArgumentException("Signal identifier too long");
        observed.add(trigger.name() + ":" + source);
        if (observed.size() > 512) throw new IllegalArgumentException("Too many objective signals");
        var completed = strings(region.getAsJsonArray("completed"));
        var awarded = new ArrayList<String>();
        int credits = region.get("credits").getAsInt();
        boolean changed;
        do {
            changed = false;
            for (var goal : growth.goals())
                if (!completed.contains(goal.id())
                        && observed.contains(goal.trigger().name() + ":" + goal.source())
                        && goal.prerequisites(completed)) {
                    completed.add(goal.id());
                    awarded.add(goal.id());
                    credits += goal.credits();
                    for (var cell : goal.targets()) queue(draft, new Key(slot, cell));
                    changed = true;
                }
        } while (changed);
        region.add("observed", array(observed));
        region.add("completed", array(completed));
        region.addProperty("credits", credits);
        if (!growth.chooseFrontier()) spendAutomatically(draft, slot);
        if (!draft.equals(data)) commit(draft);
        return List.copyOf(awarded);
    }

    public Set<Cell> frontier(int slot) {
        var result = new TreeSet<Cell>();
        var cells = territoryCells(slot);
        cells.forEach(
                (cell, status) -> {
                    if (status != Status.CONFLICT)
                        for (var neighbour : layout.neighbours(cell))
                            if (layout.distance(neighbour) <= growth.radius()
                                    && !cells.containsKey(neighbour)) result.add(neighbour);
                });
        return Collections.unmodifiableSet(result);
    }

    private Set<Cell> frontier(JsonObject root, int slot) {
        var result = new TreeSet<Cell>();
        var states = root.getAsJsonObject("cells");
        for (var entry : states.entrySet()) {
            var key = Key.parse(entry.getKey());
            if (key.territory() != slot
                    || Status.valueOf(entry.getValue().getAsString()) == Status.CONFLICT) continue;
            for (var neighbour : layout.neighbours(key.cell()))
                if (layout.distance(neighbour) <= growth.radius()
                        && !states.has(new Key(slot, neighbour).id())) result.add(neighbour);
        }
        return Collections.unmodifiableSet(result);
    }

    public int cost(int slot, Cell cell) {
        checkKey(new Key(slot, cell));
        return cost.applyAsInt(slot, cell);
    }

    public void choose(int slot, Cell cell) throws IOException {
        if (!growth.chooseFrontier())
            throw new IllegalStateException("This pack selects cells automatically.");
        int price = cost(slot, cell);
        if (credits(slot) < price || !frontier(slot).contains(cell))
            throw new IllegalArgumentException(
                    "Choose an available frontier cell; this cell costs " + price + " credits.");
        var draft = data.deepCopy();
        queue(draft, new Key(slot, cell));
        region(draft, slot).addProperty("credits", credits(slot) - price);
        commit(draft);
    }

    private void spendAutomatically(JsonObject draft, int slot) {
        var region = region(draft, slot);
        int credits = region.get("credits").getAsInt();
        if (credits == 0) return;
        var frontier = new TreeSet<>(frontier(draft, slot));
        var cells = draft.getAsJsonObject("cells");
        while (credits > 0 && !frontier.isEmpty()) {
            Cell next = null;
            for (var candidate : frontier)
                if (cost(slot, candidate) <= credits) {
                    next = candidate;
                    break;
                }
            if (next == null) break;
            frontier.remove(next);
            queue(draft, new Key(slot, next));
            for (var neighbour : layout.neighbours(next))
                if (layout.distance(neighbour) <= growth.radius()
                        && !cells.has(new Key(slot, neighbour).id())) frontier.add(neighbour);
            credits -= cost(slot, next);
        }
        region.addProperty("credits", credits);
    }

    private static JsonObject region(JsonObject root, int slot) {
        var regions = root.getAsJsonObject("regions");
        String id = Integer.toString(slot);
        if (!regions.has(id)) {
            var region = new JsonObject();
            region.addProperty("credits", 0);
            region.add("observed", new JsonArray());
            region.add("completed", new JsonArray());
            regions.add(id, region);
        }
        return regions.getAsJsonObject(id);
    }

    private static Set<String> strings(JsonArray array) {
        var result = new TreeSet<String>();
        array.forEach(value -> result.add(value.getAsString()));
        return result;
    }

    private static JsonArray array(Collection<String> values) {
        var array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private void commit(JsonObject draft) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".pending");
        var bytes = StandardCharsets.UTF_8.encode(JSON.toJson(draft) + "\n");
        try (var channel =
                FileChannel.open(
                        temporary,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        Files.move(
                temporary,
                file,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        data = draft;
        index();
    }
}
