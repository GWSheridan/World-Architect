package com.gwsheridan.worldarchitect.modpack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/** Flattened authoring provenance and one immutable policy baseline; parents are never loaded at runtime. */
public record PresetInheritance(List<Parent> parents,Policy policy,JsonObject baseline,Map<String,String> resources) {
    public static final int MAX_DEPTH=8,MAX_BASELINE_BYTES=4*1024*1024;
    public PresetInheritance {parents=List.copyOf(parents);baseline=baseline.deepCopy();resources=Map.copyOf(resources);}
    @Override public JsonObject baseline(){return baseline.deepCopy();}
    public record Parent(Identifier preset,String release,String sha256) {
        public static final Codec<Parent> CODEC=RecordCodecBuilder.<Parent>create(b->b.group(Identifier.CODEC.fieldOf("preset").forGetter(Parent::preset),Codec.STRING.fieldOf("release").forGetter(Parent::release),Codec.STRING.fieldOf("sha256").forGetter(Parent::sha256)).apply(b,Parent::new)).validate(p->BundleFiles.safeId(p.preset)&&p.release.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,39}")&&p.sha256.matches("[a-f0-9]{64}")?DataResult.success(p):DataResult.error(()->"Invalid inherited preset identity or SHA-256."));
        String identity(){return preset+"/"+release;}
    }
    /** No nested manifest or inherited baseline: encoded nesting stays bounded. */
    public record Policy(BundleManifest.Lock lock,WorldOptions options,List<Identifier> dimensions,Map<Identifier,List<BundleManifest.Feature>> features,List<BundleManifest.Option> editableOptions,List<String> mods,boolean library,Optional<PackPolicy> pack) {
        public Policy(BundleManifest.Lock lock,WorldOptions options,List<Identifier> dimensions,Map<Identifier,List<BundleManifest.Feature>> features,List<BundleManifest.Option> editableOptions,List<String> mods,boolean library){this(lock,options,dimensions,features,editableOptions,mods,library,Optional.empty());}
        public Policy {dimensions=List.copyOf(dimensions);features=features.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,e->List.copyOf(e.getValue())));editableOptions=List.copyOf(editableOptions);mods=List.copyOf(mods);}
        static final Codec<Policy> CODEC=RecordCodecBuilder.create(b->b.group(
                net.minecraft.util.StringRepresentable.fromEnum(BundleManifest.Lock::values).fieldOf("lock").forGetter(Policy::lock),
                WorldOptions.CODEC.codec().fieldOf("options").forGetter(Policy::options),
                Identifier.CODEC.listOf(0,128).optionalFieldOf("editable_dimensions",List.of()).forGetter(Policy::dimensions),
                Codec.unboundedMap(Identifier.CODEC,net.minecraft.util.StringRepresentable.fromEnum(BundleManifest.Feature::values).listOf(1,BundleManifest.Feature.values().length)).optionalFieldOf("editable_features",Map.of()).forGetter(Policy::features),
                net.minecraft.util.StringRepresentable.fromEnum(BundleManifest.Option::values).listOf(0,3).optionalFieldOf("editable_options",List.of()).forGetter(Policy::editableOptions),
                Codec.STRING.listOf(0,256).optionalFieldOf("required_mods",List.of()).forGetter(Policy::mods),
                Codec.BOOL.optionalFieldOf("allow_structure_library",true).forGetter(Policy::library),
                PackPolicy.CODEC.optionalFieldOf("pack").forGetter(Policy::pack)).apply(b,Policy::new));
        static Policy of(BundleManifest m){return new Policy(m.lock(),m.options(),m.editableDimensions(),m.editableFeatures(),m.editableOptions(),m.requiredMods(),m.allowStructureLibrary(),m.pack());}
        BundleManifest manifest(Parent parent){return new BundleManifest(parent.preset(),"Inherited policy","Immutable parent policy","Modpack author",parent.release(),lock,options,dimensions,features,editableOptions,mods,library,Optional.empty(),pack);}
    }
    private static final Codec<JsonObject> OBJECT=Codec.PASSTHROUGH.comapFlatMap(dynamic->{
        var value=dynamic.convert(JsonOps.INSTANCE).getValue();
        if(!value.isJsonObject()||!bounded(value))return DataResult.error(()->"Inherited baselines must be objects within 4 MiB, 64 levels and 262,144 values.");
        return DataResult.success(value.getAsJsonObject());
    },value->new Dynamic<>(JsonOps.INSTANCE,value));
    public static final Codec<PresetInheritance> CODEC=RecordCodecBuilder.<PresetInheritance>create(b->b.group(
            Codec.intRange(1,1).fieldOf("version").forGetter(p->1),Parent.CODEC.listOf(1,MAX_DEPTH).fieldOf("parents").forGetter(PresetInheritance::parents),
            Policy.CODEC.fieldOf("policy").forGetter(PresetInheritance::policy),OBJECT.fieldOf("baseline").forGetter(PresetInheritance::baseline),
            Codec.unboundedMap(Codec.STRING,Codec.STRING).optionalFieldOf("resource_hashes",Map.of()).forGetter(PresetInheritance::resources)
    ).apply(b,(version,parents,policy,baseline,resources)->new PresetInheritance(parents,policy,baseline,resources))).validate(PresetInheritance::validate);
    private record Node(JsonElement value,int depth) {}
    private static boolean bounded(JsonElement value){return bounded(value,MAX_BASELINE_BYTES);}
    private static boolean bounded(JsonElement value,int maximumBytes){
        var queue=new ArrayDeque<Node>();queue.add(new Node(value,0));int count=0;
        while(!queue.isEmpty()){var node=queue.removeLast();if(node.depth()>64||++count>262144)return false;var next=node.value();if(next.isJsonObject())next.getAsJsonObject().entrySet().forEach(e->queue.add(new Node(e.getValue(),node.depth()+1)));else if(next.isJsonArray())next.getAsJsonArray().forEach(e->queue.add(new Node(e,node.depth()+1)));}
        return value.toString().getBytes(StandardCharsets.UTF_8).length<=maximumBytes;
    }
    private DataResult<PresetInheritance> validate(){
        if(parents.isEmpty()||parents.size()>MAX_DEPTH||parents.stream().map(Parent::identity).distinct().count()!=parents.size()||!bounded(baseline)||!baseline.has("dimensions")||!baseline.get("dimensions").isJsonObject()||resources.size()>BundleFiles.MAX_TEMPLATES)return DataResult.error(()->"Invalid, cyclic or oversized inherited preset baseline.");
        for(var parent:parents)if(Parent.CODEC.encodeStart(JsonOps.INSTANCE,parent).error().isPresent())return DataResult.error(()->"Invalid parent identity.");
        if(resources.entrySet().stream().anyMatch(e->!(BundleFiles.templatePath(e.getKey())||BundleFiles.modularPath(e.getKey()))||!e.getValue().matches("[a-f0-9]{64}")))return DataResult.error(()->"Invalid inherited resource fingerprint.");
        var parentManifest=policy.manifest(parents.getFirst());var checked=BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,parentManifest);if(checked.error().isPresent())return DataResult.error(()->"Invalid inherited lock policy.");
        return DataResult.success(this);
    }
    public Optional<String> manifestProblem(BundleManifest child){
        var checked=validate();if(checked.error().isPresent())return Optional.of(checked.error().orElseThrow().message());
        if(parents.stream().anyMatch(p->p.preset().equals(child.preset())&&p.release().equals(child.release())))return Optional.of("A child preset cannot inherit from its own revision.");
        var parent=policy.manifest(parents.getFirst());
        if(parent.pack().isPresent()&&!parent.pack().orElseThrow().compatibleChild(parent.lock(),child.pack()))return Optional.of("A child cannot weaken or replace inherited runtime policy locks.");
        if(parent.pack().isEmpty()&&parent.locked()&&child.pack().isPresent()){
            var pack=child.pack().orElseThrow();var overworld=Identifier.parse("minecraft:overworld");
            if(!parent.canEditDimension(overworld)&&(pack.defaults().initial().isPresent()||pack.defaults().respawn().isPresent()||pack.editable().contains("initial")||pack.editable().contains("respawn")))return Optional.of("The parent locks Overworld spawn rules; a child cannot override them through runtime defaults.");
            if(pack.defaults().portals().keySet().stream().anyMatch(id->!parent.canEditDimension(id))||pack.editable().contains("portals")&&baseline.getAsJsonObject("dimensions").keySet().stream().map(Identifier::parse).anyMatch(id->!parent.canEditDimension(id)))return Optional.of("The parent locks portal spawn rules in a dimension.");
        }
        if(!child.requiredMods().containsAll(parent.requiredMods())||!parent.allowStructureLibrary()&&child.allowStructureLibrary())return Optional.of("A child cannot remove inherited dependencies or enable a disabled structure library.");
        if(!parent.locked())return Optional.empty();
        if(!child.locked()||child.editableDimensions().stream().anyMatch(id->!parent.canEditDimension(id))||child.editableFeatures().entrySet().stream().anyMatch(e->e.getValue().stream().anyMatch(f->!parent.canEditFeature(e.getKey(),f)))||child.editableOptions().stream().anyMatch(o->!parent.canEditOption(o)))return Optional.of("A child may tighten inherited locks but cannot weaken them.");
        try{ModpackRuntime.compareOptions(parent,child.options());}catch(IllegalStateException e){return Optional.of(e.getMessage());}
        return Optional.empty();
    }
    public void enforce(PresetStudio.Draft child,WorldPreset decoded,HolderLookup.Provider registries)throws IOException{
        var problem=manifestProblem(child.manifest());if(problem.isPresent())throw new IOException(problem.orElseThrow());
        var parent=policy.manifest(parents.getFirst());if(!parent.locked())return;
        var actual=child.templates();for(var expected:resources.entrySet()){var bytes=actual.get(expected.getKey());if(bytes==null||!fingerprint(bytes).equals(expected.getValue()))throw new IOException("An inherited locked resource changed or is missing: "+expected.getKey());}
        try{var expected=WorldPreset.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,registries),baseline).getOrThrow(IOException::new);ModpackRuntime.compareDimensions(parent,expected.createWorldDimensions(),decoded.createWorldDimensions(),registries);}
        catch(IllegalArgumentException|IllegalStateException e){throw new IOException(e.getMessage(),e);}
    }
    public static PresetInheritance capture(PresetStudio.Draft parent,String fingerprint){
        var ancestors=new ArrayList<Parent>();ancestors.add(new Parent(parent.manifest().preset(),parent.manifest().release(),fingerprint));parent.manifest().inheritance().ifPresent(p->ancestors.addAll(p.parents()));
        var hashes=new TreeMap<String,String>();parent.templates().forEach((path,bytes)->hashes.put(path,fingerprint(bytes)));
        var result=new PresetInheritance(ancestors,Policy.of(parent.manifest()),parent.preset(),hashes);result.validate().getOrThrow();return result;
    }
    public static String fingerprint(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public List<String> report(PresetStudio.Draft child){
        var rows=new ArrayList<String>();rows.add("Inherited parent "+parents.getFirst().identity()+" · SHA-256 "+parents.getFirst().sha256());rows.add("Self-contained export · "+parents.size()+" pinned ancestor revisions · inherited policy "+policy.lock().getSerializedName());
        var changes=new ArrayList<String>();diff("",baseline,child.preset(),changes);diff("/options",WorldOptions.CODEC.codec().encodeStart(JsonOps.INSTANCE,policy.options()).getOrThrow(),WorldOptions.CODEC.codec().encodeStart(JsonOps.INSTANCE,child.manifest().options()).getOrThrow(),changes);rows.add("Child policy: "+child.manifest().lock().getSerializedName());rows.add("Effective generation overrides: "+(changes.size()>128?"128+":changes.size()));rows.addAll(changes.stream().limit(128).toList());if(changes.size()>128)rows.add("Further differences omitted from this bounded view.");return List.copyOf(rows);
    }
    private static void diff(String path,JsonElement before,JsonElement after,List<String> output){
        if(Objects.equals(before,after)||output.size()>=129)return;
        if(before!=null&&after!=null&&before.isJsonObject()&&after.isJsonObject()){var keys=new TreeSet<>(before.getAsJsonObject().keySet());keys.addAll(after.getAsJsonObject().keySet());for(var key:keys)diff(path+"/"+key.replace("~","~0").replace("/","~1"),before.getAsJsonObject().get(key),after.getAsJsonObject().get(key),output);}
        else output.add(path+" · "+(before==null?"added":after==null?"removed":"overridden"));
    }
    public static JsonObject override(JsonObject source,String pointer,JsonElement value,boolean remove){
        if(!bounded(source,BundleFiles.MAX_JSON_BYTES)||!remove&&!bounded(value,65536))throw new IllegalArgumentException("Override inputs exceed their depth or size budget.");
        if(!pointer.startsWith("/")||pointer.length()>1024)throw new IllegalArgumentException("Use a JSON pointer beginning with /.");
        var parts=pointer.substring(1).split("/",-1);if(parts.length>32)throw new IllegalArgumentException("Override pointers are limited to 32 levels.");var copy=source.deepCopy();JsonElement target=copy;
        for(int i=0;i<parts.length;i++){if(java.util.regex.Pattern.compile("~(?![01])").matcher(parts[i]).find())throw new IllegalArgumentException("Escape ~ as ~0 and / as ~1 in override paths.");String key=parts[i].replace("~1","/").replace("~0","~");boolean last=i==parts.length-1;if(target.isJsonObject()){var object=target.getAsJsonObject();if(last){if(remove){if(!object.has(key))throw new IllegalArgumentException("That override field does not exist.");object.remove(key);}else object.add(key,value.deepCopy());}else{target=object.get(key);if(target==null)throw new IllegalArgumentException("The override's parent path does not exist.");}}
            else if(target.isJsonArray()){int index=Integer.parseInt(key);var array=target.getAsJsonArray();if(last){if(remove)array.remove(index);else array.set(index,value.deepCopy());}else target=array.get(index);}
            else throw new IllegalArgumentException("Override paths must traverse objects or arrays.");}
        if(!bounded(copy,BundleFiles.MAX_JSON_BYTES))throw new IllegalArgumentException("Override exceeds the preset depth or size limit.");return copy;
    }
}
