package com.gwsheridan.worldsunwritten.benchmark;

import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.concurrent.*;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Separate developer fixture. It depends on Minecraft/NeoForge, never Worlds Unwritten classes. */
@Mod("wa_benchmark")
public final class BenchmarkProbe {
    private static final Object LOCK=new Object();
    private static boolean active;
    private static long peakHeap,peakResident;
    private final ScheduledExecutorService sampler=Executors.newSingleThreadScheduledExecutor(task->{var thread=new Thread(task,"generation-memory-sampler");thread.setDaemon(true);return thread;});
    public BenchmarkProbe(IEventBus bus){
        NeoForge.EVENT_BUS.addListener(this::commands);NeoForge.EVENT_BUS.addListener(this::started);
        sampler.scheduleAtFixedRate(BenchmarkProbe::sample,50,50,TimeUnit.MILLISECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(sampler::shutdownNow));
    }
    private void started(ServerStartedEvent event){
        String core=ModList.get().isLoaded("worldsunwritten")?"worldsunwritten":"worldarchitect";
        var json=new JsonObject();json.addProperty("format",1);json.addProperty("pid",ProcessHandle.current().pid());json.addProperty("java",System.getProperty("java.version"));json.addProperty("os",System.getProperty("os.name"));json.addProperty("processors",Runtime.getRuntime().availableProcessors());json.addProperty("version",ModList.get().getModContainerById(core).orElseThrow().getModInfo().getVersion().toString());
        var flags=new com.google.gson.JsonArray();ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(flag->flag.startsWith("-Xms")||flag.startsWith("-Xmx")||flag.startsWith("-XX:")).forEach(flags::add);json.add("jvm_args",flags);
        try{var path=ModList.get().getModFileById(core).getFile().getFilePath();json.addProperty("loaded_jar_sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));}catch(Exception e){throw new IllegalStateException("Benchmark requires an external Worlds Unwritten JAR",e);}
        write("benchmark-process.json",json);
    }
    private void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(literal("wa_benchmark").requires(source->hasPermission(LEVEL_ADMINS).test(source)&&source.getEntity()==null)
                .then(argument("base",integer(-50000,50000)).then(argument("width",integer(1,32)).then(argument("sample",integer(0,16)).executes(context->{
                    int base=getInteger(context,"base"),width=getInteger(context,"width"),id=getInteger(context,"sample");String filename="benchmark-sample-"+id+".json";
                    if(Files.exists(Path.of(filename))||Math.abs(base)<1000||context.getSource().getServer().getPlayerList().getPlayerCount()!=0){context.getSource().sendFailure(Component.literal("WA_BENCHMARK_FAILED samples need fresh remote coordinates, a new sample ID and no connected players"));return 0;}
                    var level=context.getSource().getServer().overworld();long gcCount=gcCount(),gcTime=gcTime();
                    synchronized(LOCK){peakHeap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();peakResident=resident();active=true;}
                    long epoch=System.currentTimeMillis(),started=System.nanoTime();
                    try{
                        for(int z=base;z<base+width;z++)for(int x=base;x<base+width;x++)level.getChunk(x,z);
                        long nanos=System.nanoTime()-started,finished=System.currentTimeMillis();
                        var json=new JsonObject();json.addProperty("format",1);json.addProperty("method","direct_generation");json.addProperty("id",id);json.addProperty("base_chunk",base);json.addProperty("width",width);json.addProperty("chunks",width*width);json.addProperty("seed",Long.toString(level.getSeed()));json.addProperty("dimension",level.dimension().identifier().toString());json.addProperty("elapsed_nanos",nanos);json.addProperty("started_epoch_ms",epoch);json.addProperty("finished_epoch_ms",finished);json.addProperty("gc_count",gcCount()-gcCount);json.addProperty("gc_millis",gcTime()-gcTime);
                        synchronized(LOCK){sample();active=false;json.addProperty("sampled_heap_peak_bytes",peakHeap);if(peakResident>=0)json.addProperty("sampled_resident_peak_bytes",peakResident);else json.add("sampled_resident_peak_bytes",com.google.gson.JsonNull.INSTANCE);}
                        write(filename,json);context.getSource().sendSuccess(()->Component.literal("WA_BENCHMARK_SAMPLE "+id),false);return width*width;
                    }finally{synchronized(LOCK){active=false;}}
                })))));
    }
    private static void sample(){synchronized(LOCK){if(active){peakHeap=Math.max(peakHeap,ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());long current=resident();if(current>=0)peakResident=Math.max(peakResident,current);}}}
    private static long resident(){
        if(!System.getProperty("os.name").equals("Linux"))return -1;
        try{for(String line:Files.readAllLines(Path.of("/proc/self/status")))if(line.startsWith("VmRSS:"))return Long.parseLong(line.substring(6).trim().split("\\s+")[0])*1024;}catch(Exception ignored){}return -1;
    }
    private static long gcCount(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean->Math.max(0,bean.getCollectionCount())).sum();}
    private static long gcTime(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean->Math.max(0,bean.getCollectionTime())).sum();}
    private static void write(String filename,JsonObject value){try{Files.writeString(Path.of(filename),value.toString()+"\n",StandardOpenOption.CREATE_NEW);}catch(java.io.IOException e){throw new IllegalStateException("Cannot write benchmark sample",e);}}
}
