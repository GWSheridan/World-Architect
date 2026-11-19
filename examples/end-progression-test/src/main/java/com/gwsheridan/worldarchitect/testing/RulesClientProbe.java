package com.gwsheridan.worldarchitect.testing;
import java.nio.file.*;
import java.util.stream.Stream;
import com.gwsheridan.worldarchitect.environment.WorldEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
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
@EventBusSubscriber(modid="wa_dragon_probe",value=Dist.CLIENT)
public final class RulesClientProbe {
    private static final String FOLDER=System.getProperty("wa.rules.probe");
    private static final KeyEvent ENTER=new KeyEvent(257,0,0);
    private static int ticks,action,finish;
    private static String previous="";
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
        for(String line:Files.readAllLines(file)){
            if(line.startsWith("COMMAND "))mc.player.connection.send(new ServerboundChatCommandPacket(line.substring(8)));
            else if(line.equals("SHOT"))Screenshot.grab(mc,false);
            else if(line.equals("CLOSE"))mc.gui.setScreen(null);
            else if(line.startsWith("EDIT ")){String[] parts=line.split(" ",3);widgets(mc.gui.screen()).filter(EditBox.class::isInstance).map(EditBox.class::cast).toList().get(Integer.parseInt(parts[1])).setValue(parts[2]);}
            else if(line.startsWith("PRESS "))widgets(mc.gui.screen()).filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(line.substring(6))).findFirst().orElseThrow().onPress(ENTER);
            else if(line.equals("RESET_OBSERVATION"))previous="";
            else throw new AssertionError(line);
        }
        action++;finish=ticks+12;
    }
    private static Stream<GuiEventListener> widgets(GuiEventListener listener){return listener instanceof ContainerEventHandler container?Stream.concat(Stream.of(listener),container.children().stream().flatMap(RulesClientProbe::widgets)):Stream.of(listener);}
    private static int rgb(Vector3fc v){return Math.round(v.x()*255)<<16|Math.round(v.y()*255)<<8|Math.round(v.z()*255);}
    @SubscribeEvent public static void chat(ClientChatReceivedEvent.System event)throws Exception{if(FOLDER!=null)record("SERVER "+event.getMessage().getString().replace('\n',' '));}
    private static void record(String s)throws Exception{Files.writeString(Path.of(FOLDER,"observed.log"),s+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
}

