package com.gwsheridan.worldsunwritten.workbench;

import com.google.gson.*;
import com.gwsheridan.worldsunwritten.authoring.DraftRecovery;
import com.gwsheridan.worldsunwritten.modpack.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.core.HolderLookup;

/** Exports reviewed inputs only; this class cannot start processes or load a production save. */
public final class WorkbenchAuthoring {
    private WorkbenchAuthoring(){}
    public record Source(String name,String release,String bundle,String sha256,JsonObject scenario){}
    private static void safe(Path path)throws IOException{for(Path p=path.toAbsolutePath().normalize();p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Workbench paths cannot contain symbolic links.");}
    private static String hash(Path path)throws IOException{
        safe(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>32L*1024*1024)throw new IOException("Expected a bounded preset bundle.");
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static Source source(String name,String release,HolderLookup.Provider registries)throws IOException{
        var draft=PresetStudio.load(ModpackRuntime.directory(),name,release);var checked=PresetStudio.validate(draft,registries);
        var dimensions=checked.preset().createWorldDimensions().dimensions().keySet().stream().map(k->k.identifier().toString()).sorted().toList();
        if(dimensions.size()>16)throw new IOException("Select at most sixteen dimensions for one scenario.");
        String sha=hash(ModpackRuntime.directory().resolve("studio").resolve(name).resolve(release+".zip"));String bundle=name+"-"+release+".zip";
        return new Source(name,release,bundle,sha,WorkbenchScenario.validate(WorkbenchCommands.scenario(bundle,sha,draft.manifest().options().seed(),dimensions)));
    }
    public static Path export(Source original,String outputName,JsonElement input,HolderLookup.Provider registries)throws IOException{
        if(!ModpackRuntime.developerMode())throw new IOException("Enable developer_mode before exporting scenarios.");
        if(!outputName.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}"))throw new IOException("Choose a simple scenario export name.");
        var scenario=WorkbenchScenario.validate(input);var current=source(original.name,original.release,registries);
        if(!current.sha256.equals(original.sha256)||!scenario.getAsJsonObject("bundle").get("file").getAsString().equals(current.bundle)||!scenario.getAsJsonObject("bundle").get("sha256").getAsString().equals(current.sha256))throw new IOException("The saved bundle or scenario binding changed. Reopen the workbench.");
        Path directory=ModpackRuntime.directory().resolve("exports"),target=directory.resolve(outputName+".scenario.json"),bundle=directory.resolve(current.bundle);safe(target);safe(bundle);
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("That scenario export already exists. Choose a new name.");
        if(Files.exists(bundle,LinkOption.NOFOLLOW_LINKS)){if(!hash(bundle).equals(current.sha256))throw new IOException("An existing export has a different bundle fingerprint.");}
        else{var exported=PresetStudio.export(ModpackRuntime.directory(),current.name,current.release,registries);if(!hash(exported).equals(current.sha256))throw new IOException("Exported bundle fingerprint differs.");}
        String content=new GsonBuilder().setPrettyPrinting().create().toJson(scenario)+"\n";
        if(content.getBytes(StandardCharsets.UTF_8).length>WorkbenchScenario.MAX_BYTES)content=scenario.toString()+"\n";
        if(content.getBytes(StandardCharsets.UTF_8).length>WorkbenchScenario.MAX_BYTES)throw new IOException("Formatted scenario exceeds 256 KiB.");
        Files.createDirectories(directory);Files.writeString(target,content,StandardOpenOption.CREATE_NEW);return target;
    }
}
