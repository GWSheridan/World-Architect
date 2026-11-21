package com.gwsheridan.worldarchitect.cells;

import com.gwsheridan.worldarchitect.studio.StudioViewPayload;
import java.io.IOException;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

final class CellPanel {
    private CellPanel(){}
    static void open(ServerPlayer player)throws IOException{
        var state=CellWorlds.state(player.level());if(state==null)throw new IllegalArgumentException("This dimension has no progressive cells.");int slot=CellWorlds.territory(state,player,true);var root=new CompoundTag();root.putString("kind","cell_progress");root.putInt("territory",slot);root.putInt("credits",state.ledger.credits(slot));root.putBoolean("choice",state.generator.cells().growth().chooseFrontier());
        var frontier=new ListTag();for(var cell:state.ledger.frontier(slot)){var row=new CompoundTag();row.putInt("q",cell.q());row.putInt("r",cell.r());row.putString("type",state.generator.cells().tile(state.generator.seed(),slot,cell).title());frontier.add(row);}root.put("frontier",frontier);
        var completed=state.ledger.completed(slot);var goals=new ListTag();for(var goal:state.generator.cells().growth().goals()){var row=new CompoundTag();row.putString("title",goal.title());row.putBoolean("complete",completed.contains(goal.id()));row.putString("trigger",goal.trigger().getSerializedName()+" · "+goal.source());row.putString("prerequisites",String.join(", ",goal.requires()));goals.add(row);}root.put("goals",goals);PacketDistributor.sendToPlayer(player,new StudioViewPayload(root));
    }
}
