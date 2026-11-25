package com.gwsheridan.worldsunwritten.platform;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public record AdapterLookup<T>(Function<Identifier, Optional<T>> values, Supplier<Set<Identifier>> keys) {
    public static <T> AdapterLookup<T> from(Registry<T> registry) {
        return new AdapterLookup<>(registry::getOptional, registry::keySet);
    }
}
