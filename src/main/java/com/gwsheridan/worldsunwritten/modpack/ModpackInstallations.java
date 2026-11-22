package com.gwsheridan.worldsunwritten.modpack;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.HolderLookup;

/** Export only the preset's installation files, never an authoring world or another mod's JAR. */
public final class ModpackInstallations {
    private ModpackInstallations(){}
    public static Path export(Path directory,String name,PresetStudio.Draft source,HolderLookup.Provider registries)throws IOException{
        if(!ModpackRuntime.developerMode())throw new IOException("Enable authoring and restart before exporting a modpack project.");
        var checked=PresetStudio.validate(source,registries);var draft=checked.draft();String release=draft.manifest().release();
        if(!name.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}"))throw new IOException("Choose a simple project name.");
        Path saved=directory.resolve("studio").resolve(name).resolve(release+".zip");
        if(Files.exists(saved)){
            var old=PresetStudio.load(directory,name,release);
            if(!com.gwsheridan.worldsunwritten.authoring.PresetDraftDocument.encode(name,old).equals(com.gwsheridan.worldsunwritten.authoring.PresetDraftDocument.encode(name,draft)))throw new IOException("This revision already exists with different content. Choose a new release.");
        }else PresetStudio.save(directory,name,draft,registries);
        byte[] bytes=Files.readAllBytes(saved);String filename=name+"-"+release+".zip",hash=PresetInheritance.fingerprint(bytes);
        Path folder=directory.resolve("exports").resolve(name+"-"+release+"-pack");Files.createDirectories(folder.getParent());
        if(Files.exists(folder))throw new IOException("The installation export already exists. Choose a new release to preserve it.");
        Path temporary=Files.createTempDirectory(folder.getParent(),".pack-");
        // Retain a failed staging directory for diagnosis; an incomplete export is never presented as final.
        for(String side:List.of("client","server")){
            Path config=temporary.resolve(side).resolve("config/worldsunwritten");Files.createDirectories(config.resolve("bundles"));
            Files.write(config.resolve("bundles").resolve(filename),bytes,StandardOpenOption.CREATE_NEW);
            Files.writeString(config.resolve("developer.json"),"{\"format\":1,\"developer_mode\":false,\"bundle\":\""+filename+"\"}\n",StandardOpenOption.CREATE_NEW);
        }
        Files.writeString(temporary.resolve("server/server.properties"),"# Worlds Unwritten supplies omitted generation defaults for a new world.\nonline-mode=true\n",StandardOpenOption.CREATE_NEW);
        Files.writeString(temporary.resolve("SHA256SUMS.txt"),hash+"  client/config/worldsunwritten/bundles/"+filename+"\n"+hash+"  server/config/worldsunwritten/bundles/"+filename+"\n",StandardOpenOption.CREATE_NEW);
        Files.writeString(temporary.resolve("testkit.properties"),"format=2\nbundle=client/config/worldsunwritten/bundles/"+filename+"\nsha256="+hash+"\nworldsunwritten=2.0.0\nneoforge=26.3.0.103\n",StandardOpenOption.CREATE_NEW);
        var instructions=new ArrayList<String>(List.of(draft.manifest().title()+" · "+release,"","Install matching Worlds Unwritten and required mods first. Copy the client or server config folder into a clean instance, then restart and create a new world.","The included server.properties is a new-server example. Merge its settings when using an existing server configuration.","Developer mode is disabled. The preset archive is identical on both sides. Existing worlds retain their original settings and templates.","This export contains no Minecraft installation, provider JARs, saved worlds, ownership ledger or player data.","","Test with the optional Worlds Unwritten test kit, an installed matching NeoForge server runtime and your required provider JARs. Select this project folder; results stay in a new disposable output directory.","","Required mods: "+String.join(", ",draft.manifest().requiredMods()),"Preset SHA-256: "+hash,"","Validation:"));instructions.addAll(checked.report());
        Files.write(temporary.resolve("README.txt"),instructions,StandardOpenOption.CREATE_NEW);
        Files.move(temporary,folder,StandardCopyOption.ATOMIC_MOVE);return folder;
    }
}
