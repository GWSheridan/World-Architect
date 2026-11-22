package com.gwsheridan.worldarchitect.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.util.StringRepresentable;

/** Original square/regular-hex geometry. Block centres and tie breaking are shared by all consumers. */
public record CellLayout(Shape shape,int size,int gap,int rim) {
    private static final double ROOT3=StrictMath.sqrt(3);
    public static final Codec<CellLayout> CODEC=RecordCodecBuilder.<CellLayout>create(b->b.group(
            StringRepresentable.fromEnum(Shape::values).fieldOf("shape").forGetter(CellLayout::shape),
            Codec.intRange(16,256).fieldOf("size").forGetter(CellLayout::size),
            Codec.intRange(0,64).optionalFieldOf("gap",0).forGetter(CellLayout::gap),
            Codec.intRange(0,8).optionalFieldOf("rim",0).forGetter(CellLayout::rim)
    ).apply(b,CellLayout::new)).validate(CellLayout::validate);
    public DataResult<CellLayout> validate(){return size%4!=0||gap+rim>=size/2?DataResult.error(()->"Use a cell size divisible by four, with gap and rim smaller than half its size."):DataResult.success(this);}
    public Cell at(int blockX,int blockZ){return atPoint(blockX+.5,blockZ+.5);}
    public Cell atPoint(double x,double z){
        if(shape==Shape.SQUARE)return new Cell((int)StrictMath.floor(x/size),(int)StrictMath.floor(z/size));
        x-=8;z-=8;
        double q=shape==Shape.POINTY_HEX?(x/ROOT3-z/3)/size:2*x/(3*size);
        double r=shape==Shape.POINTY_HEX?2*z/(3*size):(z/ROOT3-x/3)/size;
        int a=(int)StrictMath.floor(q),b=(int)StrictMath.floor(r);Cell best=null;double distance=Double.POSITIVE_INFINITY;
        for(int i=a-1;i<=a+2;i++)for(int j=b-1;j<=b+2;j++){
            Cell candidate=new Cell(i,j);Point centre=centre(candidate);double dx=x+8-centre.x,dz=z+8-centre.z,d=dx*dx+dz*dz;
            if(d<distance-1e-7||StrictMath.abs(d-distance)<=1e-7&&(best==null||candidate.compareTo(best)<0)){best=candidate;distance=d;}
        }
        return Objects.requireNonNull(best);
    }
    public Point centre(Cell cell){return switch(shape){
        case SQUARE->new Point((cell.q+.5)*size,(cell.r+.5)*size);
        case POINTY_HEX->new Point(8+size*ROOT3*(cell.q+cell.r*.5),8+size*1.5*cell.r);
        case FLAT_HEX->new Point(8+size*1.5*cell.q,8+size*ROOT3*(cell.r+cell.q*.5));
    };}
    public double edge(Cell cell,double x,double z){var c=centre(cell);x-=c.x;z-=c.z;return switch(shape){
        case SQUARE->size*.5-Math.max(Math.abs(x),Math.abs(z));
        case POINTY_HEX->size*ROOT3*.5-Math.max(Math.abs(x),Math.max(Math.abs(x*.5+z*ROOT3*.5),Math.abs(x*.5-z*ROOT3*.5)));
        case FLAT_HEX->size*ROOT3*.5-Math.max(Math.abs(z),Math.max(Math.abs(z*.5+x*ROOT3*.5),Math.abs(z*.5-x*ROOT3*.5)));
    };}
    public boolean contains(Cell cell,int x,int z){return at(x,z).equals(cell)&&edge(cell,x+.5,z+.5)>=gap;}
    public boolean rim(Cell cell,int x,int z){double e=edge(cell,x+.5,z+.5)-gap;return e>=0&&e<rim;}
    public Bounds bounds(Cell cell){var c=centre(cell);double rx=shape==Shape.SQUARE?size*.5:shape==Shape.POINTY_HEX?size*ROOT3*.5:size;
        double rz=shape==Shape.SQUARE?size*.5:shape==Shape.POINTY_HEX?size:size*ROOT3*.5;
        return new Bounds((int)StrictMath.floor(c.x-rx),(int)StrictMath.floor(c.z-rz),(int)StrictMath.ceil(c.x+rx),(int)StrictMath.ceil(c.z+rz));}
    public List<Cell> neighbours(Cell cell){int[][] shifts=shape==Shape.SQUARE?new int[][]{{0,-1},{1,0},{0,1},{-1,0}}:new int[][]{{1,0},{1,-1},{0,-1},{-1,0},{-1,1},{0,1}};return Arrays.stream(shifts).map(s->new Cell(cell.q+s[0],cell.r+s[1])).toList();}
    public int distance(Cell cell){return shape==Shape.SQUARE?Math.max(Math.abs(cell.q),Math.abs(cell.r)):Math.max(Math.abs(cell.q),Math.max(Math.abs(cell.r),Math.abs(cell.q+cell.r)));}
    public List<Cell> within(int radius){if(radius<0||radius>15)throw new IllegalArgumentException("Cell radius must be 0–15.");var cells=new ArrayList<Cell>();for(int q=-radius;q<=radius;q++)for(int r=-radius;r<=radius;r++){var cell=new Cell(q,r);if(distance(cell)<=radius)cells.add(cell);}cells.sort(Comparator.comparingInt(this::distance).thenComparing(Comparator.naturalOrder()));return List.copyOf(cells);}
    public enum Shape implements StringRepresentable {SQUARE,POINTY_HEX,FLAT_HEX;public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}}
    public record Point(double x,double z){}
    /** Exclusive maximum bounds. */
    public record Bounds(int minX,int minZ,int maxX,int maxZ){public int chunks(){return ((maxX-1>>4)-(minX>>4)+1)*((maxZ-1>>4)-(minZ>>4)+1);}}
    public record Cell(int q,int r) implements Comparable<Cell>{
        public Cell {if(q < -2_000_000||q > 2_000_000||r < -2_000_000||r > 2_000_000)throw new IllegalArgumentException("Cell coordinates must be within -2000000..2000000.");}
        public static final Cell ORIGIN=new Cell(0,0);
        public static final Codec<Cell> CODEC=RecordCodecBuilder.create(b->b.group(Codec.intRange(-2_000_000,2_000_000).fieldOf("q").forGetter(Cell::q),Codec.intRange(-2_000_000,2_000_000).fieldOf("r").forGetter(Cell::r)).apply(b,Cell::new));
        public String key(){return q+","+r;}
        public static Cell parse(String key){String[] parts=key.split(",",-1);if(parts.length!=2)throw new IllegalArgumentException("Use q,r coordinates.");return new Cell(Integer.parseInt(parts[0].trim()),Integer.parseInt(parts[1].trim()));}
        public int compareTo(Cell other){int first=Integer.compare(q,other.q);return first==0?Integer.compare(r,other.r):first;}
    }
}
