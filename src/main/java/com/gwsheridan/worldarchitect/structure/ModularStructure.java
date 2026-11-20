package com.gwsheridan.worldarchitect.structure;

import com.gwsheridan.worldarchitect.WorldArchitect;
import com.gwsheridan.worldarchitect.config.IslandTemplate;
import com.gwsheridan.worldarchitect.worldgen.SolidColumnGenerator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** A bounded assembly of embedded library templates using the native structure lifecycle. */
public final class ModularStructure extends Structure {
    static final int MAX_MODULES = 16;
    static final int MAX_SPAN = 96;
    static final int MAX_BLOCKS = 65_536;
    public static final MapCodec<ModularStructure> CODEC = RecordCodecBuilder.<ModularStructure>mapCodec(builder -> builder.group(
            settingsCodec(builder),
            Module.CODEC.listOf(1, MAX_MODULES).fieldOf("modules").forGetter(structure -> structure.modules),
            Codec.intRange(-64, 64).optionalFieldOf("y_offset", 0).forGetter(structure -> structure.yOffset),
            Codec.intRange(0, 32).optionalFieldOf("max_slope", 4).forGetter(structure -> structure.maxSlope),
            Codec.BOOL.optionalFieldOf("random_rotation", true).forGetter(structure -> structure.randomRotation),
            ProceduralPool.CODEC.optionalFieldOf("assembly_pool").forGetter(structure -> structure.pool),
            PlacementAnchor.CODEC.optionalFieldOf("placement").forGetter(structure -> structure.anchor)
    ).apply(builder, ModularStructure::new)).validate(ModularStructure::validate);

    private final List<Module> modules;
    private final Optional<ProceduralPool> pool;
    private final Optional<PlacementAnchor> anchor;
    private final ThreadLocal<List<Module>> selectedLayout = new ThreadLocal<>();
    private final int yOffset;
    private final int maxSlope;
    private final boolean randomRotation;
    private final BlockPos size;

    ModularStructure(StructureSettings settings, List<Module> modules, int yOffset, int maxSlope, boolean randomRotation) { this(settings,modules,yOffset,maxSlope,randomRotation,Optional.empty()); }

    ModularStructure(StructureSettings settings, List<Module> modules, int yOffset, int maxSlope, boolean randomRotation, Optional<ProceduralPool> pool) {
        this(settings,modules,yOffset,maxSlope,randomRotation,pool,Optional.empty());
    }
    ModularStructure(StructureSettings settings, List<Module> modules, int yOffset, int maxSlope, boolean randomRotation, Optional<ProceduralPool> pool,Optional<PlacementAnchor> anchor) {
        super(settings);
        this.anchor=anchor;
        this.pool = pool;
        this.modules = List.copyOf(modules);
        this.yOffset = yOffset;
        this.maxSlope = maxSlope;
        this.randomRotation = randomRotation;
        int x = 0;
        int y = 0;
        int z = 0;
        for (var module : modules) {
            BlockPos end = module.offset().offset(module.entry().template().size());
            x = Math.max(x, end.getX());
            y = Math.max(y, end.getY());
            z = Math.max(z, end.getZ());
        }
        size = new BlockPos(x, y, z);
    }

    private DataResult<ModularStructure> validate() {
        if(anchor.isPresent()&&anchor.orElseThrow().validate().error().isPresent())return DataResult.error(()->"Invalid placement anchor.");
        if (pool.isEmpty() && (size.getX() > MAX_SPAN || size.getY() > MAX_SPAN || size.getZ() > MAX_SPAN)
                || modules.stream().mapToInt(module -> module.entry().template().blocks().size()).sum() > MAX_BLOCKS) {
            return DataResult.error(() -> "A modular structure may span at most 96 blocks per axis and contain 65,536 blocks.");
        }
        if (modules.stream().map(Module::name).distinct().count() != modules.size()) {
            return DataResult.error(() -> "Module names must be unique.");
        }
        if (pool.isPresent()) {
            if (terrainAdaptation() != net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE) return DataResult.error(() -> "Pool structures require terrain_adaptation none.");
            return pool.orElseThrow().validateCatalog(modules).map(ignored -> this);
        }
        var occupied = new HashSet<BlockPos>();
        for (var module : modules) {
            for (var cell : module.entry().template().blocks()) {
                if (!occupied.add(module.offset().offset(cell.pos()))) {
                    return DataResult.error(() -> "Modules overlap at an occupied cell.");
                }
            }
        }
        for (var module : modules) {
            for (var marker : module.entry().markers().values()) {
                BlockPos pos = module.offset().offset(marker.pos());
                if (occupied.contains(pos) || occupied.contains(pos.above())) {
                    return DataResult.error(() -> "A module obstructs another module's spawn marker.");
                }
            }
        }
        if (terrainAdaptation() != net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE) {
            return DataResult.error(() -> "Modular structures require terrain_adaptation none.");
        }
        return DataResult.success(this);
    }

    public void validateTemplates(net.minecraft.core.HolderLookup.Provider registries) throws IOException {
        if (pool.isPresent()) pool.orElseThrow().validateCatalog(modules).getOrThrow(IOException::new);
        for (var module : modules) {
            for (var cell : module.entry().template().blocks()) {
                TemplateMetadata.sanitize(cell.state(), cell.metadata(), registries);
            }
        }
    }

    public int horizontalSpan() {
        return pool.isPresent() ? MAX_SPAN : Math.max(size.getX(), size.getZ());
    }

    public int blockCount() { return pool.isPresent() ? Math.min(MAX_BLOCKS, modules.stream().mapToInt(m -> m.entry().template().blocks().size()).max().orElse(0) * pool.orElseThrow().maxModules()) : modules.stream().mapToInt(module -> module.entry().template().blocks().size()).sum(); }
    String placementDescription(){return anchor.map(a->a.mode().getSerializedName()+(a.mode()==PlacementAnchor.Mode.FIXED?" Y "+a.fixedY():"")).orElse("legacy seabed")+"; height offset "+yOffset;}
    boolean rootAnchored(){return pool.filter(p->p.format()==3).isPresent();}
    public boolean hasMarker(String module, String marker) {
        if (pool.isEmpty()) return modules.stream().anyMatch(part -> part.name().equals(module) && part.entry().markers().containsKey(marker));
        if (module.equals("root")) return modules.stream().anyMatch(part -> part.name().equals(pool.orElseThrow().root()) && part.entry().markers().containsKey(marker));
        return module.matches("module_([1-9]|1[0-5])") && Integer.parseInt(module.substring(7)) < pool.orElseThrow().maxModules() && modules.stream().anyMatch(part -> part.entry().markers().containsKey(marker));
    }
    Optional<List<Module>> previewLayout(long seed) throws IOException { return pool.isPresent() ? pool.orElseThrow().layout(modules, seed) : Optional.of(modules); }
    ProceduralPool.LayoutReport previewReport(long seed)throws IOException{return pool.isPresent()?pool.orElseThrow().plan(modules,seed):new ProceduralPool.LayoutReport(Optional.of(modules),0,0,Map.of(),List.of("Static assembly."));}

    @Override
    public StructureStart generate(Holder<Structure> selected, ResourceKey<Level> dimension, RegistryAccess registries,
            ChunkGenerator generator, BiomeSource biomes, Climate.Sampler sampler, RandomState random,
            StructureTemplateManager templates, long seed, ChunkPos chunk, int references,
            LevelHeightAccessor height, java.util.function.Predicate<Holder<Biome>> validBiome) {
        if (generator instanceof SolidColumnGenerator solid && !com.gwsheridan.worldarchitect.worldgen.RuleExecution.allowsTemplate(solid, selected, seed, chunk, height, random)) {
            SiteProbe.reject("Rule-engine selection or candidate spacing rejected this chunk.");
            return StructureStart.INVALID_START;
        }
        List<Module> layout;
        try { layout = pool.isPresent() ? pool.orElseThrow().layout(modules, ProceduralPool.seed(seed,chunk.x(),chunk.z())).orElse(null) : modules; }
        catch(IOException e) { throw new IllegalStateException("Invalid pool template",e); }
        if (layout == null) {SiteProbe.reject("Pool requirements, connections or bounded layout search rejected this seed.");return StructureStart.INVALID_START;}
        if (layout.stream().anyMatch(module -> module.entry().rules().dimension().filter(id -> !id.equals(dimension.identifier())).isPresent())) {
            SiteProbe.reject("A module requires a different dimension.");
            return StructureStart.INVALID_START;
        }
        if (pool.isEmpty()) return super.generate(selected, dimension, registries, generator, biomes, sampler, random, templates, seed, chunk, references, height, validBiome);
        var previous = selectedLayout.get(); selectedLayout.set(layout);
        try { return super.generate(selected, dimension, registries, generator, biomes, sampler, random, templates, seed, chunk, references, height, validBiome); }
        finally { if(previous==null)selectedLayout.remove();else selectedLayout.set(previous); }
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!(context.chunkGenerator() instanceof SolidColumnGenerator generator)) {
            return Optional.empty();
        }
        List<Module> layout = pool.isPresent() ? selectedLayout.get() : modules;
        if (layout == null) { try { layout = previewLayout(ProceduralPool.seed(context.seed(),context.chunkPos().x(),context.chunkPos().z())).orElse(null); } catch(IOException e) { throw new IllegalStateException(e); } }
        if (layout == null) return Optional.empty();
        BlockPos layoutSize = pool.isPresent() ? new BlockPos(layout.stream().mapToInt(m->m.offset().getX()+m.entry().template().size().getX()).max().orElse(1),layout.stream().mapToInt(m->m.offset().getY()+m.entry().template().size().getY()).max().orElse(1),layout.stream().mapToInt(m->m.offset().getZ()+m.entry().template().size().getZ()).max().orElse(1)) : size;
        int x = context.chunkPos().getMinBlockX();
        int z = context.chunkPos().getMinBlockZ();
        Rotation rotation = randomRotation ? Rotation.getRandom(context.random()) : Rotation.NONE;
        var transform = new TemplatePlacement(BlockPos.ZERO, rotation, Mirror.NONE);
        int seabed=generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
        int y=anchor.map(a->a.resolve(generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState()),seabed,yOffset)).orElse(seabed+yOffset);
        BlockPos origin = new BlockPos(x, y, z);
        if(pool.filter(p->p.format()==3).isPresent())origin=origin.subtract(transformedOffset(layout.stream().filter(m->m.name().equals("root")).findFirst().orElseThrow(),layoutSize,transform));
        var pieces = new ArrayList<ModularPiece>(layout.size());
        try {
            for (var module : layout) {
                SiteProbe.check();
                LibraryEntry transformed = module.entry().transformed(transform);
                BlockPos offset = transformedOffset(module,layoutSize,transform);
                pieces.add(new ModularPiece(module.name(), origin.offset(offset), transformed));
            }
            SiteProbe.geometry(origin,pieces,layoutSize,transform);
            if (!accepts(context, generator, pieces)) {
                return Optional.empty();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Invalid saved generation template", e);
        }
        return Optional.of(new GenerationStub(origin, builder -> pieces.forEach(builder::addPiece)));
    }

    private boolean accepts(GenerationContext context, SolidColumnGenerator generator, List<ModularPiece> pieces) throws IOException {
        var columns = new HashMap<Long, NoiseColumn>();
        var occupied = new HashMap<BlockPos, BlockState>();
        var planned=new HashMap<BlockPos,BlockState>();
        if(pool.filter(p->p.format()==3).isPresent())for(var piece:pieces)for(var cell:piece.entry().template().blocks())planned.put(piece.origin().offset(cell.pos()),cell.state());
        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        for (var piece : pieces) {
            var entry = piece.entry();
            var rules = entry.rules();
            var bottom = new HashMap<Long, BlockPos>();
            for (var cell : entry.template().blocks()) {
                SiteProbe.check();
                BlockPos pos = piece.origin().offset(cell.pos());
                if((anchor.isPresent()||pool.filter(p->p.format()==3).isPresent())&&generator.endProgression().isPresent()
                        &&!com.gwsheridan.worldarchitect.config.EndProgression.allowsGenerationWrite(context.chunkPos().getMinBlockX(),context.chunkPos().getMinBlockZ(),pos.getX(),pos.getZ()))return SiteProbe.reject("A template cell crosses the native End arena reservation.");
                if (!com.gwsheridan.worldarchitect.worldgen.RuleExecution.templateCell(generator, this, context.seed(), pos)) return SiteProbe.reject("A template cell falls outside its height, biome or region rule at "+pos.toShortString());
                if (!context.heightAccessor().isInsideBuildHeight(pos) || pos.getY() < rules.minY() || pos.getY() > rules.maxY()
                        || Math.abs((long) pos.getX()) >= 29_999_984 || Math.abs((long) pos.getZ()) >= 29_999_984) {
                    return SiteProbe.reject("A template cell exceeds its allowed height or world bounds at "+pos.toShortString());
                }
                Holder<Biome> biome = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(pos.getX()),
                        QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
                if (!context.validBiome().test(biome) || rules.biome().filter(id -> !biome.is(id)).isPresent()) {
                    return SiteProbe.reject("A template cell fails its biome restrictions at "+pos.toShortString());
                }
                NoiseColumn column = column(context, generator, columns, pos);
                BlockState existing = column.getBlock(pos.getY());
                if (!rules.collision().allows(existing,cell.state())) {
                    return SiteProbe.reject("Collision with "+existing.getBlock().getName().getString()+" at "+pos.toShortString());
                }
                if (rules.terrain() == TemplateRules.Terrain.BURIED && pos.getY() >= generator.getBaseHeight(pos.getX(), pos.getZ(),
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()) - 1) {
                    return SiteProbe.reject("Buried placement requires terrain above "+pos.toShortString());
                }
                if (rules.terrain() == TemplateRules.Terrain.SUPPORTED) {
                    bottom.merge(columnKey(pos), pos, (a, b) -> a.getY() < b.getY() ? a : b);
                }
                TemplateMetadata.sanitize(cell.state(), cell.metadata(), context.registryAccess());
                occupied.put(pos, cell.state());
            }
            for (var pos : bottom.values()) {
                NoiseColumn column = column(context, generator, columns, pos);
                if (!IslandTemplate.safeFloor(planned.getOrDefault(pos.below(),column.getBlock(pos.getY() - 1)), net.minecraft.world.level.EmptyBlockGetter.INSTANCE, pos.below())) {
                    return SiteProbe.reject("Supported placement needs a safe floor below "+pos.toShortString());
                }
                int surface = generator.getBaseHeight(pos.getX(), pos.getZ(), Heightmap.Types.OCEAN_FLOOR_WG,
                        context.heightAccessor(), context.randomState());
                minSurface = Math.min(minSurface, surface);
                maxSurface = Math.max(maxSurface, surface);
            }
        }
        if (maxSurface != Integer.MIN_VALUE && maxSurface - minSurface > maxSlope) {
            return SiteProbe.reject("Supported terrain exceeds the maximum slope.");
        }
        for (var piece : pieces) {
            for (var marker : piece.entry().markers().values()) {
                BlockPos pos = piece.origin().offset(marker.pos());
                if (!context.heightAccessor().isInsideBuildHeight(pos.above()) || occupied.containsKey(pos) || occupied.containsKey(pos.above())) {
                    return SiteProbe.reject("A spawn marker exceeds build height or is blocked by an assembly cell at "+pos.toShortString());
                }
                NoiseColumn column = column(context, generator, columns, pos);
                if (!column.getBlock(pos.getY()).isAir() || !column.getBlock(pos.getY() + 1).isAir()) {
                    return SiteProbe.reject("A spawn marker requires two air cells at "+pos.toShortString());
                }
            }
        }
        return true;
    }

    private static NoiseColumn column(GenerationContext context, SolidColumnGenerator generator, Map<Long, NoiseColumn> cache, BlockPos pos) {
        return cache.computeIfAbsent(columnKey(pos), ignored -> generator.baseColumn(pos.getX(), pos.getZ(), context.heightAccessor(), context.seed()));
    }

    private static long columnKey(BlockPos pos) {
        return BlockPos.asLong(pos.getX(), 0, pos.getZ());
    }
    private static BlockPos transformedOffset(Module module,BlockPos layoutSize,TemplatePlacement transform){
        BlockPos first=transform.position(layoutSize,module.offset()),last=transform.position(layoutSize,module.offset().offset(module.entry().template().size()).offset(-1,-1,-1));
        return new BlockPos(Math.min(first.getX(),last.getX()),first.getY(),Math.min(first.getZ(),last.getZ()));
    }

    @Override
    public StructureType<?> type() {
        return WorldArchitect.MODULAR_STRUCTURE.get();
    }

    record Module(String name, BlockPos offset, LibraryEntry entry) {
        static final Codec<Module> CODEC = RecordCodecBuilder.<Module>create(builder -> builder.group(
                Codec.STRING.validate(name -> name.matches("[a-z0-9_-]{1,32}") ? DataResult.success(name)
                        : DataResult.error(() -> "Module names need 1–32 lowercase letters, digits, underscores or hyphens."))
                        .fieldOf("name").forGetter(Module::name),
                BlockPos.CODEC.validate(pos -> pos.getX() >= 0 && pos.getY() >= 0 && pos.getZ() >= 0
                        && pos.getX() < MAX_SPAN && pos.getY() < MAX_SPAN && pos.getZ() < MAX_SPAN
                        ? DataResult.success(pos) : DataResult.error(() -> "Module offsets must lie between 0 and 95 on each axis."))
                        .fieldOf("offset").forGetter(Module::offset),
                LibraryEntry.CODEC.fieldOf("template").forGetter(Module::entry)
        ).apply(builder, Module::new));
    }
}
