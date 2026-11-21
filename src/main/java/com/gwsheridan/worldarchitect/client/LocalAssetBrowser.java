package com.gwsheridan.worldarchitect.client;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Explicit file selection; imports are read only and exports use their fixed project directory. */
final class LocalAssetBrowser {
    private LocalAssetBrowser(){}
    static void open(Screen parent,Path start,Consumer<Path> select){
        try{
            Path directory=start.toAbsolutePath().normalize();Files.createDirectories(directory);
            var paths=new TreeMap<String,Path>();
            if(directory.getParent()!=null)paths.put("↑ Parent folder",directory.getParent());
            try(var entries=Files.list(directory)){
                entries.filter(p->Files.isDirectory(p)||p.toString().endsWith(".nbt")||p.toString().endsWith(".schem")||p.toString().endsWith(".island.json")).sorted().limit(4096).forEach(p->paths.put((Files.isDirectory(p)?"Folder · ":"File · ")+p.getFileName(),p));
            }
            Minecraft.getInstance().gui.setScreen(new StudioChoiceScreen(parent,"Import · "+directory.getFileName(),List.copyOf(paths.keySet()),choice->{var path=paths.get(choice);if(Files.isDirectory(path))open(parent,path,select);else {select.accept(path);Minecraft.getInstance().gui.setScreen(parent);}}));
        }catch(Exception error){Minecraft.getInstance().gui.setScreen(new StudioReportScreen(parent,"Cannot browse island assets",List.of(Objects.toString(error.getMessage(),"Choose a readable local folder."))));}
    }
}
