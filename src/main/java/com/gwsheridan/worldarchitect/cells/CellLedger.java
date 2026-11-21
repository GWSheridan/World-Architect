package com.gwsheridan.worldarchitect.cells;

import com.google.gson.*;
import com.gwsheridan.worldarchitect.api.CellLayout;
import com.gwsheridan.worldarchitect.api.CellLayout.Cell;
import com.gwsheridan.worldarchitect.worldgen.CellGenerator.Key;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Server-thread journal. Every reward and ownership decision is committed before it becomes visible. */
public final class CellLedger {
    public enum Status {QUEUED,APPLYING,READY,CONFLICT}
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final CellLayout layout;
    private final GrowthRules growth;
    private JsonObject data;
    private Map<Key,Status> indexed=Map.of();
    private Map<Integer,Map<Cell,Status>> territories=Map.of();
    private List<Key> pending=List.of();
    private Set<Key> ready=Set.of();
    public CellLedger(Path file,CellWorldProfile profile,String pin,boolean fresh) throws IOException {
        this(file,profile.layout(),profile.growth(),pin,fresh);
    }
    public CellLedger(Path file,CellLayout layout,GrowthRules growth,String pin,boolean fresh) throws IOException {
        this.file=file;this.layout=layout;this.growth=growth;
        if(Files.notExists(file)){
            if(!fresh)throw new IOException("The saved cell progression journal is missing. Restore it from the world backup.");
            var root=new JsonObject();root.addProperty("format",1);root.addProperty("generation_sha256",pin);root.add("owners",new JsonObject());root.add("regions",new JsonObject());root.add("cells",new JsonObject());root.getAsJsonObject("cells").addProperty(new Key(0,Cell.ORIGIN).id(),Status.READY.name());commit(root);
        }else{
            if(Files.size(file)>32*1024*1024)throw new IOException("Cell journal exceeds its size limit.");
            try{data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();if(data.get("format").getAsInt()!=1||!data.get("generation_sha256").getAsString().equals(pin))throw new IllegalArgumentException("Generation pin changed");validate();index();}
            catch(RuntimeException error){throw new IOException("Invalid cell progression journal. Restore the original journal: "+error.getMessage(),error);}
        }
    }
    private void validate(){
        var occupied=new HashSet<Integer>();for(var entry:data.getAsJsonObject("owners").entrySet()){int slot=entry.getValue().getAsInt();if(entry.getKey().length()>100||slot<1||slot>growth.territories()||!occupied.add(slot))throw new IllegalArgumentException("Invalid territory ownership");}
        for(var entry:data.getAsJsonObject("cells").entrySet()){var key=Key.parse(entry.getKey());if(key.territory()<0||key.territory()>growth.territories()||layout.distance(key.cell())>growth.radius())throw new IllegalArgumentException("Cell outside saved territory");Status.valueOf(entry.getValue().getAsString());}
        if(status(new Key(0,Cell.ORIGIN))!=Status.READY)throw new IllegalArgumentException("The original starting cell is missing");
        if(data.has("conflict_resume"))for(var entry:data.getAsJsonObject("conflict_resume").entrySet())if(status(Key.parse(entry.getKey()))!=Status.CONFLICT||!entry.getValue().getAsBoolean())throw new IllegalArgumentException("Invalid conflict recovery state");
        for(var entry:data.getAsJsonObject("regions").entrySet()){int slot=Integer.parseInt(entry.getKey());if(slot<0||slot>growth.territories())throw new IllegalArgumentException("Invalid progress region");var region=entry.getValue().getAsJsonObject();if(region.get("credits").getAsInt()<0||region.get("credits").getAsInt()>8192)throw new IllegalArgumentException("Invalid expansion credits");var known=growth.goals().stream().map(GrowthRules.Goal::id).toList();for(var completed:strings(region.getAsJsonArray("completed")))if(!known.contains(completed))throw new IllegalArgumentException("Unknown completed objective");}
    }
    public Map<Key,Status> cells(){return indexed;}
    public Map<Cell,Status> territoryCells(int slot){return territories.getOrDefault(slot,Map.of());}
    public Set<Key> ready(){return ready;}
    public List<Key> pending(){return pending;}
    private void index(){var all=new TreeMap<Key,Status>(Comparator.comparing(Key::id));var regions=new HashMap<Integer,Map<Cell,Status>>();var queue=new ArrayList<Key>();var active=new HashSet<Key>();for(var entry:data.getAsJsonObject("cells").entrySet()){var key=Key.parse(entry.getKey());var status=Status.valueOf(entry.getValue().getAsString());all.put(key,status);regions.computeIfAbsent(key.territory(),ignored->new HashMap<>()).put(key.cell(),status);if(status==Status.READY)active.add(key);if(status==Status.QUEUED||status==Status.APPLYING)queue.add(key);}var saved=new HashMap<Integer,Map<Cell,Status>>();regions.forEach((slot,cells)->saved.put(slot,Map.copyOf(cells)));indexed=Collections.unmodifiableMap(all);territories=Map.copyOf(saved);pending=List.copyOf(queue);ready=Set.copyOf(active);}
    public Status status(Key key){var value=data.getAsJsonObject("cells").get(key.id());return value==null?null:Status.valueOf(value.getAsString());}
    public int assigned(String owner){var value=data.getAsJsonObject("owners").get(owner);return value==null?0:value.getAsInt();}
    public int claim(String owner) throws IOException {
        if(owner.isBlank()||owner.length()>100)throw new IllegalArgumentException("Invalid territory owner");int existing=assigned(owner);if(existing>0)return existing;
        var used=new HashSet<Integer>();data.getAsJsonObject("owners").entrySet().forEach(e->used.add(e.getValue().getAsInt()));cells().keySet().forEach(key->used.add(key.territory()));int slot=1;while(used.contains(slot))slot++;if(slot>growth.territories())throw new IllegalStateException("All saved territories are assigned.");
        var draft=data.deepCopy();draft.getAsJsonObject("owners").addProperty(owner,slot);queue(draft,new Key(slot,Cell.ORIGIN));commit(draft);return slot;
    }
    public void reassign(String owner,int slot) throws IOException {
        if(owner.isBlank()||owner.length()>100||slot<1||slot>growth.territories())throw new IllegalArgumentException("Invalid territory reassignment");for(var entry:data.getAsJsonObject("owners").entrySet())if(!entry.getKey().equals(owner)&&entry.getValue().getAsInt()==slot)throw new IllegalArgumentException("That territory already has an owner.");
        var draft=data.deepCopy();draft.getAsJsonObject("owners").addProperty(owner,slot);queue(draft,new Key(slot,Cell.ORIGIN));commit(draft);
    }
    public void transition(Key key,Status expected,Status next) throws IOException {if(status(key)!=expected)throw new IllegalStateException("Cell journal transition changed");var draft=data.deepCopy();draft.getAsJsonObject("cells").addProperty(key.id(),next.name());if(!draft.has("conflict_resume"))draft.add("conflict_resume",new JsonObject());var resume=draft.getAsJsonObject("conflict_resume");if(next==Status.CONFLICT&&expected==Status.APPLYING)resume.addProperty(key.id(),true);else if(next!=Status.CONFLICT)resume.remove(key.id());if(!growth.chooseFrontier()&&(expected==Status.CONFLICT||next==Status.READY))spendAutomatically(draft,key.territory());commit(draft);}
    public void retry(Key key) throws IOException {boolean resume=data.has("conflict_resume")&&data.getAsJsonObject("conflict_resume").has(key.id())&&data.getAsJsonObject("conflict_resume").get(key.id()).getAsBoolean();transition(key,Status.CONFLICT,resume?Status.APPLYING:Status.QUEUED);}
    public void queue(Key key) throws IOException {checkKey(key);if(status(key)!=null)return;var draft=data.deepCopy();queue(draft,key);commit(draft);}
    private void queue(JsonObject draft,Key key){checkKey(key);var cells=draft.getAsJsonObject("cells");if(!cells.has(key.id()))cells.addProperty(key.id(),Status.QUEUED.name());}
    private void checkKey(Key key){if(key.territory()<0||key.territory()>growth.territories()||growth.scope()==GrowthRules.Scope.SHARED&&key.territory()!=0||layout.distance(key.cell())>growth.radius())throw new IllegalArgumentException("Cell is outside the saved territory.");}
    private JsonObject progress(int slot){var regions=data.getAsJsonObject("regions");if(regions.has(Integer.toString(slot)))return regions.getAsJsonObject(Integer.toString(slot));var value=new JsonObject();value.addProperty("credits",0);value.add("completed",new JsonArray());return value;}
    public Set<String> completed(int slot){return strings(progress(slot).getAsJsonArray("completed"));}
    public int credits(int slot){return progress(slot).get("credits").getAsInt();}
    /** Signal observation survives prerequisites arriving later; a goal pays once per physical territory. */
    public List<String> observe(int slot,GrowthRules.Trigger trigger,String source) throws IOException {
        if(source.length()>200)throw new IllegalArgumentException("Signal identifier too long");String observation=trigger.name()+":"+source;var existing=data.getAsJsonObject("regions").getAsJsonObject(Integer.toString(slot));if(existing!=null&&strings(existing.getAsJsonArray("observed")).contains(observation))return List.of();
        checkKey(new Key(slot,Cell.ORIGIN));var draft=data.deepCopy();var region=region(draft,slot);var observed=strings(region.getAsJsonArray("observed"));if(source.length()>200)throw new IllegalArgumentException("Signal identifier too long");observed.add(trigger.name()+":"+source);if(observed.size()>512)throw new IllegalArgumentException("Too many objective signals");
        var completed=strings(region.getAsJsonArray("completed"));var awarded=new ArrayList<String>();int credits=region.get("credits").getAsInt();boolean changed;
        do{changed=false;for(var goal:growth.goals())if(!completed.contains(goal.id())&&observed.contains(goal.trigger().name()+":"+goal.source())&&goal.prerequisites(completed)){
            completed.add(goal.id());awarded.add(goal.id());credits+=goal.credits();for(var cell:goal.targets())queue(draft,new Key(slot,cell));changed=true;
        }}while(changed);
        region.add("observed",array(observed));region.add("completed",array(completed));region.addProperty("credits",credits);
        if(!growth.chooseFrontier())spendAutomatically(draft,slot);
        if(!draft.equals(data))commit(draft);return List.copyOf(awarded);
    }
    public Set<Cell> frontier(int slot){var result=new TreeSet<Cell>();var cells=territoryCells(slot);cells.forEach((cell,status)->{if(status!=Status.CONFLICT)for(var neighbour:layout.neighbours(cell))if(layout.distance(neighbour)<=growth.radius()&&!cells.containsKey(neighbour))result.add(neighbour);});return Collections.unmodifiableSet(result);}
    private Set<Cell> frontier(JsonObject root,int slot){var result=new TreeSet<Cell>();var states=root.getAsJsonObject("cells");for(var entry:states.entrySet()){var key=Key.parse(entry.getKey());if(key.territory()!=slot||Status.valueOf(entry.getValue().getAsString())==Status.CONFLICT)continue;for(var neighbour:layout.neighbours(key.cell()))if(layout.distance(neighbour)<=growth.radius()&&!states.has(new Key(slot,neighbour).id()))result.add(neighbour);}return Collections.unmodifiableSet(result);}
    public void choose(int slot,Cell cell) throws IOException {if(!growth.chooseFrontier())throw new IllegalStateException("This pack selects cells automatically.");if(credits(slot)<1||!frontier(slot).contains(cell))throw new IllegalArgumentException("Choose an available frontier cell with an expansion credit.");var draft=data.deepCopy();queue(draft,new Key(slot,cell));region(draft,slot).addProperty("credits",credits(slot)-1);commit(draft);}
    private void spendAutomatically(JsonObject draft,int slot){var region=region(draft,slot);int credits=region.get("credits").getAsInt();if(credits==0)return;var frontier=new TreeSet<>(frontier(draft,slot));var cells=draft.getAsJsonObject("cells");while(credits>0&&!frontier.isEmpty()){var next=frontier.pollFirst();queue(draft,new Key(slot,next));for(var neighbour:layout.neighbours(next))if(layout.distance(neighbour)<=growth.radius()&&!cells.has(new Key(slot,neighbour).id()))frontier.add(neighbour);credits--;}region.addProperty("credits",credits);}
    private static JsonObject region(JsonObject root,int slot){var regions=root.getAsJsonObject("regions");String id=Integer.toString(slot);if(!regions.has(id)){var region=new JsonObject();region.addProperty("credits",0);region.add("observed",new JsonArray());region.add("completed",new JsonArray());regions.add(id,region);}return regions.getAsJsonObject(id);}
    private static Set<String> strings(JsonArray array){var result=new TreeSet<String>();array.forEach(value->result.add(value.getAsString()));return result;}
    private static JsonArray array(Collection<String> values){var array=new JsonArray();values.forEach(array::add);return array;}
    private void commit(JsonObject draft) throws IOException {
        Files.createDirectories(file.getParent());Path temporary=file.resolveSibling(file.getFileName()+".pending");var bytes=StandardCharsets.UTF_8.encode(JSON.toJson(draft)+"\n");
        try(var channel=FileChannel.open(temporary,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}
        Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);data=draft;index();
    }
}
