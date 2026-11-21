package com.gwsheridan.worldarchitect.testing;
import java.nio.file.*;
import java.util.stream.Stream;
import com.gwsheridan.worldarchitect.environment.WorldEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3fc;
@EventBusSubscriber(modid="wa_validation",value=Dist.CLIENT)
public final class MultiplayerProbe {
    private static final String FOLDER=System.getProperty("wa.validation.probe");
    private static final KeyEvent ENTER=new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,'\r',0);
    private static int ticks,action,finish;
    private static String previous="";
    private static net.minecraft.nbt.CompoundTag islandChoice;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(FOLDER==null)return;
        var root=Path.of(FOLDER);var mc=Minecraft.getInstance();
        if(Files.exists(root.resolve("stop"))){mc.stop();return;}
        if(mc.gui.screen()!=null && mc.gui.screen().getClass().getSimpleName().equals("WinScreen"))mc.gui.screen().onClose();
        if(mc.player==null||mc.level==null)return;
        ticks++;
        if(mc.player.isDeadOrDying()){mc.player.respawn();return;}
        if(ticks%10==0){
            var level=mc.level;var pos=mc.player.position();var attrs=level.environmentAttributes();var profile=WorldEnvironment.profile(level);
            String state=String.format(java.util.Locale.ROOT,"AT %s %.2f %.2f %.2f TIME %d RAIN %.1f THUNDER %.1f FOG %06x SKY %06x CLOUD %.1f HEIGHT %.1f SUN %.2f PROFILE %s",
                level.dimension().identifier(),pos.x,pos.y,pos.z,level.getDefaultClockTime(),level.getRainLevel(1),level.getThunderLevel(1),
                rgb(attrs.getValue(EnvironmentAttributes.FOG_COLOR,pos)),rgb(attrs.getValue(EnvironmentAttributes.SKY_COLOR,pos)),
                attrs.getValue(EnvironmentAttributes.CLOUD_COLOR,pos).w(),attrs.getValue(EnvironmentAttributes.CLOUD_HEIGHT,pos),
                attrs.getValue(EnvironmentAttributes.SUN_ANGLE,pos),profile);
            if(!state.equals(previous)){record(state);previous=state;}
        }
        if(finish>0){if(ticks>=finish){record("DONE "+(action-1)+" SCREEN "+(mc.gui.screen()==null?"none":mc.gui.screen().getTitle().getString()));finish=0;}return;}
        var file=root.resolve("action-"+action+".txt");if(!Files.exists(file))return;
        var commands=Files.readAllLines(file);if(commands.isEmpty())throw new AssertionError("Empty published action file");
        for(String line:commands){
            if(line.startsWith("COMMAND "))mc.player.connection.send(new ServerboundChatCommandPacket(line.substring(8)));
            else if(line.equals("CHOICE")){var screen=mc.gui.screen();if(screen!=null&&screen.getClass().getSimpleName().equals("IslandChoiceScreen")){var field=screen.getClass().getDeclaredField("data");field.setAccessible(true);islandChoice=((net.minecraft.nbt.CompoundTag)field.get(screen)).copy();var types=islandChoice.getListOrEmpty("types").stream().map(t->(net.minecraft.nbt.CompoundTag)t).map(t->t.getStringOr("id","")+":"+t.getIntOr("available",0)).toList();record("CHOICE "+islandChoice.getStringOr("token","")+" "+islandChoice.getStringOr("catalog","")+" "+types);}else record("CHOICE_CLOSED");}
            else if(line.startsWith("CHOOSE ")){if(islandChoice==null)throw new AssertionError("No observed island choice");mc.player.connection.send(new ServerboundChatCommandPacket("wa_island choose "+islandChoice.getStringOr("token","")+" "+islandChoice.getStringOr("catalog","")+" "+line.substring(7)));record("CHOICE_SENT "+line.substring(7));}
            else if(line.equals("SHOT"))Screenshot.grab(mc,false);
            else if(line.equals("CLOSE"))mc.gui.setScreen(null);
            else if(line.equals("SCREEN_CLOSED")){if(mc.gui.screen()!=null)throw new AssertionError("A cancelled preview reopened its screen");record("SCREEN_CLOSED_PASS");}
            else if(line.startsWith("EDIT ")){String[] parts=line.split(" ",3);widgets(mc.gui.screen()).filter(EditBox.class::isInstance).map(EditBox.class::cast).toList().get(Integer.parseInt(parts[1])).setValue(parts[2]);}
            else if(line.equals("FIELDS")){var screen=mc.gui.screen();record("FIELDS "+widgets(screen).filter(EditBox.class::isInstance).map(EditBox.class::cast).map(EditBox::getValue).toList());for(String key:java.util.List.of("status","document")){try{var field=screen.getClass().getDeclaredField(key);field.setAccessible(true);record("FIELD "+key+" "+field.get(screen));}catch(NoSuchFieldException ignored){}}}
            else if(line.startsWith("PRESS ")){var button=widgets(mc.gui.screen()).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast).filter(b->b.getMessage().getString().equals(line.substring(6))).findFirst().orElseThrow();if(!button.active)throw new AssertionError("Disabled "+line);button.onPress(ENTER);}
            else if(line.startsWith("ROW "))select(mc.gui.screen(),Integer.parseInt(line.substring(4)));
            else if(line.startsWith("SCALE ")){mc.options.guiScale().set(Integer.parseInt(line.substring(6)));mc.resizeGui();}
            else if(line.equals("BOUNDS")){var screen=mc.gui.screen();int w=screen.width,h=screen.height;bounds(screen);screen.resize(320,240);bounds(screen);screen.resize(w,h);record("BOUNDS_PASS "+screen.getTitle().getString());}
            else if(line.equals("VIEW")){var screen=mc.gui.screen();record("WIDGETS "+widgets(screen).filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).map(w->w.getMessage().getString()+"="+w.active).toList());try{var field=screen.getClass().getDeclaredField("data");field.setAccessible(true);record("VIEW "+field.get(screen));}catch(NoSuchFieldException ignored){}}
            else if(line.startsWith("USE ")){var p=line.substring(4).split(" ");var pos=new net.minecraft.core.BlockPos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));}
            else if(line.equals("MENU"))record("MENU "+mc.player.containerMenu.getClass().getSimpleName()+" SCREEN "+(mc.gui.screen()==null?"none":mc.gui.screen().getTitle().getString()));
            else if(line.equals("RESET_OBSERVATION"))previous="";
            else if(line.startsWith("BLOCK ")){var parts=line.split(" ");var pos=new net.minecraft.core.BlockPos(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3]));record("BLOCK "+pos.toShortString()+" "+net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(mc.level.getBlockState(pos)));}
            else throw new AssertionError(line);
        }
        action++;finish=ticks+12;
    }
    private static Stream<GuiEventListener> widgets(GuiEventListener listener){return listener instanceof ContainerEventHandler container?Stream.concat(Stream.of(listener),container.children().stream().flatMap(MultiplayerProbe::widgets)):Stream.of(listener);}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void select(net.minecraft.client.gui.screens.Screen screen,int index)throws Exception{var rows=widgets(screen).filter(AbstractSelectionList.class::isInstance).map(AbstractSelectionList.class::cast).findFirst().orElseThrow();var entry=rows.children().get(index);var method=java.util.Arrays.stream(rows.getClass().getMethods()).filter(m->m.getName().equals("setSelected")&&!m.isBridge()&&m.getParameterCount()==1&&m.getParameterTypes()[0].isInstance(entry)).findFirst().orElseThrow();method.setAccessible(true);method.invoke(rows,entry);}
    private static void bounds(net.minecraft.client.gui.screens.Screen screen){var controls=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.visible).toList();for(var a:controls){if(a.getX()<0||a.getY()<0||a.getRight()>screen.width||a.getBottom()>screen.height)throw new AssertionError("Offscreen "+a.getMessage());for(var b:controls)if(a!=b&&a.getRight()>b.getX()&&a.getX()<b.getRight()&&a.getBottom()>b.getY()&&a.getY()<b.getBottom())throw new AssertionError("Overlapping "+a.getMessage()+" / "+b.getMessage());}}
    private static int rgb(Vector3fc v){return Math.round(v.x()*255)<<16|Math.round(v.y()*255)<<8|Math.round(v.z()*255);}
    @SubscribeEvent public static void chat(ClientChatReceivedEvent.System event)throws Exception{if(FOLDER!=null)record("SERVER "+event.getMessage().getString().replace('\n',' '));}
    private static void record(String s)throws Exception{Files.writeString(Path.of(FOLDER,"observed.log"),s+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
}
