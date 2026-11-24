package com.gwsheridan.worldsunwritten.cells;

import com.gwsheridan.worldsunwritten.api.CellLayout;
import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;

import java.util.*;

/** Detached, bounded layout editing shared by mouse controls and keyboard actions. */
public final class CellCanvas {
    private CellWorldProfile profile;
    private final ArrayDeque<CellWorldProfile> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private Set<Cell> selection = Set.of();
    private Map<Cell, String> clipboard = Map.of();
    private final long seed;

    public CellCanvas(CellWorldProfile profile) {
        this(profile, 0);
    }

    public CellCanvas(CellWorldProfile profile, long seed) {
        this.profile = profile.validate().getOrThrow();
        this.seed = seed;
    }

    public CellWorldProfile profile() {
        return profile;
    }

    public Set<Cell> selection() {
        return selection;
    }

    public void select(Collection<Cell> cells) {
        if (cells.size() > 1024) throw new IllegalArgumentException("Select at most 1024 cells.");
        for (var c : cells) check(c);
        selection = Set.copyOf(cells);
    }

    private void check(Cell c) {
        if (profile.progressive() && profile.layout().distance(c) > profile.growth().radius())
            throw new IllegalArgumentException("Cell lies outside the saved territory radius.");
    }

    public void paint(String type) {
        if (profile.tiles().stream().noneMatch(t -> t.id().equals(type)))
            throw new IllegalArgumentException("Unknown cell type.");
        var values = assignments();
        String start = profile.start();
        for (var c : selection) {
            check(c);
            if (c.equals(Cell.ORIGIN)) start = type;
            else values.put(c, type);
        }
        change(values, start, profile.campaign());
    }

    public void erase() {
        var values = assignments();
        selection.forEach(values::remove);
        change(values, profile.start(), profile.campaign());
    }

    public void copy(Cell anchor) {
        var values = new LinkedHashMap<Cell, String>();
        for (var c : selection)
            values.put(
                    new Cell(c.q() - anchor.q(), c.r() - anchor.r()),
                    profile.tile(seed, 0, c).id());
        clipboard = Map.copyOf(values);
    }

    public void paste(Cell anchor) {
        var values = assignments();
        String start = profile.start();
        for (var e : clipboard.entrySet()) {
            var c = new Cell(anchor.q() + e.getKey().q(), anchor.r() + e.getKey().r());
            check(c);
            if (c.equals(Cell.ORIGIN)) start = e.getValue();
            else values.put(c, e.getValue());
        }
        change(values, start, profile.campaign());
    }

    public static Cell transform(Cell cell, CellLayout.Shape shape, boolean mirror) {
        int q = cell.q(), r = cell.r();
        return mirror
                ? (shape == CellLayout.Shape.SQUARE ? new Cell(-q, r) : new Cell(r, q))
                : (shape == CellLayout.Shape.SQUARE ? new Cell(-r, q) : new Cell(-r, q + r));
    }

    public void transformClipboard(boolean mirror) {
        var result = new LinkedHashMap<Cell, String>();
        for (var e : clipboard.entrySet())
            result.put(transform(e.getKey(), profile.layout().shape(), mirror), e.getValue());
        clipboard = Map.copyOf(result);
    }

    public void group(String id) {
        var campaign = profile.campaign().orElse(CellCampaign.EMPTY);
        var groups = new ArrayList<>(campaign.groups());
        groups.removeIf(g -> g.id().equals(id));
        groups.add(new CellCampaign.Group(id, selection.stream().sorted().toList()));
        change(assignments(), profile.start(), Optional.of(campaign.withGroups(groups)));
    }

    public void selectGroup(String id) {
        select(
                profile.campaign().orElse(CellCampaign.EMPTY).groups().stream()
                        .filter(g -> g.id().equals(id))
                        .findFirst()
                        .orElseThrow()
                        .cells());
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void undo() {
        if (canUndo()) {
            redo.push(profile);
            profile = undo.pop();
        }
    }

    public void redo() {
        if (canRedo()) {
            undo.push(profile);
            profile = redo.pop();
        }
    }

    private TreeMap<Cell, String> assignments() {
        var result = new TreeMap<Cell, String>();
        profile.assignments().forEach(a -> result.put(a.cell(), a.type()));
        return result;
    }

    private void change(Map<Cell, String> values, String start, Optional<CellCampaign> campaign) {
        if (values.size() > 1024)
            throw new IllegalArgumentException("At most 1024 fixed assignments.");
        var next =
                new CellWorldProfile(
                        profile.layout(),
                        profile.salt(),
                        start,
                        profile.tiles(),
                        values.entrySet().stream()
                                .map(e -> new CellWorldProfile.Assignment(e.getKey(), e.getValue()))
                                .toList(),
                        profile.border(),
                        profile.borderToFloor(),
                        profile.growth(),
                        campaign);
        next.validate().getOrThrow();
        if (next.equals(profile)) return;
        undo.push(profile);
        while (undo.size() > 32) undo.removeLast();
        redo.clear();
        profile = next;
    }
}
