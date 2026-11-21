package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.structure.IslandBuild;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

/** Native resource-pack models, cached in a depth-tested off-screen surface. */
final class IslandModelPreview {
    private static final BlockDisplayContext CONTEXT=BlockDisplayContext.create();
    private Geometry geometry;private Object modelSet;private final BlockPos size;private final List<IslandBuild.Cell> source;private boolean sampled;
    private float yaw=45;
    private record Cell(BlockPos pos,BlockModelRenderState model){}
    private record Geometry(BlockPos size,List<Cell> cells){}
    record State(Geometry geometry,float yaw,int x0,int y0,int x1,int y1,float scale,ScreenRectangle scissorArea,ScreenRectangle bounds)implements PictureInPictureRenderState{}
    IslandModelPreview(IslandBuild build){
        this(build.size(),build.cells());
    }
    IslandModelPreview(BlockPos size,List<IslandBuild.Cell> source){
        this.size=size.immutable();this.source=List.copyOf(source);rebuild();
    }
    private void rebuild(){
        var states=new HashMap<BlockPos,BlockState>();source.forEach(c->states.put(c.pos(),c.state()));
        var visible=source.stream().filter(cell->!cell.state().isSolidRender()||!Arrays.stream(net.minecraft.core.Direction.values()).allMatch(d->{var adjacent=states.get(cell.pos().relative(d));return adjacent!=null&&adjacent.isSolidRender();})).toList();int stride=Math.max(1,(visible.size()+8191)/8192);sampled=stride>1;
        var models=new HashMap<BlockState,BlockModelRenderState>();var cells=new ArrayList<Cell>();var mc=Minecraft.getInstance();modelSet=mc.getModelManager().getBlockModelSet();
        for(int i=0;i<visible.size();i+=stride){var cell=visible.get(i);
            var model=models.computeIfAbsent(cell.state(),state->{var result=new BlockModelRenderState();mc.getBlockModelResolver().update(result,state,CONTEXT);return result;});
            if(!model.isEmpty())cells.add(new Cell(cell.pos(),model));
        }
        geometry=new Geometry(size,List.copyOf(cells));
    }
    boolean sampled(){return sampled;}
    void orbit(int direction){yaw+=45*direction;}
    void draw(GuiGraphicsExtractor g,int x,int y,int width,int height){
        if(width<1||height<1)return;if(modelSet!=Minecraft.getInstance().getModelManager().getBlockModelSet())rebuild();var size=geometry.size();double radians=Math.toRadians(yaw),horizontal=Math.abs(Math.cos(radians))*size.getX()+Math.abs(Math.sin(radians))*size.getZ();double vertical=size.getY()*.866+horizontal*.5;
        float scale=(float)Math.max(.1,Math.min((width-12)/(horizontal+2),(height-12)/(vertical+2)));
        var clip=g.peekScissorStack();g.submitPictureInPictureRenderState(new State(geometry,yaw,x,y,x+width,y+height,scale,clip,PictureInPictureRenderState.getBounds(x,y,x+width,y+height,clip)));
    }
    static void register(RegisterPictureInPictureRenderersEvent event){event.register(State.class,Renderer::new);}
    private static final class Renderer extends PictureInPictureRenderer<State>{
        private State previous;
        @Override public Class<State> getRenderStateClass(){return State.class;}
        @Override protected float getTranslateY(int height,int guiScale){return height/2f;}
        @Override protected boolean textureIsReadyToBlit(State state){return previous!=null&&previous.geometry==state.geometry&&previous.yaw==state.yaw&&previous.scale==state.scale;}
        @Override protected void renderToTexture(State state,PoseStack pose,SubmitNodeCollector collector){
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            pose.rotateDegrees(Axis.XP,210);pose.rotateDegrees(Axis.YP,state.yaw);var size=state.geometry.size;
            pose.translate(-size.getX()/2f,-size.getY()/2f,-size.getZ()/2f);
            for(var cell:state.geometry.cells){pose.pushPose();pose.translate(cell.pos.getX(),cell.pos.getY(),cell.pos.getZ());cell.model.submitMultiLayer(pose,collector,15728880,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
            previous=state;
        }
        @Override protected String getTextureLabel(){return "World Architect island preview";}
    }
}
