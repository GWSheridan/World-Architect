package com.gwsheridan.worldarchitect.skyblock;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class AdministrationActionTest {
    private static final Set<String> ONLINE=Set.of("Alice","Bob");
    @Test void islandActionsRetainTheirExactTargetsAndDescriptions(){
        String uuid="11111111-2222-3333-4444-555555555555";
        var action=AdministrationAction.parse("wa_island reassign "+uuid+" 4095",ONLINE);assertEquals("wa_island reassign "+uuid+" 4095",action.command());assertTrue(action.description().contains(uuid));assertTrue(action.description().contains("4095"));
        assertTrue(AdministrationAction.parse("wa_island swap Alice Bob",ONLINE).description().contains("Swap"));
        for(String slot:List.of("0","4096","-1"))assertThrows(IllegalArgumentException.class,()->AdministrationAction.parse("wa_island reassign Alice "+slot,ONLINE));
    }
    @Test void selectorsCommandInjectionAndUnrelatedAdministrationAreRejected(){
        for(String input:List.of("op Alice","execute as Alice run op Alice","wa_island reassign @a 2","team join builders @a","team add builders\nop Alice","wa_island list","wa_spawn set respawn fixed minecraft:overworld 0 65 0 run op Alice","wa_island  swap Alice Bob"))assertThrows(IllegalArgumentException.class,()->AdministrationAction.parse(input,ONLINE),input);
    }
    @Test void teamMembershipIsCheckedAgainAgainstCurrentOnlinePlayers(){
        var action=AdministrationAction.parse("team join builders Alice",ONLINE);assertTrue(action.description().contains("Alice"));assertThrows(IllegalArgumentException.class,()->AdministrationAction.parse(action.command(),Set.of("Bob")));
        assertEquals("team add 建筑队",AdministrationAction.parse("team add 建筑队",ONLINE).command());assertTrue(AdministrationAction.parse("wa_island team assign builders 3",ONLINE).description().contains("island 3"));
    }
    @Test void everySupportedSpawnKindAndClearOperationIsAccepted(){
        for(String input:List.of("wa_spawn set initial fixed minecraft:overworld -10 65 30","wa_spawn set respawn island minecraft:overworld","wa_spawn set portal team minecraft:overworld","wa_spawn set respawn marker minecraft:overworld builds:hall entry 0 65 0 cw90 left_right","wa_spawn set initial generated minecraft:overworld builds:village 0 65 0 main entry","wa_spawn clear portal minecraft:the_end"))assertEquals(input,AdministrationAction.parse(input,ONLINE).command());
        for(String input:List.of("wa_spawn set respawn fixed minecraft:overworld 0 2032 0","wa_spawn set respawn fixed minecraft:overworld 30000000 65 0","wa_spawn set respawn marker minecraft:overworld builds:hall BadMarker 0 65 0","wa_spawn set respawn marker minecraft:overworld builds:hall entry 0 65 0 invalid"))assertThrows(IllegalArgumentException.class,()->AdministrationAction.parse(input,ONLINE));
    }
    @Test void protectionFlagsAndContainerOperationsUseAnExplicitGrammar(){
        assertEquals("wa_island protection containers true",AdministrationAction.parse("wa_island protection containers true",ONLINE).command());assertTrue(AdministrationAction.parse("wa_island protection container add minecraft:hopper",ONLINE).description().contains("minecraft:hopper"));
        for(String input:List.of("wa_island protection containers yes","wa_island protection bypass true","wa_island protection container clear minecraft:chest","wa_island protection container add Invalid:Block"))assertThrows(IllegalArgumentException.class,()->AdministrationAction.parse(input,ONLINE));
    }
}
