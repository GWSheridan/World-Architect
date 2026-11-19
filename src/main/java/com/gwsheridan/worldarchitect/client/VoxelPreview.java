package com.gwsheridan.worldarchitect.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Bounded voxel view. Interior faces are omitted and orbit order is cached. */
final class VoxelPreview {
    private final int[] cells,size; private final BitSet occupied=new BitSet(1<<21); private Integer[] order;
    private double yaw=Math.PI/4;
    private int cachedWidth,cachedHeight;
    private List<int[]> spans=List.of();
    VoxelPreview(int[] cells,int[] size){
        if(cells.length%2!=0||cells.length>131072||size.length!=3)throw new IllegalArgumentException("Invalid assembly geometry");
        this.cells=cells.clone();this.size=size.clone();for(int i=0;i<cells.length;i+=2){if(cells[i]<0||cells[i]>=(1<<21))throw new IllegalArgumentException("Invalid cell coordinate");occupied.set(cells[i]);}sort();
    }
    void orbit(int direction){yaw+=direction*Math.PI/4;sort();cachedWidth=0;}
    private void sort(){order=java.util.stream.IntStream.range(0,cells.length/2).boxed().sorted(Comparator.comparingDouble(i->{int p=cells[i*2];return(p&127)*Math.sin(yaw)+(p>>14&127)*Math.cos(yaw)+(p>>7&127)*.5;})).toArray(Integer[]::new);}
    private boolean filled(int x,int y,int z){return x>=0&&y>=0&&z>=0&&x<128&&y<128&&z<128&&occupied.get(x|y<<7|z<<14);}
    int[] project(double x,double y,double z,int left,int top,int width,int height){double scale=Math.max(.05,Math.min(width/(size[0]+size[2]+2.0),height/(size[1]+(size[0]+size[2])*.5+2)));return IsoCanvas.point(x-size[0]/2.0,y-size[1]/2.0,z-size[2]/2.0,yaw,scale,left+width/2,top+height/2);}
    void draw(GuiGraphicsExtractor g,int left,int top,int width,int height){
        if(width<=0||height<=0)return;
        if(width!=cachedWidth||height!=cachedHeight)rasterize(width,height);
        for(var span:spans)g.fill(left+span[0],top+span[1],left+span[2],top+span[1]+1,span[3]);
    }
    /** Cache the complete projected image; large assemblies do not rebuild every frame. */
    private void rasterize(int width,int height){
        int[] pixels=new int[Math.multiplyExact(width,height)];
        for(int i:order){int p=cells[i*2],x=p&127,y=p>>7&127,z=p>>14&127;int color=0xFF000000|java.awt.Color.HSBtoRGB(Math.floorMod(cells[i*2+1],360)/360f,.45f,.83f);
            if(!filled(x,y+1,z))quad(pixels,color,width,height,new double[][]{{x,y+1,z},{x+1,y+1,z},{x+1,y+1,z+1},{x,y+1,z+1}});
            int dx=Math.sin(yaw)>=0?1:-1,dz=Math.cos(yaw)>=0?1:-1;
            if(!filled(x+dx,y,z)){int face=x+(dx>0?1:0);quad(pixels,shade(color,.60),width,height,new double[][]{{face,y,z},{face,y,z+1},{face,y+1,z+1},{face,y+1,z}});}
            if(!filled(x,y,z+dz)){int face=z+(dz>0?1:0);quad(pixels,shade(color,.78),width,height,new double[][]{{x,y,face},{x+1,y,face},{x+1,y+1,face},{x,y+1,face}});}
        }
        var output=new ArrayList<int[]>();
        for(int y=0;y<height;y++)for(int x=0;x<width;){int start=x,color=pixels[y*width+x++];while(x<width&&pixels[y*width+x]==color)x++;if(color!=0)output.add(new int[]{start,y,x,color});}
        spans=List.copyOf(output);cachedWidth=width;cachedHeight=height;
    }
    private void quad(int[] pixels,int color,int width,int height,double[][] points){
        int[][] p=new int[4][];int min=height,max=0;for(int i=0;i<4;i++){p[i]=project(points[i][0],points[i][1],points[i][2],0,0,width,height);min=Math.min(min,p[i][1]);max=Math.max(max,p[i][1]);}
        for(int y=Math.max(0,min);y<=Math.min(height-1,max);y++){
            double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
            for(int i=0;i<4;i++){int[] a=p[i],b=p[(i+1)%4];if(a[1]==b[1]||y<Math.min(a[1],b[1])||y>=Math.max(a[1],b[1]))continue;double x=a[0]+(double)(y-a[1])*(b[0]-a[0])/(b[1]-a[1]);lo=Math.min(lo,x);hi=Math.max(hi,x);}
            if(lo<=hi){int first=Math.clamp((int)Math.floor(lo),0,width),last=Math.clamp((int)Math.ceil(hi)+1,0,width);if(first<last)Arrays.fill(pixels,y*width+first,y*width+last,color);}
        }
    }
    private static int shade(int color,double factor){return 0xFF000000|(int)((color>>16&255)*factor)<<16|(int)((color>>8&255)*factor)<<8|(int)((color&255)*factor);}
}
