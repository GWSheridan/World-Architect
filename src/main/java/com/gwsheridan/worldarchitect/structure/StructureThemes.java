package com.gwsheridan.worldarchitect.structure;

import com.gwsheridan.worldarchitect.config.ExactInt;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Embedded, versioned snapshots. Palette and variant RNG never consumes a pool planner's RNG. */
public record StructureThemes(List<Palette> palettes,List<Variant> variants) {
    public StructureThemes {palettes=List.copyOf(palettes);variants=List.copyOf(variants);}
    public static final Codec<StructureThemes> CODEC=RecordCodecBuilder.<StructureThemes>create(b->b.group(ExactInt.range(1,1).fieldOf("version").forGetter(t->1),Palette.CODEC.listOf(0,8).optionalFieldOf("palettes",List.of()).forGetter(StructureThemes::palettes),Variant.CODEC.listOf(0,16).optionalFieldOf("variants",List.of()).forGetter(StructureThemes::variants)).apply(b,(version,palettes,variants)->new StructureThemes(palettes,variants))).validate(StructureThemes::validate);
    public record Mapping(Identifier from,Identifier to,boolean preserveProperties){
        public static final Codec<Mapping> CODEC=RecordCodecBuilder.create(b->b.group(Identifier.CODEC.fieldOf("from").forGetter(Mapping::from),Identifier.CODEC.fieldOf("to").forGetter(Mapping::to),Codec.BOOL.optionalFieldOf("preserve_properties",true).forGetter(Mapping::preserveProperties)).apply(b,Mapping::new));
        BlockState apply(BlockState state)throws IOException{
            if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(from))return state;
            BlockState output=block(to);if(TemplateMetadata.restricted(output))throw new IOException("A palette targets a restricted block.");
            if((state.hasBlockEntity()||output.hasBlockEntity())&&state.getBlock()!=output.getBlock())throw new IOException("Palette mappings cannot change a block-entity type.");
            if(preserveProperties)for(var property:state.getProperties()){var target=output.getBlock().getStateDefinition().getProperty(property.getName());if(target==null)throw new IOException("Target "+to+" has no compatible property "+property.getName());output=transfer(output,target,value(property,state));}
            return output;
        }
    }
    public record Palette(String name,int weight,List<Mapping> mappings){
        public Palette {mappings=List.copyOf(mappings);}
        public static final Codec<Palette> CODEC=RecordCodecBuilder.create(b->b.group(Codec.STRING.fieldOf("name").forGetter(Palette::name),ExactInt.range(1,100).fieldOf("weight").forGetter(Palette::weight),Mapping.CODEC.listOf(1,32).fieldOf("mappings").forGetter(Palette::mappings)).apply(b,Palette::new));
    }
    public record Variant(String name,String module,int weight,Identifier source,LibraryEntry snapshot){
        public static final Codec<Variant> CODEC=RecordCodecBuilder.create(b->b.group(Codec.STRING.fieldOf("name").forGetter(Variant::name),Codec.STRING.fieldOf("module").forGetter(Variant::module),ExactInt.range(1,100).fieldOf("weight").forGetter(Variant::weight),Identifier.CODEC.fieldOf("source").forGetter(Variant::source),LibraryEntry.CODEC.fieldOf("snapshot").forGetter(Variant::snapshot)).apply(b,Variant::new));
    }
    public DataResult<StructureThemes> validate(){
        if(palettes.size()>8||variants.size()>16||palettes.isEmpty()&&variants.isEmpty()||palettes.stream().map(Palette::name).distinct().count()!=palettes.size()||variants.stream().map(Variant::name).distinct().count()!=variants.size())return DataResult.error(()->"Use up to eight uniquely named palettes and sixteen uniquely named variants.");
        for(var p:palettes)if(!name(p.name())||p.weight()<1||p.weight()>100||p.mappings().isEmpty()||p.mappings().size()>32||p.mappings().stream().map(Mapping::from).distinct().count()!=p.mappings().size())return DataResult.error(()->"A palette needs a unique name, weight 1–100 and 1–32 distinct source blocks.");
        for(var v:variants)if(!name(v.name())||!name(v.module())||v.weight()<1||v.weight()>100)return DataResult.error(()->"A variant needs valid names and weight 1–100.");return DataResult.success(this);
    }
    private static boolean name(String name){return name.matches("[a-z0-9_-]{1,32}");}
    private static BlockState block(Identifier id)throws IOException{try{return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,id.toString(),false).blockState();}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IOException("Missing palette block "+id,e);}}
    private static <T extends Comparable<T>> String value(Property<T> property,BlockState state){return property.getName(state.getValue(property));}
    private static <T extends Comparable<T>> BlockState transfer(BlockState state,Property<T> property,String value)throws IOException{return state.setValue(property,property.getValue(value).orElseThrow(()->new IOException("Incompatible palette property "+property.getName()+"="+value)));}
    private LibraryEntry palette(LibraryEntry entry,Palette palette)throws IOException{
        var mappings=new HashMap<Identifier,Mapping>();palette.mappings().forEach(m->mappings.put(m.from(),m));var cells=new ArrayList<NativeTemplate.Cell>();
        for(var cell:entry.template().blocks()){SiteProbe.check();var mapping=mappings.get(BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()));var state=mapping==null?cell.state():mapping.apply(cell.state());if(cell.metadata()!=null&&state.getBlock()!=cell.state().getBlock())throw new IOException("Saved metadata requires the original block type.");if(TemplateMetadata.restricted(state))throw new IOException("Unsupported themed template block.");cells.add(new NativeTemplate.Cell(cell.pos(),state,cell.metadata()==null?null:cell.metadata().copy()));}
        return LibraryEntry.read(new NativeTemplate(entry.template().size(),cells),entry.toTag(),entry.title(),entry.author());
    }
    void validateCatalog(List<ModularStructure.Module> modules,Optional<ProceduralPool> pool,net.minecraft.core.HolderLookup.Provider registries)throws IOException{
        validate().getOrThrow(IOException::new);long cells=modules.stream().mapToLong(m->m.entry().template().blocks().size()).sum()+variants.stream().mapToLong(v->v.snapshot().template().blocks().size()).sum();if(cells>ModularStructure.MAX_BLOCKS)throw new IOException("The complete base and variant catalog exceeds 65,536 cells.");
        for(var p:palettes)for(var m:p.mappings()){var from=block(m.from());m.apply(from);if(TemplateMetadata.restricted(from))throw new IOException("A palette references a restricted source block.");}
        var entries=new ArrayList<LibraryEntry>();modules.forEach(m->entries.add(m.entry()));
        for(var v:variants){var module=modules.stream().filter(m->m.name().equals(v.module())).findFirst().orElseThrow(()->new IOException("Variant references missing module "+v.module()));if(!module.entry().template().size().equals(v.snapshot().template().size())||!v.snapshot().markers().keySet().containsAll(module.entry().markers().keySet()))throw new IOException("Variants must retain module bounds and every base marker name.");v.snapshot().template().sanitized(registries);var catalog=new ArrayList<>(modules);catalog.set(modules.indexOf(module),new ModularStructure.Module(module.name(),module.offset(),v.snapshot()));if(pool.isPresent())pool.orElseThrow().validateCatalog(catalog).getOrThrow(IOException::new);entries.add(v.snapshot());}
        for(var p:palettes)for(var entry:entries)palette(entry,p).template().sanitized(registries);
    }
    List<ModularStructure.Module> catalog(List<ModularStructure.Module> base,long seed)throws IOException{
        var random=RandomSource.create(seed^0x53C865A36B2944D1L);Palette palette=palettes.isEmpty()?null:choose(palettes,random,Palette::weight);var result=new ArrayList<ModularStructure.Module>();
        for(var module:base){var options=variants.stream().filter(v->v.module().equals(module.name())).toList();var entry=options.isEmpty()?module.entry():choose(options,random,Variant::weight).snapshot();if(palette!=null)entry=palette(entry,palette);result.add(new ModularStructure.Module(module.name(),module.offset(),entry));}return List.copyOf(result);
    }
    private static <T> T choose(List<T> values,RandomSource random,java.util.function.ToIntFunction<T> weight){int roll=random.nextInt(values.stream().mapToInt(weight).sum());for(var v:values){roll-=weight.applyAsInt(v);if(roll<0)return v;}throw new IllegalStateException("Invalid theme weights.");}
    StructureThemes transform(List<AssemblyDraft.Part> parts)throws IOException{var result=new ArrayList<Variant>();for(var v:variants){var part=parts.stream().filter(p->p.name().equals(v.module())).findFirst().orElseThrow(()->new IOException("Missing variant module."));result.add(new Variant(v.name(),v.module(),v.weight(),v.source(),v.snapshot().transformed(new TemplatePlacement(BlockPos.ZERO,part.rotation(),part.mirror()))));}return new StructureThemes(palettes,result);}
    public Set<String> namespaces(){var names=new TreeSet<String>();for(var p:palettes)for(var m:p.mappings()){names.add(m.from().getNamespace());names.add(m.to().getNamespace());}for(var v:variants)for(var cell:v.snapshot().template().blocks())names.add(BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()).getNamespace());return Set.copyOf(names);}
}
