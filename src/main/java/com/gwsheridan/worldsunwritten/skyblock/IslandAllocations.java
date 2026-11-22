package com.gwsheridan.worldsunwritten.skyblock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Accessed on the server thread. Commit the claim before putting a player on its island. */
public final class IslandAllocations {
    private static final String FILE = "worldsunwritten-islands-v1.dat";

    private IslandAllocations() {}

    public static boolean exists(Path dataDirectory) {
        return Files.exists(com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length())));
    }

    public static void prepare(Path dataDirectory, int capacity, boolean newWorld) {
        Path file = com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length()));
        if (newWorld && Files.notExists(file)) {
            store(file, new CompoundTag(), new CompoundTag());
        } else {
            read(file, capacity);
        }
    }

    public static int claim(Path dataDirectory, int capacity, UUID player) {
        Path file = com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length()));
        Claims claims = read(file, capacity);
        Integer existing = claims.players.get(player);
        if (existing != null) {
            return existing;
        }
        int slot = claims.occupied.nextClearBit(1);
        if (slot > capacity) {
            throw new SkyblockJoinException("All " + capacity + " personal Skyblock islands are assigned.");
        }
        claims.tag.putInt(player.toString(), slot);
        store(file, claims.tag, claims.teamsTag);
        return slot;
    }

    public static int claimTeam(Path dataDirectory, int capacity, String team) {
        checkTeam(team);
        Path file = com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length()));
        Claims claims = read(file, capacity);
        Integer existing = claims.teams.get(team);
        if (existing != null) {
            return existing;
        }
        int slot = claims.occupied.nextClearBit(1);
        if (slot > capacity) {
            throw new SkyblockJoinException("All " + capacity + " personal Skyblock islands are assigned.");
        }
        claims.teamsTag.putInt(team, slot);
        store(file, claims.tag, claims.teamsTag);
        return slot;
    }

    public static void reassign(Path dataDirectory, int capacity, UUID player, int slot) {
        Path file = com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length()));
        Claims claims = read(file, capacity);
        int current = claims.players.getOrDefault(player, 0);
        requireVacant(claims, capacity, slot, current);
        if (current != slot) {
            claims.tag.putInt(player.toString(), slot);
            store(file, claims.tag, claims.teamsTag);
        }
    }

    public static void reassignTeam(Path dataDirectory, int capacity, String team, int slot) {
        checkTeam(team);
        Path file = com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length()));
        Claims claims = read(file, capacity);
        int current = claims.teams.getOrDefault(team, 0);
        requireVacant(claims, capacity, slot, current);
        if (current != slot) {
            claims.teamsTag.putInt(team, slot);
            store(file, claims.tag, claims.teamsTag);
        }
    }

    public static void swap(Path dataDirectory, int capacity, UUID first, UUID second) {
        Path file = com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length()));
        Claims claims = read(file, capacity);
        Integer a = claims.players.get(first);
        Integer b = claims.players.get(second);
        if (first.equals(second) || a == null || b == null) {
            throw new IllegalArgumentException("Choose two different players with assigned islands.");
        }
        claims.tag.putInt(first.toString(), b);
        claims.tag.putInt(second.toString(), a);
        store(file, claims.tag, claims.teamsTag);
    }

    public static Map<UUID, Integer> players(Path dataDirectory, int capacity) {
        return Map.copyOf(read(com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length())), capacity).players);
    }

    public static Map<String, Integer> teams(Path dataDirectory, int capacity) {
        return Map.copyOf(read(com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length())), capacity).teams);
    }

    public static boolean owns(Path dataDirectory, int capacity, UUID player, String team, int slot) {
        Claims claims = read(com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length())), capacity);
        return claims.players.getOrDefault(player, 0) == slot || claims.teams.getOrDefault(team, 0) == slot;
    }

    /** Install a catalog pin only for a newly created world; never reinterpret legacy assignments. */
    public static void prepareCatalog(Path dataDirectory, com.gwsheridan.worldsunwritten.config.IslandCatalog catalog, long seed, boolean newWorld) {
        Path file=com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length()));
        String pin=catalogPin(catalog,seed);
        if(newWorld&&Files.notExists(file)){
            var root=new CompoundTag();root.putInt("version",3);root.put("players",new CompoundTag());root.put("teams",new CompoundTag());root.putString("catalog_sha256",pin);root.putLong("seed",seed);
            var types=new CompoundTag();for(int slot=0;slot<=catalog.personalSlots();slot++)types.putString(Integer.toString(slot),catalog.entry(slot,seed).id());root.put("slot_types",types);write(file,root);
        }
        var root=readRoot(file);read(file,catalog.personalSlots());
        if(root.getIntOr("version",0)!=3||!root.getStringOr("catalog_sha256","").equals(pin)||!root.contains("seed")||root.getLongOr("seed",0)!=seed)throw new IllegalStateException("The saved island catalog does not match its immutable generation snapshot.");
        var types=root.getCompoundOrEmpty("slot_types");
        for(int slot=0;slot<=catalog.personalSlots();slot++)if(!types.getStringOr(Integer.toString(slot),"").equals(catalog.entry(slot,seed).id()))throw new IllegalStateException("Saved island slot type changed.");
    }

    public static String catalogPin(com.gwsheridan.worldsunwritten.config.IslandCatalog catalog,long seed){
        var json=com.gwsheridan.worldsunwritten.config.IslandCatalog.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,catalog).getOrThrow().toString();
        return com.gwsheridan.worldsunwritten.modpack.PresetInheritance.fingerprint((json+"\n"+seed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static Map<String,Integer> availableTypes(Path dataDirectory,int capacity){
        var file=com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length()));var claims=read(file,capacity);var root=readRoot(file);
        if(root.getIntOr("version",0)!=3)throw new IllegalStateException("Island choices require a versioned catalog world.");
        var result=new java.util.TreeMap<String,Integer>();var types=root.getCompoundOrEmpty("slot_types");
        for(int slot=1;slot<=capacity;slot++){String type=types.getStringOr(Integer.toString(slot),"");result.putIfAbsent(type,0);if(!claims.occupied.get(slot))result.merge(type,1,Integer::sum);}
        return Map.copyOf(result);
    }

    /** The caller revalidates the player and current team on the server thread. */
    public static int claimType(Path dataDirectory,int capacity,UUID player,String team,String type){
        var file=com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length()));var claims=read(file,capacity);var root=readRoot(file);
        if(root.getIntOr("version",0)!=3||!type.matches("[a-z][a-z0-9_-]{0,31}"))throw new IllegalStateException("Invalid island choice.");
        boolean shared=!team.isEmpty();if(shared)checkTeam(team);
        int existing=shared?claims.teams.getOrDefault(team,0):claims.players.getOrDefault(player,0);
        var types=root.getCompoundOrEmpty("slot_types");
        if(existing>0){if(!types.getStringOr(Integer.toString(existing),"").equals(type))throw new IllegalStateException("This player or team already has an island. Selection cannot replace it.");return existing;}
        for(int slot=1;slot<=capacity;slot++)if(!claims.occupied.get(slot)&&types.getStringOr(Integer.toString(slot),"").equals(type)){
            if(shared)claims.teamsTag.putInt(team,slot);else claims.tag.putInt(player.toString(),slot);
            store(file,claims.tag,claims.teamsTag);return slot;
        }
        throw new SkyblockJoinException("No unassigned "+type+" islands remain. Choose another available type.");
    }

    private static CompoundTag readRoot(Path file){try{return NbtIo.readCompressed(file,NbtAccounter.create(2*1024*1024));}catch(IOException error){throw new UncheckedIOException("Cannot read the original island ledger.",error);}}

    private static void requireVacant(Claims claims, int capacity, int slot, int current) {
        if (slot < 1 || slot > capacity) {
            throw new IllegalArgumentException("Choose an island slot between 1 and " + capacity + ".");
        }
        if (claims.occupied.get(slot) && slot != current) {
            throw new IllegalArgumentException("Island slot " + slot + " already has an owner. Use swap for two player assignments.");
        }
    }

    private static void checkTeam(String team) {
        if (team.isBlank() || team.length() > 64) {
            throw new IllegalArgumentException("Team names must contain 1–64 characters.");
        }
    }

    public static int assignedSlot(Path dataDirectory, int capacity, UUID player) {
        return read(com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length())), capacity).players.getOrDefault(player, 0);
    }

    private static Claims read(Path file, int capacity) {
        try {
            var root = NbtIo.readCompressed(file, NbtAccounter.create(2 * 1024 * 1024));
            int version = root.getIntOr("version", 0);
            if (version != 1 && version != 2 && version != 3) {
                throw new IllegalStateException("Invalid Skyblock allocation version in " + file);
            }
            var tag = root.getCompound("players").orElseThrow(() -> new IllegalStateException("Missing Skyblock allocations in " + file));
            var teamsTag = root.getCompoundOrEmpty("teams");
            if (version == 1 && root.contains("teams") || version >= 2 && root.getCompound("teams").isEmpty()) {
                throw new IllegalStateException("Invalid team allocations in " + file);
            }
            if(version==3){
                var types=root.getCompoundOrEmpty("slot_types");
                if(!root.getStringOr("catalog_sha256","").matches("[a-f0-9]{64}")||!root.contains("seed")||types.size()!=capacity+1)throw new IllegalStateException("Missing or invalid island catalog pin.");
                for(int slot=0;slot<=capacity;slot++)if(!types.getStringOr(Integer.toString(slot),"").matches("[a-z][a-z0-9_-]{0,31}"))throw new IllegalStateException("Invalid saved island type.");
            }
            if (tag.size() + teamsTag.size() > capacity) {
                throw new IllegalStateException("Too many Skyblock allocations in " + file);
            }
            BitSet occupied = new BitSet(capacity + 1);
            Map<UUID, Integer> players = new HashMap<>();
            for (String key : tag.keySet()) {
                int slot = tag.getIntOr(key, 0);
                UUID player = UUID.fromString(key);
                if (!player.toString().equals(key) || slot < 1 || slot > capacity || occupied.get(slot)) {
                    throw new IllegalStateException("Invalid or duplicate Skyblock allocation in " + file + " for " + key);
                }
                players.put(player, slot);
                occupied.set(slot);
            }
            Map<String, Integer> teams = new HashMap<>();
            for (String team : teamsTag.keySet()) {
                checkTeam(team);
                int slot = teamsTag.getIntOr(team, 0);
                if (slot < 1 || slot > capacity || occupied.get(slot)) {
                    throw new IllegalStateException("Invalid or duplicate Skyblock team allocation in " + file + " for " + team);
                }
                teams.put(team, slot);
                occupied.set(slot);
            }
            return new Claims(tag, teamsTag, players, teams, occupied);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read Skyblock allocations from " + file + ". Restore the original allocation file before opening the world.", e);
        }
    }

    private static void store(Path file, CompoundTag players, CompoundTag teams) {
        CompoundTag previous = Files.exists(file) ? readRoot(file) : new CompoundTag();
        boolean catalog=previous.getIntOr("version",0)==3;
        CompoundTag root = catalog ? previous : new CompoundTag();
        root.putInt("version", catalog ? 3 : teams.isEmpty() ? 1 : 2);
        root.put("players", players);
        if (catalog || !teams.isEmpty()) {
            root.put("teams", teams);
        }
        write(file,root);
    }

    private static void write(Path file,CompoundTag root){
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), ".worldsunwritten-islands-", ".tmp");
            try {
                NbtIo.writeCompressed(root, temporary);
                try (FileChannel data = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    data.force(true);
                }
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save Skyblock allocations to " + file, e);
        }
    }

    private record Claims(CompoundTag tag, CompoundTag teamsTag, Map<UUID, Integer> players, Map<String, Integer> teams, BitSet occupied) {}
}
