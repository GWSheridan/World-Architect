package com.gwsheridan.worldsunwritten.compatibility.legacy;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lossless conversion of the frozen public value types; Minecraft objects keep their identity. */
public final class LegacyValues {
    private static final String OLD = "com.gwsheridan.worldarchitect.";
    private static final String CURRENT = "com.gwsheridan.worldsunwritten.";
    private LegacyValues() {}
    @SuppressWarnings("unchecked") public static <T> T current(Object value) { return (T) convert(value, false); }
    @SuppressWarnings("unchecked") public static <T> T legacy(Object value) { return (T) convert(value, true); }
    private static Object convert(Object value, boolean legacy) {
        if (value == null) return null;
        if (value instanceof Optional<?> option) return option.map(item -> convert(item, legacy));
        if (value instanceof List<?> list) return list.stream().map(item -> convert(item, legacy)).toList();
        if (value instanceof Set<?> set) { var result = new LinkedHashSet<>(); set.forEach(item -> result.add(convert(item, legacy))); return Set.copyOf(result); }
        if (value instanceof Map<?,?> map) { var result = new LinkedHashMap<>(); map.forEach((key, item) -> result.put(convert(key, legacy), convert(item, legacy))); return Map.copyOf(result); }
        if (value instanceof byte[] bytes) return bytes.clone();
        String source = legacy ? CURRENT : OLD, target = legacy ? OLD : CURRENT;
        var type = value.getClass(); if (!type.getName().startsWith(source)) return value;
        try {
            var result = Class.forName(target + type.getName().substring(source.length()));
            if (type.isEnum()) return enumValue(result, ((Enum<?>) value).name());
            if (type.isRecord()) {
                var components = type.getRecordComponents(); Object[] args = new Object[components.length]; Class<?>[] types = new Class<?>[components.length];
                for (int i = 0; i < components.length; i++) {
                    args[i] = convert(components[i].getAccessor().invoke(value), legacy);
                    types[i] = mapped(components[i].getType(), source, target);
                }
                return result.getDeclaredConstructor(types).newInstance(args);
            }
            if (value instanceof com.gwsheridan.worldarchitect.structure.ModularStructure structure && !legacy) return structure;
            if (value instanceof com.gwsheridan.worldsunwritten.structure.ModularStructure structure && legacy) return new com.gwsheridan.worldarchitect.structure.ModularStructure(structure);
            throw new IllegalArgumentException("Unsupported compatibility value " + type.getName());
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalArgumentException("Cannot convert a legacy API value", error.getCause());
        } catch (ReflectiveOperationException error) { throw new IllegalArgumentException("Cannot convert a legacy API value", error); }
    }
    private static Class<?> mapped(Class<?> type, String source, String target) throws ClassNotFoundException {
        return type.getName().startsWith(source) ? Class.forName(target + type.getName().substring(source.length())) : type;
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static Enum<?> enumValue(Class<?> type, String name) { return Enum.valueOf((Class) type, name); }
}
