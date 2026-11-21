package com.gwsheridan.worldarchitect.cells;

import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import com.gwsheridan.worldarchitect.api.CellLayout.Cell;
import com.gwsheridan.worldarchitect.worldgen.CellGenerator.Key;
import com.mojang.brigadier.context.CommandContext;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class CellCommands {
    private CellCommands(){}
    public static void register(RegisterCommandsEvent event){
        var root=literal("wa_cells").executes(c->run(c,()->status(c)));
        root.then(literal("panel").executes(c->run(c,()->CellPanel.open(c.getSource().getPlayerOrException()))));
        root.then(literal("home").executes(c->run(c,()->CellWorlds.home(c.getSource().getPlayerOrException()))));
        root.then(literal("choose").then(argument("q",integer(-30,30)).then(argument("r",integer(-30,30)).executes(c->run(c,()->{
            var s=state(c);var p=c.getSource().getPlayerOrException();s.ledger.choose(CellWorlds.territory(s,p,true),new Cell(getInteger(c,"q"),getInteger(c,"r")));
        })))));
        var admin=literal("admin").requires(hasPermission(LEVEL_ADMINS));
        admin.then(literal("unlock").then(argument("territory",integer(0,255)).then(argument("q",integer(-30,30)).then(argument("r",integer(-30,30)).executes(c->run(c,()->state(c).ledger.queue(key(c))))))));
        admin.then(literal("retry").then(argument("territory",integer(0,255)).then(argument("q",integer(-30,30)).then(argument("r",integer(-30,30)).executes(c->run(c,()->state(c).ledger.retry(key(c))))))));
        admin.then(literal("signal").then(argument("player",EntityArgument.player()).then(argument("signal",IdentifierArgument.id()).executes(c->run(c,()->CellWorlds.signal(c.getSource().getLevel(),EntityArgument.getPlayer(c,"player"),GrowthRules.Trigger.SIGNAL,IdentifierArgument.getId(c,"signal")))))));
        admin.then(literal("reassign").then(argument("territory",integer(1,255)).then(argument("owner",greedyString()).executes(c->run(c,()->{
            var state=state(c);var scope=state.generator.cells().growth().scope();String owner=getString(c,"owner").trim();if(scope==GrowthRules.Scope.PLAYER){if(!owner.startsWith("player:"))throw new IllegalArgumentException("Use player:UUID in this world.");UUID.fromString(owner.substring(7));}else if(scope==GrowthRules.Scope.TEAM){if(!owner.startsWith("team:")||c.getSource().getServer().getScoreboard().getPlayerTeam(owner.substring(5))==null)throw new IllegalArgumentException("Use team:NAME for an existing scoreboard team.");}else throw new IllegalArgumentException("Shared territory cannot be reassigned.");state.ledger.reassign(owner,getInteger(c,"territory"));
        })))));
        root.then(admin);event.getDispatcher().register(root);
    }
    private static Key key(CommandContext<CommandSourceStack> c){return new Key(getInteger(c,"territory"),new Cell(getInteger(c,"q"),getInteger(c,"r")));}
    private static CellWorlds.State state(CommandContext<CommandSourceStack> c){var state=CellWorlds.state(c.getSource().getLevel());if(state==null)throw new IllegalArgumentException("This dimension has no progressive cells.");return state;}
    private static void status(CommandContext<CommandSourceStack> c)throws Exception{
        var s=state(c);int slot=c.getSource().getPlayer()==null?0:CellWorlds.territory(s,c.getSource().getPlayer(),false);long ready=s.ledger.territoryCells(slot).values().stream().filter(status->status==CellLedger.Status.READY).count();
        c.getSource().sendSuccess(()->Component.literal("Territory "+slot+" · "+ready+" open cells · "+s.ledger.credits(slot)+" expansion credits\nCompleted: "+String.join(", ",s.ledger.completed(slot))+"\nFrontier: "+s.ledger.frontier(slot).stream().map(Cell::key).limit(20).toList()),false);
    }
    private static int run(CommandContext<CommandSourceStack> c,Action action){try{action.run();return 1;}catch(Exception error){c.getSource().sendFailure(Component.literal(Objects.toString(error.getMessage(),"Cell action failed")));return 0;}}
    @FunctionalInterface private interface Action{void run()throws Exception;}
}
