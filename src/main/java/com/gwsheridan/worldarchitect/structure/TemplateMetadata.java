package com.gwsheridan.worldarchitect.structure;

import java.io.IOException;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Only decorative fields are copied. Containers are always created with empty inventories. */
final class TemplateMetadata {
    private TemplateMetadata() {}

    static boolean restricted(BlockState state) {
        return state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.CHAIN_COMMAND_BLOCK)
                || state.is(Blocks.REPEATING_COMMAND_BLOCK) || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)
                || state.is(Blocks.SPAWNER) || state.is(Blocks.TRIAL_SPAWNER) || state.is(Blocks.VAULT)
                || state.is(Blocks.END_GATEWAY) || state.is(Blocks.MOVING_PISTON);
    }

    static void validateSchematic(BlockState state, CompoundTag raw, BlockPos pos) throws IOException {
        Identifier id = Identifier.tryParse(raw.getStringOr("id", ""));
        var type = id == null ? null : BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
        if (type == null || !type.isValid(state)) {
            throw new IOException("Schematic block entity " + id + " does not match " + state + " at " + pos.toShortString());
        }
        Set<String> allowed;
        if (type == BlockEntityTypes.SIGN || type == BlockEntityTypes.HANGING_SIGN) {
            allowed = Set.of("front_text", "back_text", "is_waxed");
        } else if (type == BlockEntityTypes.BANNER) {
            allowed = Set.of("patterns", "CustomName");
        } else if (type == BlockEntityTypes.CHEST || type == BlockEntityTypes.TRAPPED_CHEST) {
            allowed = Set.of("CustomName");
        } else {
            throw new IOException("Unsupported schematic block entity " + id + " at " + pos.toShortString() + ". Remove its data and re-export.");
        }
        for (String field : raw.keySet()) {
            if (allowed.contains(field) || field.equals("id") || field.equals("x") || field.equals("y") || field.equals("z")
                    || field.equals("Items") && raw.getList("Items").filter(list -> list.isEmpty()).isPresent()) {
                continue;
            }
            throw new IOException("Unsupported schematic block-entity field " + field + " at " + pos.toShortString() + ". Clear it and re-export.");
        }
    }

    static @Nullable CompoundTag sanitize(BlockState state, @Nullable CompoundTag raw, HolderLookup.Provider registries) throws IOException {
        if (raw == null) {
            return null;
        }
        if (!state.hasBlockEntity()) {
            throw new IOException("A template provides block-entity metadata for " + state);
        }
        CompoundTag result = new CompoundTag();
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        BlockEntityType<?> type;
        if (BlockEntityTypes.SIGN.isValid(state) || BlockEntityTypes.HANGING_SIGN.isValid(state)) {
            type = BlockEntityTypes.SIGN.isValid(state) ? BlockEntityTypes.SIGN : BlockEntityTypes.HANGING_SIGN;
            for (String side : new String[] {"front_text", "back_text"}) {
                if (raw.contains(side)) {
                    SignText text = SignText.CODEC.parse(ops, raw.get(side)).getOrThrow(IOException::new);
                    var safe = new SignText(text.getMessages(false).stream().map(TemplateMetadata::plainText).toList(),
                            text.getMessages(true).stream().map(TemplateMetadata::plainText).toList(), text.getColor(), text.hasGlowingText());
                    result.put(side, SignText.CODEC.encodeStart(ops, safe).getOrThrow(IOException::new));
                }
            }
            result.putBoolean("is_waxed", raw.getBooleanOr("is_waxed", false));
        } else if (BlockEntityTypes.BANNER.isValid(state)) {
            type = BlockEntityTypes.BANNER;
            if (raw.contains("patterns")) {
                var pattern = BannerPatternLayers.CODEC.parse(ops, raw.get("patterns")).getOrThrow(IOException::new);
                if (pattern.layers().size() > 20) {
                    throw new IOException("A banner exceeds 20 pattern layers.");
                }
                result.put("patterns", BannerPatternLayers.CODEC.encodeStart(ops, pattern).getOrThrow(IOException::new));
            }
        } else if (BlockEntityTypes.CHEST.isValid(state) || BlockEntityTypes.TRAPPED_CHEST.isValid(state)) {
            type = BlockEntityTypes.CHEST.isValid(state) ? BlockEntityTypes.CHEST : BlockEntityTypes.TRAPPED_CHEST;
        } else {
            return null;
        }
        if (type != BlockEntityTypes.SIGN && type != BlockEntityTypes.HANGING_SIGN && raw.contains("CustomName")) {
            Component name = ComponentSerialization.CODEC.parse(ops, raw.get("CustomName")).getOrThrow(IOException::new);
            result.put("CustomName", ComponentSerialization.CODEC.encodeStart(ops, plainText(name)).getOrThrow(IOException::new));
        }
        if (result.isEmpty()) {
            return null;
        }
        result.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type).toString());
        return result;
    }

    private static Component plainText(Component component) {
        return Component.literal(component.getString(256));
    }
}
