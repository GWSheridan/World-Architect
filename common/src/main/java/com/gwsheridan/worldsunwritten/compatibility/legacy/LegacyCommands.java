package com.gwsheridan.worldsunwritten.compatibility.legacy;

import net.minecraft.commands.Commands;
import com.gwsheridan.worldsunwritten.commands.CommandRegistration;

/** Historical quest commands enter the same checked command tree as current commands. */
public final class LegacyCommands {
    private LegacyCommands() {}
    public static String administrationGroup(String token) {
        return token.equals("wa_island") ? "island" : token.equals("wa_spawn") ? "spawn" : token;
    }
    public static void register(CommandRegistration event) {
        var root = event.dispatcher().getRoot().getChild(LegacyIds.CURRENT);
        if (root == null) return;
        var alias = Commands.literal(LegacyIds.NAMESPACE).requires(root.getRequirement()).redirect(root);
        if (root.getCommand() != null) alias.executes(root.getCommand());
        event.dispatcher().register(alias);
        for (var child : root.getChildren()) {
            var old = Commands.literal("wa_" + child.getName()).requires(child.getRequirement()).redirect(child);
            if (child.getCommand() != null) old.executes(child.getCommand());
            event.dispatcher().register(old);
        }
    }
}
