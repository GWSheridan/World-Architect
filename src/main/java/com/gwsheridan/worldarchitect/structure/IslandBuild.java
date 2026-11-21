package com.gwsheridan.worldarchitect.structure;

import com.gwsheridan.worldarchitect.config.IslandTemplate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable island asset. Inventories are explicit saved content, never join rewards. */
public record IslandBuild(BlockPos size, Map<String, BlockPos> markers, String spawnMarker, List<Cell> cells) {
    public static final int MAX_SIDE = 64, MAX_CELLS = 65536;
    public static final Codec<IslandBuild> CODEC = RecordCodecBuilder.<IslandBuild>create(b -> b.group(
            Codec.intRange(1, 1).fieldOf("format").forGetter(v -> 1),
            BlockPos.CODEC.fieldOf("size").forGetter(IslandBuild::size),
            Codec.unboundedMap(Codec.STRING, BlockPos.CODEC).fieldOf("markers").forGetter(IslandBuild::markers),
            Codec.STRING.fieldOf("spawn_marker").forGetter(IslandBuild::spawnMarker),
            Cell.CODEC.listOf(1, MAX_CELLS).fieldOf("blocks").forGetter(IslandBuild::cells)
    ).apply(b, (version, size, markers, spawn, cells) -> new IslandBuild(size, markers, spawn, cells))).validate(IslandBuild::validate);

    public IslandBuild {
        size = size.immutable();
        var copy = new TreeMap<String, BlockPos>();
        markers.forEach((name, pos) -> copy.put(name, pos.immutable()));
        markers = Collections.unmodifiableMap(copy);
        cells = List.copyOf(cells);
    }

    public BlockPos spawn() { return markers.get(spawnMarker); }

    public DataResult<IslandBuild> validate() {
        try {
            if (size.getX()<1||size.getY()<3||size.getZ()<1||size.getX()>MAX_SIDE||size.getY()>MAX_SIDE||size.getZ()>MAX_SIDE||cells.isEmpty()||cells.size()>MAX_CELLS) throw new IllegalArgumentException("An island must fit 64 × 64 × 64 and 65,536 occupied cells.");
            if (markers.isEmpty()||markers.size()>32||!markers.containsKey(spawnMarker)) throw new IllegalArgumentException("Choose a saved island spawn marker.");
            var states = new HashMap<BlockPos, BlockState>();
            int inventories=0;
            for (var cell : cells) {
                if (!NativeTemplate.inside(size,cell.pos())||cell.state().isAir()||TemplateMetadata.restricted(cell.state())||cell.state().is(Blocks.STRUCTURE_VOID)||cell.state().is(Blocks.NETHER_PORTAL)||cell.state().is(Blocks.END_PORTAL)||states.putIfAbsent(cell.pos(),cell.state())!=null) throw new IllegalArgumentException("Invalid or duplicate island cell at "+cell.pos().toShortString());
                checkMetadata(cell);
                if (cell.data().filter(t->!t.getListOrEmpty("Items").isEmpty()).isPresent() && ++inventories>64) throw new IllegalArgumentException("At most 64 starter containers are allowed.");
            }
            for (var cell:cells) if(cell.state().getBlock() instanceof FallingBlock) {
                var support=states.get(cell.pos().below());
                if(support==null||!IslandTemplate.safeFloor(support,net.minecraft.world.level.EmptyBlockGetter.INSTANCE,cell.pos().below())) throw new IllegalArgumentException("Support the falling block at "+cell.pos().toShortString());
            }
            for(var entry:markers.entrySet()) {
                var pos=entry.getValue();var floor=states.get(pos.below());
                if(!entry.getKey().matches("[a-z][a-z0-9_-]{0,31}")||!NativeTemplate.inside(size,pos.above())||floor==null||states.containsKey(pos)||states.containsKey(pos.above())||!IslandTemplate.safeFloor(floor,net.minecraft.world.level.EmptyBlockGetter.INSTANCE,pos.below())) throw new IllegalArgumentException("Marker "+entry.getKey()+" needs two air cells above a safe floor inside the island.");
            }
            return DataResult.success(this);
        } catch(IllegalArgumentException error) { return DataResult.error(error::getMessage); }
    }

    private static void checkMetadata(Cell cell) {
        if(cell.state().hasBlockEntity()&&!container(cell.state())&&!sign(cell.state())&&!net.minecraft.world.level.block.entity.BlockEntityTypes.BANNER.isValid(cell.state())) throw new IllegalArgumentException("Unsupported island block entity: "+cell.state());
        if(cell.data().isEmpty())return;
        var tag=cell.data().orElseThrow();
        if(!cell.state().hasBlockEntity()) throw new IllegalArgumentException("Metadata supplied for a block without a block entity.");
        String expected=tag.getStringOr("id","");var id=Identifier.tryParse(expected);var type=id==null?null:BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
        if(type==null||!type.isValid(cell.state()))throw new IllegalArgumentException("Island block entity does not match its block state.");
        var allowed=container(cell.state())?Set.of("id","CustomName","Items"):sign(cell.state())?Set.of("id","front_text","back_text","is_waxed"):Set.of("id","patterns","CustomName");
        if(!allowed.containsAll(tag.keySet()))throw new IllegalArgumentException("Unsupported island metadata field at "+cell.pos().toShortString());
        var slots=new BitSet(27);
        for(var value:tag.getListOrEmpty("Items")) {
            if(!(value instanceof CompoundTag item))throw new IllegalArgumentException("Invalid starter item.");
            int slot=item.getIntOr("Slot",-1);
            if(slot<0||slot>=27||slots.get(slot))throw new IllegalArgumentException("Invalid or duplicate starter-container slot.");
            slots.set(slot);
        }
    }

    public IslandBuild checked(HolderLookup.Provider registries) throws IOException {
        validate().getOrThrow(IOException::new);
        var result=new ArrayList<Cell>();var ops=RegistryOps.create(NbtOps.INSTANCE,registries);
        for(var cell:cells) {
            CompoundTag raw=cell.data().orElse(null), safe=null;
            if(raw!=null) {
                if(cell.state().is(Blocks.BARREL)) {safe=new CompoundTag();safe.putString("id","minecraft:barrel");if(raw.contains("CustomName"))safe.putString("CustomName",net.minecraft.network.chat.ComponentSerialization.CODEC.parse(ops,raw.get("CustomName")).getOrThrow(IOException::new).getString());}
                else safe=TemplateMetadata.sanitize(cell.state(),raw,registries);
                if(container(cell.state())) {
                    if(safe==null)safe=new CompoundTag();
                    safe.putString("id",cell.state().is(Blocks.BARREL)?"minecraft:barrel":cell.state().is(Blocks.TRAPPED_CHEST)?"minecraft:trapped_chest":"minecraft:chest");
                    var items=new ListTag();
                    for(var value:raw.getListOrEmpty("Items")) {
                        var item=(CompoundTag)value;var stack=ItemStack.CODEC.parse(ops,item).getOrThrow(IOException::new);
                        if(stack.isEmpty()||stack.getCount()>stack.getMaxStackSize())throw new IOException("Invalid starter stack at "+cell.pos().toShortString());
                        var encoded=(CompoundTag)ItemStack.CODEC.encodeStart(ops,stack).getOrThrow(IOException::new);encoded.putByte("Slot",(byte)item.getIntOr("Slot",-1));items.add(encoded);
                    }
                    safe.put("Items",items);
                }
            }
            result.add(new Cell(cell.pos(),cell.state(),Optional.ofNullable(safe)));
        }
        return new IslandBuild(size,markers,spawnMarker,result);
    }

    public static boolean container(BlockState state) {return state.is(Blocks.CHEST)||state.is(Blocks.TRAPPED_CHEST)||state.is(Blocks.BARREL);}
    private static boolean sign(BlockState state){return net.minecraft.world.level.block.entity.BlockEntityTypes.SIGN.isValid(state)||net.minecraft.world.level.block.entity.BlockEntityTypes.HANGING_SIGN.isValid(state);}

    public static IslandBuild classic() {
        var old=IslandTemplate.classic();
        return new IslandBuild(old.size(),Map.of("spawn",old.spawn()),"spawn",old.blocks().stream().map(c->new Cell(c.pos(),c.state(),Optional.empty())).toList());
    }

    public static IslandBuild importNative(CompoundTag tag, BlockPos marker, boolean inventories, HolderLookup.Provider registries) throws IOException {
        if(!tag.getListOrEmpty("entities").isEmpty())throw new IOException("Island imports do not support entities.");
        var nativeBuild=NativeTemplate.readIsland(tag);var cells=new ArrayList<Cell>();
        for(var cell:nativeBuild.blocks())if(cell.isSolidContent()) {
            var data=importMetadata(cell.metadata(),inventories);
            cells.add(new Cell(cell.pos(),cell.state(),Optional.ofNullable(data)));
        }
        return new IslandBuild(nativeBuild.size(),Map.of("spawn",marker),"spawn",cells).checked(registries);
    }

    /** Minecraft writes an empty component map even for ordinary decorative block entities. */
    static CompoundTag importMetadata(CompoundTag raw,boolean contents)throws IOException{
        if(raw==null)return null;var data=raw.copy();for(String field:List.of("x","y","z"))data.remove(field);
        if(data.contains("components")){if(data.getCompound("components").filter(CompoundTag::isEmpty).isEmpty())throw new IOException("Island imports do not support additional block-entity components.");data.remove("components");}
        if(!contents)data.remove("Items");return data;
    }

    public record Cell(BlockPos pos, BlockState state, Optional<CompoundTag> data) {
        private static final Codec<BlockState> STATE=Codec.STRING.comapFlatMap(text->{try{return DataResult.success(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,text,false).blockState());}catch(Exception e){return DataResult.error(()->"Unknown island block state: "+text);}},BlockStateParser::serialize);
        public static final Codec<Cell> CODEC=RecordCodecBuilder.create(b->b.group(BlockPos.CODEC.fieldOf("pos").forGetter(Cell::pos),STATE.fieldOf("state").forGetter(Cell::state),CompoundTag.CODEC.optionalFieldOf("data").forGetter(Cell::data)).apply(b,Cell::new));
        public Cell {pos=pos.immutable();data=data.map(CompoundTag::copy);}
        @Override public Optional<CompoundTag> data(){return data.map(CompoundTag::copy);}
    }
}
