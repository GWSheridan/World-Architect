package com.gwsheridan.worldsunwritten.compatibility.legacy;

import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Historical quest commands enter the same checked command tree as current commands. */
public final class LegacyCommands {
    private LegacyCommands() {}
    public static String administrationGroup(String token) {
        return token.equals("wa_island") ? "island" : token.equals("wa_spawn") ? "spawn" : token;
    }
    public static void register(RegisterCommandsEvent event) {
        var root = event.getDispatcher().getRoot().getChild(LegacyIds.CURRENT);
        if (root == null) return;
        var alias = Commands.literal(LegacyIds.NAMESPACE).requires(root.getRequirement()).redirect(root);
        if (root.getCommand() != null) alias.executes(root.getCommand());
        event.getDispatcher().register(alias);
        for (var child : root.getChildren()) {
            var old = Commands.literal("wa_" + child.getName()).requires(child.getRequirement()).redirect(child);
            if (child.getCommand() != null) old.executes(child.getCommand());
            event.getDispatcher().register(old);
        }
    }
}
