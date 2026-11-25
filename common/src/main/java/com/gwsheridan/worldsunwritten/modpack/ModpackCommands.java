package com.gwsheridan.worldsunwritten.modpack;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.gwsheridan.worldsunwritten.commands.CommandRegistration;

public final class ModpackCommands {
    private ModpackCommands() {}

    public static void register(CommandRegistration event) {
        var root = Commands.literal("modpack").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .executes(context -> {
                    String status = WorldBundles.active(context.getSource().getServer()).map(bundle -> {
                        var manifest = bundle.manifest();
                        return manifest.title() + " · " + manifest.preset() + " · " + manifest.release() + " · " + manifest.lock().getSerializedName()
                                + "\n" + manifest.description();
                    }).orElse("No bundle installed.");
                    context.getSource().sendSuccess(() -> Component.literal(status + "\nDeveloper export: "
                            + (ModpackRuntime.developerMode() ? "enabled" : "disabled")), false);
                    return 1;
                });
        if (ModpackRuntime.developerMode()) {
            root.then(Commands.literal("export").then(Commands.argument("draft", StringArgumentType.word()).executes(context -> {
                try {
                    var result = ModpackPackager.export(context.getSource().getServer(), StringArgumentType.getString(context, "draft"));
                    context.getSource().sendSuccess(() -> Component.literal("Exported preset bundle " + result.getFileName()), false);
                    return 1;
                } catch (IOException | IllegalArgumentException | IllegalStateException e) {
                    context.getSource().sendFailure(Component.literal("Cannot export preset bundle: " + e.getMessage()));
                    return 0;
                }
            })));
        }
        root.then(Commands.literal("authoring_enable").requires(source->source.getEntity()==null).executes(context->{try{ModpackRuntime.requestAuthoring();context.getSource().sendSuccess(()->Component.literal("Authoring configuration saved. Restart this authoring server to enable it."),false);return 1;}catch(IOException error){context.getSource().sendFailure(Component.literal(error.getMessage()));return 0;}}));
        com.gwsheridan.worldsunwritten.commands.ModCommands.register(event,root);
    }
}
