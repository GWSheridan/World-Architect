package com.gwsheridan.worldarchitect.skyblock;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogAllocationsTest {
    @TempDir Path data;
    private Path file(){return data.resolve("worldarchitect-islands-v1.dat");}
    private void prepare()throws Exception{var root=new CompoundTag();root.putInt("version",3);root.putString("catalog_sha256","a".repeat(64));root.putLong("seed",17);root.put("players",new CompoundTag());root.put("teams",new CompoundTag());var types=new CompoundTag();for(int slot=0;slot<=4;slot++)types.putString(Integer.toString(slot),slot==0?"lobby":slot%2==0?"forest":"desert");root.put("slot_types",types);NbtIo.writeCompressed(root,file());}
    @Test void choicesReserveMatchingSlotsAndKeepCatalogPinsAcrossAllAdministrationWrites()throws Exception{
        prepare();var alpha=UUID.randomUUID();assertEquals(1,IslandAllocations.claimType(data,4,alpha,"","desert"));assertEquals(3,IslandAllocations.claimType(data,4,UUID.randomUUID(),"builders","desert"));assertEquals(3,IslandAllocations.claimType(data,4,UUID.randomUUID(),"builders","desert"));
        byte[] before=Files.readAllBytes(file());assertThrows(SkyblockJoinException.class,()->IslandAllocations.claimType(data,4,UUID.randomUUID(),"","desert"));assertThrows(IllegalStateException.class,()->IslandAllocations.claimType(data,4,alpha,"","forest"));assertArrayEquals(before,Files.readAllBytes(file()));
        var beta=UUID.randomUUID();assertEquals(2,IslandAllocations.claim(data,4,beta));IslandAllocations.swap(data,4,alpha,beta);var root=NbtIo.readCompressed(file(),NbtAccounter.create(2*1024*1024));assertEquals(3,root.getIntOr("version",0));assertEquals("a".repeat(64),root.getStringOr("catalog_sha256",""));assertEquals("desert",root.getCompoundOrEmpty("slot_types").getStringOr("1",""));assertEquals(Map.of("forest",1,"desert",0),IslandAllocations.availableTypes(data,4));
    }
    @Test void missingCatalogMetadataFailsBeforeAnyClaimIsWritten()throws Exception{prepare();var root=NbtIo.readCompressed(file(),NbtAccounter.create(2*1024*1024));root.getCompoundOrEmpty("slot_types").remove("4");NbtIo.writeCompressed(root,file());byte[] before=Files.readAllBytes(file());assertThrows(IllegalStateException.class,()->IslandAllocations.claim(data,4,UUID.randomUUID()));assertArrayEquals(before,Files.readAllBytes(file()));}
}
