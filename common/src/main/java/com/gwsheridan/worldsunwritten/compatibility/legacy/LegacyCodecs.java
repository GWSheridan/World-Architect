package com.gwsheridan.worldsunwritten.compatibility.legacy;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Distinct registry codec values retain their original dispatch key on round trips. */
public final class LegacyCodecs {
    private static final ThreadLocal<Boolean> CONSTRUCTING = new ThreadLocal<>();
    private static final Map<MapCodec<?>, MapCodec<?>> CODECS = new IdentityHashMap<>();
    private static final Map<MapCodec<?>, MapCodec<?>> CURRENT = new IdentityHashMap<>();
    private LegacyCodecs() {}
    public static boolean constructing() { return Boolean.TRUE.equals(CONSTRUCTING.get()); }
    public static <T> T during(Supplier<T> operation) {
        return during(true, operation);
    }
    public static <T> T during(boolean legacy, Supplier<T> operation) {
        Boolean previous = CONSTRUCTING.get(); CONSTRUCTING.set(legacy);
        try { return operation.get(); }
        finally { if (previous == null) CONSTRUCTING.remove(); else CONSTRUCTING.set(previous); }
    }
    @SuppressWarnings("unchecked")
    public static synchronized <A> MapCodec<A> alias(MapCodec<A> canonical) {
        return scoped(canonical, true);
    }
    public static synchronized <A> MapCodec<A> canonical(MapCodec<A> codec) { return scoped(codec, false); }
    @SuppressWarnings("unchecked")
    private static <A> MapCodec<A> scoped(MapCodec<A> canonical, boolean legacy) {
        return (MapCodec<A>) (legacy ? CODECS : CURRENT).computeIfAbsent(canonical, ignored -> new MapCodec<A>() {
            @Override public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) { return during(legacy, () -> canonical.decode(ops, input)); }
            @Override public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) { return canonical.encode(input, ops, prefix); }
            @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return canonical.keys(ops); }
            @Override public String toString() { return "Legacy dispatch for " + canonical; }
        });
    }
    public static <A> MapCodec<A> serialized(MapCodec<A> codec, boolean legacy) { return legacy ? alias(codec) : canonical(codec); }
}
