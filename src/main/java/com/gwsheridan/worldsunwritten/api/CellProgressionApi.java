package com.gwsheridan.worldsunwritten.api;

import com.gwsheridan.worldsunwritten.cells.*;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

/** Version-one quest adapter: invoke from the logical server thread after validating the quest reward. */
public final class CellProgressionApi {
    public static final int VERSION=1;
    private CellProgressionApi(){}
    /** Repeated signals are harmless. Goal definitions and terrain rewards come from the saved preset. */
    public static List<String> signal(ServerPlayer player,Identifier objective){return CellWorlds.signal(player,GrowthRules.Trigger.SIGNAL,objective);}
    /** Explicit target for quests completed while the player is visiting another dimension. */
    public static List<String> signal(ServerLevel territoryDimension,ServerPlayer player,Identifier objective){return CellWorlds.signal(territoryDimension,player,GrowthRules.Trigger.SIGNAL,objective);}
}
