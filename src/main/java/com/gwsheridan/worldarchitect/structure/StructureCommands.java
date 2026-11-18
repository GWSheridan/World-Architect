package com.gwsheridan.worldarchitect.structure;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.IdentifierArgument.getId;
import static net.minecraft.commands.arguments.IdentifierArgument.id;
import static net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos;
import static net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos;

import com.gwsheridan.worldarchitect.modpack.ModpackRuntime;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class StructureCommands {
    private static final Map<MinecraftServer, Map<String, Selection>> SELECTIONS = new WeakHashMap<>();
    private static final Identifier ANY = Identifier.withDefaultNamespace("any");

    private StructureCommands() {}

    public static void register(RegisterCommandsEvent event) {
        var commands = literal("wa_structure")
                .requires(source -> ModpackRuntime.allowStructureLibrary(source.getServer()) && Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source))
                .executes(run(c -> list(c, 1)))
                .then(literal("list").executes(run(c -> list(c, 1)))
                        .then(integerArgument("page", 1, Integer.MAX_VALUE).executes(run(c -> list(c, getInteger(c, "page"))))))
                .then(corner("pos1", true))
                .then(corner("pos2", false))
                .then(literal("outline").executes(run(StructureCommands::outline)))
                .then(literal("capture").then(idArgument("name").executes(run(c -> capture(c, selected(c))))
                        .then(posArgument("from").then(posArgument("to").executes(run(c -> capture(c,
                                new Selection(c.getSource().getLevel().dimension(), getBlockPos(c, "from"), getBlockPos(c, "to")))))))))
                .then(literal("import").then(idArgument("source").then(idArgument("name").executes(run(StructureCommands::importNative)))))
                .then(literal("export").then(saved().then(idArgument("target").executes(run(StructureCommands::export)))))
                .then(literal("duplicate").then(saved().then(idArgument("target").executes(run(StructureCommands::duplicate)))))
                .then(literal("rename").then(saved().then(idArgument("target").executes(run(StructureCommands::rename)))))
                .then(literal("describe").then(saved().then(arg("category", string()).then(arg("title", string()).executes(run(StructureCommands::describe))))))
                .then(literal("preview").then(saved().executes(run(c -> preview(c, -1)))
                        .then(integerArgument("layer", 0, NativeTemplate.MAX_SIDE - 1).executes(run(c -> preview(c, getInteger(c, "layer")))))))
                .then(literal("place").then(saved().then(destination(false))))
                .then(literal("spawn").then(saved().then(arg("marker", word()).then(destination(true)))))
                .then(markers())
                .then(rules());
        event.getDispatcher().register(commands);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var selections = SELECTIONS.get(player.level().getServer());
            if (selections != null) {
                selections.remove(player.getStringUUID());
            }
        }
    }

    private static ArgumentBuilder<CommandSourceStack, ?> corner(String label, boolean first) {
        return literal(label).executes(run(c -> select(c, first, BlockPos.containing(c.getSource().getPosition()))))
                .then(posArgument("position").executes(run(c -> select(c, first, getBlockPos(c, "position")))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> destination(boolean spawn) {
        return posArgument("origin").executes(run(c -> useDestination(c, spawn, Rotation.NONE, Mirror.NONE)))
                .then(choices("rotation", "none", "cw90", "cw180", "ccw90")
                        .executes(run(c -> useDestination(c, spawn, rotation(c), Mirror.NONE)))
                        .then(choices("mirror", "none", "left_right", "front_back")
                                .executes(run(c -> useDestination(c, spawn, rotation(c), mirror(c))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> markers() {
        return literal("marker")
                .then(literal("set").then(saved().then(arg("marker", word()).then(posArgument("local")
                        .then(choices("facing", "north", "east", "south", "west").executes(run(StructureCommands::setMarker)))))))
                .then(literal("remove").then(saved().then(arg("marker", word()).executes(run(StructureCommands::removeMarker)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rules() {
        var height = integerArgument("max_y", DimensionType.MIN_Y, DimensionType.MAX_Y)
                .executes(run(c -> setRules(c, Optional.empty(), Optional.empty())))
                .then(idArgument("dimension")
                        .executes(run(c -> setRules(c, restriction(c, "dimension"), Optional.empty())))
                        .then(idArgument("biome").executes(run(c -> setRules(c, restriction(c, "dimension"), restriction(c, "biome"))))));
        var collision = choices("collision", "air", "matching")
                .then(integerArgument("min_y", DimensionType.MIN_Y, DimensionType.MAX_Y).then(height));
        var clear = saved().executes(run(c -> saveChange(c, entry(c).withRules(TemplateRules.DEFAULT))));
        var set = saved().then(choices("terrain", "free", "supported", "buried").then(collision));
        return literal("rules").then(literal("clear").then(clear)).then(literal("set").then(set));
    }

    private static int list(CommandContext<CommandSourceStack> context, int page) throws IOException {
        var ids = StructureLibrary.list(server(context));
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            LibraryDialog.library(player, ids, page);
        } else {
            int last = Math.max(1, (ids.size() + LibraryDialog.PAGE_SIZE - 1) / LibraryDialog.PAGE_SIZE);
            int first = (Math.min(page, last) - 1) * LibraryDialog.PAGE_SIZE;
            var shown = ids.subList(first, Math.min(ids.size(), first + LibraryDialog.PAGE_SIZE));
            success(context, shown.isEmpty() ? "The Structure Library is empty." : "Structure Library: " + shown);
        }
        return ids.size();
    }

    private static int select(CommandContext<CommandSourceStack> context, boolean first, BlockPos pos) throws IOException {
        var level = context.getSource().getLevel();
        if (!level.isInWorldBounds(pos)) {
            throw new IOException("Selection corner is outside this dimension's build bounds.");
        }
        var entries = SELECTIONS.computeIfAbsent(server(context), ignored -> new HashMap<>());
        String key = selectionKey(context);
        var selection = entries.get(key);
        if (selection == null || !selection.dimension.equals(level.dimension())) {
            selection = new Selection(level.dimension(), null, null);
        }
        var updated = first ? new Selection(level.dimension(), pos, selection.second) : new Selection(level.dimension(), selection.first, pos);
        entries.put(key, updated);
        success(context, "Corner " + (first ? "1" : "2") + " set to " + pos.toShortString());
        if (updated.first != null && updated.second != null && context.getSource().getEntity() instanceof ServerPlayer player) {
            showOutline(player, updated);
        }
        return 1;
    }

    private static String selectionKey(CommandContext<CommandSourceStack> context) {
        return context.getSource().getEntity() instanceof ServerPlayer player ? player.getStringUUID() : context.getSource().getTextName();
    }

    private static Selection selected(CommandContext<CommandSourceStack> context) throws IOException {
        Selection selection = SELECTIONS.getOrDefault(server(context), Map.of()).get(selectionKey(context));
        if (selection == null || selection.first == null || selection.second == null
                || !selection.dimension.equals(context.getSource().getLevel().dimension())) {
            throw new IOException("Set both selection corners in this dimension.");
        }
        return selection;
    }

    private static int outline(CommandContext<CommandSourceStack> context) throws IOException, CommandSyntaxException {
        showOutline(context.getSource().getPlayerOrException(), selected(context));
        return 1;
    }

    private static void showOutline(ServerPlayer player, Selection selection) {
        if (!player.level().dimension().equals(selection.dimension)) {
            return;
        }
        int[] low = {Math.min(selection.first.getX(), selection.second.getX()), Math.min(selection.first.getY(), selection.second.getY()),
                Math.min(selection.first.getZ(), selection.second.getZ())};
        int[] high = {Math.max(selection.first.getX(), selection.second.getX()) + 1, Math.max(selection.first.getY(), selection.second.getY()) + 1,
                Math.max(selection.first.getZ(), selection.second.getZ()) + 1};
        for (int axis = 0; axis < 3; axis++) {
            int a = (axis + 1) % 3;
            int b = (axis + 2) % 3;
            int steps = Math.min(high[axis] - low[axis], 20);
            for (int i = 0; i < 4; i++) {
                double[] point = new double[3];
                point[a] = (i & 1) == 0 ? low[a] : high[a];
                point[b] = (i & 2) == 0 ? low[b] : high[b];
                for (int step = 0; step <= steps; step++) {
                    point[axis] = low[axis] + (high[axis] - low[axis]) * ((double) step / steps);
                    player.level().sendParticles(player, ParticleTypes.END_ROD, true, true,
                            point[0], point[1], point[2], 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private static int capture(CommandContext<CommandSourceStack> context, Selection selection) throws IOException {
        Identifier id = getId(context, "name");
        var result = StructureLibrary.capture(context.getSource().getLevel(), id, context.getSource().getTextName(), selection.first, selection.second);
        StructureLibrary.saveNew(server(context), id, result);
        success(context, "Captured " + id + " with " + result.template().blocks().size() + " blocks.");
        return 1;
    }

    private static int importNative(CommandContext<CommandSourceStack> context) throws IOException {
        Identifier id = getId(context, "name");
        var result = StructureLibrary.importNative(server(context), getId(context, "source"), id, context.getSource().getTextName());
        StructureLibrary.saveNew(server(context), id, result);
        success(context, "Imported " + id + " with " + result.template().blocks().size() + " blocks.");
        return 1;
    }

    private static int export(CommandContext<CommandSourceStack> context) throws IOException {
        Identifier target = getId(context, "target");
        StructureLibrary.export(server(context), target, entry(context));
        success(context, "Exported native structure " + target + ".");
        return 1;
    }

    private static int duplicate(CommandContext<CommandSourceStack> context) throws IOException {
        Identifier target = getId(context, "target");
        StructureLibrary.saveNew(server(context), target, entry(context).duplicate());
        success(context, "Duplicated template as " + target + ".");
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> context) throws IOException {
        Identifier target = getId(context, "target");
        StructureLibrary.rename(server(context), getId(context, "template"), target);
        success(context, "Renamed template to " + target + ".");
        return 1;
    }

    private static int describe(CommandContext<CommandSourceStack> context) throws IOException {
        return saveChange(context, entry(context).withDetails(getString(context, "title"), getString(context, "category")));
    }

    private static int preview(CommandContext<CommandSourceStack> context, int layer) throws IOException {
        Identifier id = getId(context, "template");
        var entry = entry(context);
        if (layer >= entry.template().size().getY()) {
            throw new IOException("Preview layer is outside the template.");
        }
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            LibraryDialog.preview(player, id, entry, layer);
        } else {
            success(context, LibraryDialog.summary(id, entry));
        }
        return 1;
    }

    private static int useDestination(CommandContext<CommandSourceStack> context, boolean spawn, Rotation rotation, Mirror mirror)
            throws IOException, CommandSyntaxException {
        var placement = new TemplatePlacement(getBlockPos(context, "origin"), rotation, mirror);
        var level = context.getSource().getLevel();
        var entry = entry(context);
        if (spawn) {
            placement.spawn(level, context.getSource().getPlayerOrException(), entry, getString(context, "marker"));
            return 1;
        }
        int count = placement.place(level, entry, context.getSource().getEntity());
        success(context, "Placed " + count + " blocks from " + getId(context, "template") + " at " + placement.origin().toShortString() + ".");
        return count;
    }

    private static Rotation rotation(CommandContext<CommandSourceStack> context) {
        return switch (getString(context, "rotation")) {
            case "none" -> Rotation.NONE;
            case "cw90" -> Rotation.CLOCKWISE_90;
            case "cw180" -> Rotation.CLOCKWISE_180;
            case "ccw90" -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("Use none, cw90, cw180 or ccw90.");
        };
    }

    private static Mirror mirror(CommandContext<CommandSourceStack> context) {
        return switch (getString(context, "mirror")) {
            case "none" -> Mirror.NONE;
            case "left_right" -> Mirror.LEFT_RIGHT;
            case "front_back" -> Mirror.FRONT_BACK;
            default -> throw new IllegalArgumentException("Use none, left_right or front_back.");
        };
    }

    private static int setMarker(CommandContext<CommandSourceStack> context) throws IOException {
        Direction direction = Direction.byName(getString(context, "facing"));
        if (direction == null || !direction.getAxis().isHorizontal()) {
            throw new IOException("Choose north, east, south or west.");
        }
        BlockPos local = getBlockPos(context, "local");
        return saveChange(context, entry(context).withMarker(getString(context, "marker"), new LibraryEntry.Marker(local, direction)));
    }

    private static int removeMarker(CommandContext<CommandSourceStack> context) throws IOException {
        return saveChange(context, entry(context).withoutMarker(getString(context, "marker")));
    }

    private static int setRules(CommandContext<CommandSourceStack> context, Optional<Identifier> dimension, Optional<Identifier> biome)
            throws IOException {
        int min = getInteger(context, "min_y");
        int max = getInteger(context, "max_y");
        if (min > max || dimension.isPresent() && server(context).getLevel(ResourceKey.create(Registries.DIMENSION, dimension.orElseThrow())) == null
                || biome.isPresent() && server(context).registryAccess().lookupOrThrow(Registries.BIOME)
                        .get(ResourceKey.create(Registries.BIOME, biome.orElseThrow())).isEmpty()) {
            throw new IOException("Choose a valid height range and registered dimension and biome IDs.");
        }
        var terrain = TemplateRules.Terrain.parse(getString(context, "terrain"));
        var collision = TemplateRules.Collision.parse(getString(context, "collision"));
        return saveChange(context, entry(context).withRules(new TemplateRules(dimension, biome, min, max, terrain, collision)));
    }

    private static Optional<Identifier> restriction(CommandContext<CommandSourceStack> context, String name) {
        return Optional.of(getId(context, name)).filter(value -> !value.equals(ANY));
    }

    private static int saveChange(CommandContext<CommandSourceStack> context, LibraryEntry entry) throws IOException {
        Identifier id = getId(context, "template");
        StructureLibrary.update(server(context), id, entry);
        success(context, "Saved " + id + " revision " + entry.revision() + ".");
        return 1;
    }

    private static LibraryEntry entry(CommandContext<CommandSourceStack> context) throws IOException {
        return StructureLibrary.load(server(context), getId(context, "template"));
    }

    private static MinecraftServer server(CommandContext<CommandSourceStack> context) {
        return context.getSource().getServer();
    }

    private static void success(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Identifier> saved() {
        return idArgument("template").suggests((context, builder) -> {
            try {
                return SharedSuggestionProvider.suggestResource(StructureLibrary.list(server(context)), builder);
            } catch (IOException e) {
                return builder.buildFuture();
            }
        });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> choices(String name, String... values) {
        return arg(name, word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of(values), builder));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> integerArgument(String name, int min, int max) {
        return arg(name, integer(min, max));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Identifier> idArgument(String name) {
        return arg(name, id());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, net.minecraft.commands.arguments.coordinates.Coordinates> posArgument(String name) {
        return arg(name, blockPos());
    }

    private static <T> RequiredArgumentBuilder<CommandSourceStack, T> arg(String name, ArgumentType<T> type) {
        return argument(name, type);
    }

    private static Command<CommandSourceStack> run(Operation action) {
        return context -> {
            try {
                return action.execute(context);
            } catch (IOException | IllegalArgumentException | ArithmeticException e) {
                context.getSource().sendFailure(Component.literal(e.getMessage() == null ? "Structure operation failed." : e.getMessage()));
                return 0;
            }
        };
    }

    private record Selection(ResourceKey<Level> dimension, BlockPos first, BlockPos second) {}

    @FunctionalInterface
    private interface Operation {
        int execute(CommandContext<CommandSourceStack> context) throws IOException, CommandSyntaxException;
    }
}
