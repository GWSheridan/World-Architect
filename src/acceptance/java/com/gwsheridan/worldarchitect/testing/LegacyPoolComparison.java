package com.gwsheridan.worldarchitect.testing;

import static com.gwsheridan.worldarchitect.testing.AssemblyAcceptance.*;
import com.google.gson.JsonElement;
import com.gwsheridan.worldarchitect.modpack.ModpackRuntime;
import com.mojang.serialization.MapCodec;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;

/** Compares the actual published planner, loaded separately, against the retained format-1 path. */
final class LegacyPoolComparison {
    static void check(MinecraftServer server,Object current,JsonElement definition)throws Exception{
        var file=ModpackRuntime.directory().resolve("validation-baseline-1.3.0.jar");byte[] bytes=Files.readAllBytes(file);
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        require(hash.equals("c4d5cd556958fade5f995a80d7c1140a80ff18f60992fea7718634f17bacd7a9"),"Unexpected legacy comparison JAR");
        try(var loader=new URLClassLoader(new java.net.URL[]{file.toUri().toURL()},LegacyPoolComparison.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.startsWith("com.gwsheridan.worldarchitect.structure."))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)){Class<?> c=findLoadedClass(name);if(c==null){try{c=findClass(name);}catch(ClassNotFoundException e){c=super.loadClass(name,false);}}if(resolve)resolveClass(c);return c;}
            }
        }){
            var type=loader.loadClass("com.gwsheridan.worldarchitect.structure.ModularStructure");var field=type.getDeclaredField("CODEC");field.setAccessible(true);
            var codec=(MapCodec<?>)field.get(null);var ops=RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,server.registryAccess());Object legacy=codec.codec().parse(ops,definition).getOrThrow();
            for(long seed=0;seed<256;seed++){
                var old=(Optional<?>)call(legacy,"previewLayout",seed);var now=(Optional<?>)call(current,"previewLayout",seed);
                require(old.isPresent()==now.isPresent(),"Legacy pool acceptance changed at seed "+seed);
                if(old.isPresent())require(signature(old.orElseThrow()).equals(signature(now.orElseThrow())),"Legacy pool contents changed at seed "+seed);
            }
        }
        System.out.println("WA_LEGACY_POOL_256_EXACT_PASS");
    }
    static String signature(Object layout)throws Exception{var out=new StringBuilder();for(var module:(List<?>)layout)out.append(call(module,"name")).append(call(module,"offset")).append(call(call(module,"entry"),"toTag"));return out.toString();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
