package com.gwsheridan.worldarchitect.api;

import com.gwsheridan.worldarchitect.modpack.ModpackRuntime;
import com.gwsheridan.worldarchitect.modpack.PresetStudio;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.WorldDimensions;

/** Author detached presets for new worlds. These operations never replace saved-world generation. */
public final class PresetApi {
    private PresetApi() {}
    public static PresetStudio.Checked validate(PresetStudio.Draft draft, HolderLookup.Provider registries) throws IOException { return PresetStudio.validate(draft, registries); }
    public static PresetStudio.Draft capture(CommandSourceStack source, String name, String release) throws IOException {
        ApiAccess.admin(source); var server = source.getServer();
        return PresetStudio.capture(PresetStudio.defaults(name, release, server.getWorldGenSettings().options()),
                new WorldDimensions(server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM)), server.registryAccess());
    }
    public static Path save(CommandSourceStack source, String name, PresetStudio.Draft draft) throws IOException {
        ApiAccess.admin(source); return PresetStudio.save(ModpackRuntime.directory(), name, draft, source.getServer().registryAccess());
    }
    public static PresetStudio.Draft load(CommandSourceStack source, String name, String release) throws IOException {
        ApiAccess.admin(source); return PresetStudio.load(ModpackRuntime.directory(), name, release);
    }
    public static Path export(CommandSourceStack source, String name, String release) throws IOException {
        ApiAccess.admin(source); return PresetStudio.export(ModpackRuntime.directory(), name, release, source.getServer().registryAccess());
    }
    public static PresetStudio.Draft duplicate(PresetStudio.Draft source, String name, String release) { return PresetStudio.duplicate(source, name, release); }
    public static PresetStudio.Draft derive(CommandSourceStack source,String parent,String release,String expectedSha,String child,String childRelease)throws IOException{
        ApiAccess.admin(source);return PresetStudio.derive(ModpackRuntime.directory(),parent,release,expectedSha,child,childRelease,source.getServer().registryAccess());
    }
    public static PresetStudio.Draft override(CommandSourceStack source,PresetStudio.Draft draft,String pointer,com.google.gson.JsonElement value,boolean remove)throws IOException{
        ApiAccess.admin(source);return PresetStudio.override(draft,pointer,value,remove,source.getServer().registryAccess());
    }
    public static PresetStudio.Draft readImport(CommandSourceStack source, String zip) throws IOException {
        ApiAccess.admin(source); return PresetStudio.readImport(ModpackRuntime.directory(), zip, source.getServer().registryAccess());
    }
}
