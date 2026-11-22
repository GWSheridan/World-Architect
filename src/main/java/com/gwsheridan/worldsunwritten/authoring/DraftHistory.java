package com.gwsheridan.worldsunwritten.authoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

/** Detached checkpoints. Copying and accounting apply equally to undo and redo. */
public final class DraftHistory<T> {
    private record Entry<T>(T value, long bytes) {}
    private final List<Entry<T>> entries = new ArrayList<>();
    private final UnaryOperator<T> copy;
    private final ToLongFunction<T> weigh;
    private final int limit;
    private final long budget;
    private int cursor;
    private long bytes;

    public DraftHistory(T initial, UnaryOperator<T> copy, ToLongFunction<T> weigh, int limit, long budget) {
        this(copy, weigh, limit, budget);
        checkpoint(initial);
    }
    private DraftHistory(UnaryOperator<T> copy, ToLongFunction<T> weigh, int limit, long budget) {
        if (limit < 2 || budget < 1) throw new IllegalArgumentException("History needs at least two checkpoints and a positive budget.");
        this.copy = copy; this.weigh = weigh; this.limit = limit; this.budget = budget;
    }
    /** Transactional edits may reserve their complete working set before replacing the active history. */
    public DraftHistory<T> fork() {
        var result = new DraftHistory<T>(copy, weigh, limit, budget);
        result.entries.addAll(entries); result.cursor = cursor; result.bytes = bytes;
        return result;
    }
    public void checkpoint(T value) {
        if (!entries.isEmpty() && Objects.equals(entries.get(cursor).value, value)) return;
        T snapshot = copy.apply(value);
        long size = weigh.applyAsLong(snapshot);
        if (size < 0 || size > budget) throw new IllegalArgumentException("Draft exceeds the history memory budget.");
        while (entries.size() > cursor + 1) bytes -= entries.removeLast().bytes;
        entries.add(new Entry<>(snapshot, size)); bytes += size;
        while (entries.size() > 1 && (entries.size() > limit || bytes > budget)) bytes -= entries.removeFirst().bytes;
        cursor = entries.size() - 1;
    }
    public boolean canUndo() { return cursor > 0; }
    public void trimTo(long available) {
        if(available<entries.get(cursor).bytes)throw new IllegalArgumentException("The active draft exceeds the available authoring budget.");
        while(bytes>available&&entries.size()>1){if(cursor>0){bytes-=entries.removeFirst().bytes;cursor--;}else bytes-=entries.removeLast().bytes;}
    }
    public boolean canRedo() { return cursor + 1 < entries.size(); }
    public T undo() { if (!canUndo()) throw new IllegalStateException("No earlier checkpoint."); return copy.apply(entries.get(--cursor).value); }
    public T redo() { if (!canRedo()) throw new IllegalStateException("No later checkpoint."); return copy.apply(entries.get(++cursor).value); }
    public long bytes() { return bytes; }
    public int size() { return entries.size(); }
}
