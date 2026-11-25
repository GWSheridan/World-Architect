package com.gwsheridan.worldsunwritten.environment;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class EnvironmentDialog {
    private EnvironmentDialog() {}

    static void show(ServerPlayer player, ServerLevel level, EnvironmentProfile profile, String section) {
        String root = "/worldsunwritten environment " + level.dimension().identifier();
        String prefix = root + " set ";
        var inputs = new ArrayList<Input>();
        var actions = new ArrayList<ActionButton>();
        var capabilities = WorldEnvironment.capabilities(level.dimension().identifier(), level.dimensionType());
        if (section.equals("time") && !capabilities.time() && !capabilities.weather()
                || section.equals("sky") && !capabilities.sky() || section.equals("portal") && !capabilities.portals()) {
            throw new IllegalArgumentException("That environment page is unsupported here. Use /worldsunwritten environment " + level.dimension().identifier() + " for capability diagnostics.");
        }
        String description = "Choose an action to save that setting on the server. Reopen the designer for further edits.";
        switch (section) {
            case "overview" -> {
                description = "Time: " + profile.time().mode().getSerializedName() + " · Weather: " + profile.weather().getSerializedName()
                        + "\nMonsters: " + profile.monsters() + " · Creatures: " + profile.creatures() + " · Spawners: " + profile.spawners()
                        + "\nPortal entry: " + profile.portal().getSerializedName() + "\n\n" + capabilities.description();
                if (capabilities.time() || capabilities.weather()) actions.add(button("Time and weather", root + " edit time"));
                actions.add(button("Mob spawning", root + " edit mobs"));
                actions.add(button("Fog", root + " edit fog"));
                if (capabilities.sky()) actions.add(button("Sky and clouds", root + " edit sky"));
                if (capabilities.portals()) actions.add(button("Portal entry", root + " edit portal"));
            }
            case "time" -> {
                if (capabilities.time()) {
                    inputs.add(input("ticks", "Fixed time or offset · ticks 0–23999", Integer.toString(profile.time().ticks())));
                    actions.add(button("Fix time", prefix + "time fixed $(ticks)"));
                    actions.add(button("Offset time", prefix + "time offset $(ticks)"));
                    actions.add(button("Use normal time", prefix + "time vanilla"));
                }
                if (capabilities.weather()) {
                    inputs.add(input("weather", "Weather · vanilla / clear / rain / thunder", profile.weather().getSerializedName()));
                    actions.add(button("Set weather", prefix + "weather $(weather)"));
                }
            }
            case "mobs" -> {
                inputs.add(input("enabled", "Category enabled · true / false", "true"));
                for (String category : List.of("monsters", "creatures", "spawners")) actions.add(button("Set " + category, prefix + "mobs " + category + " $(enabled)"));
                description += "\nCurrent: monsters " + profile.monsters() + ", creatures " + profile.creatures() + ", spawners " + profile.spawners()
                        + ". Commands, spawn eggs, breeding and existing mobs retain their normal behaviour.";
            }
            case "portal" -> {
                inputs.add(input("entry", "Portal entry · all / players / operators / none", profile.portal().getSerializedName()));
                actions.add(button("Set portal entry", prefix + "portal $(entry)"));
            }
            case "fog" -> {
                inputs.add(input("rgb", "RGB · six hex digits, or vanilla", hex(profile.visual().fogColor())));
                inputs.add(input("fog_start", "Fog start · blocks", Float.toString(profile.visual().fog().map(EnvironmentProfile.Fog::start).orElse(0F))));
                inputs.add(input("fog_end", "Fog end · blocks", Float.toString(profile.visual().fog().map(EnvironmentProfile.Fog::end).orElse(96F))));
                actions.add(button("Set fog colour", prefix + "fog_color $(rgb)"));
                actions.add(button("Set fog distance", prefix + "fog $(fog_start) $(fog_end)"));
                actions.add(button("Use normal fog distance", prefix + "fog vanilla"));
            }
            case "sky" -> {
                inputs.add(input("sky_rgb", "Sky RGB · six hex digits, or vanilla", hex(profile.visual().skyColor())));
                inputs.add(input("cloud_rgb", "Cloud RGB · six hex digits, or vanilla", hex(profile.visual().cloudColor())));
                inputs.add(input("clouds", "Clouds · vanilla / hidden / shown", profile.visual().clouds().getSerializedName()));
                inputs.add(input("cloud_height", "Cloud height · blocks", Float.toString(profile.visual().cloudHeight().orElse(192F))));
                actions.add(button("Set sky colour", prefix + "sky_color $(sky_rgb)"));
                actions.add(button("Set cloud colour", prefix + "cloud_color $(cloud_rgb)"));
                actions.add(button("Set clouds", prefix + "clouds $(clouds)"));
                actions.add(button("Set cloud height", prefix + "cloud_height $(cloud_height)"));
            }
            default -> throw new IllegalArgumentException("Choose time, mobs, portal, fog or sky.");
        }
        var body = List.<DialogBody>of(new PlainMessage(Component.literal(description), 310));
        var header = new CommonDialogData(Component.literal("Environment · " + level.dimension().identifier()), Optional.empty(), true, false, DialogAction.CLOSE, body, inputs);
        player.openDialog(Holder.direct(new MultiActionDialog(header, actions, section.equals("overview") ? Optional.empty() : Optional.of(button("Environment", root + " edit")), 2)));
    }

    private static String hex(Optional<Integer> rgb) { return rgb.map(c -> "%06x".formatted(c)).orElse("vanilla"); }

    private static Input input(String key, String label, String value) {
        return new Input(key, new TextInput(240, Component.literal(label), true, value, 32, Optional.empty()));
    }
    private static ActionButton button(String label, String template) {
        return new ActionButton(new CommonButtonData(Component.literal(label), Optional.empty(), 150),
                Optional.of(template.contains("$(") ? new CommandTemplate(ParsedTemplate.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(template)).getOrThrow())
                        : new StaticAction(new ClickEvent.RunCommand(template))));
    }
}
