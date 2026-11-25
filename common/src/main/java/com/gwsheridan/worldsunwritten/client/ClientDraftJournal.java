package com.gwsheridan.worldsunwritten.client;

import com.google.gson.JsonElement;
import com.gwsheridan.worldsunwritten.authoring.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

/** A pending recovery is never replaced until its author explicitly resolves it. */
final class ClientDraftJournal {
    private final String key, base;
    private final Path directory = com.gwsheridan.worldsunwritten.modpack.ModpackRuntime.directory().resolve("recovery");
    private DraftHistory<JsonElement> history;
    private Optional<JsonElement> recovery = Optional.empty();
    private boolean pending, offered;
    private String status = "";

    ClientDraftJournal(String key, JsonElement initial) {
        this.key = key; base = DraftRecovery.fingerprint(initial);
        try {
            history = new DraftHistory<>(initial, JsonElement::deepCopy,
                    JsonDraftSize::estimate, 32, 32L * 1024 * 1024);
            recovery = DraftRecovery.read(directory, key, base); pending = recovery.isPresent();
        } catch (IOException | RuntimeException e) { pending = true; status = e.getMessage(); }
    }
    void checkpoint(JsonElement value) {
        if (history == null) return;
        try {
            int previous = history.size(); boolean changed = !current().equals(value);
            if(!changed)return;
            history.checkpoint(value);
            if (changed && !pending) DraftRecovery.write(directory, key, base, value);
            if (changed && previous > 1 && !history.canUndo()) status = "Older checkpoints were evicted to keep history within 32 MiB.";
        } catch (IOException | RuntimeException e) { status = e.getMessage(); }
    }
    private JsonElement current() {
        // Forking does not expose retained values and preserves the active redo branch.
        if (currentValue == null) throw new IllegalStateException("Journal has no current document.");
        return currentValue;
    }
    private JsonElement currentValue;
    void observe(JsonElement value) {
        if (currentValue == null) currentValue = value.deepCopy();
        checkpoint(value); currentValue = value.deepCopy();
    }
    boolean offer() { if (!pending || offered) return false; offered = true; return true; }
    boolean canUndo() { return history != null && history.canUndo(); }
    boolean canRedo() { return history != null && history.canRedo(); }
    boolean canRecover() { return recovery.isPresent(); }
    boolean pending() { return pending; }
    String status() { return history==null?"History unavailable: this draft exceeds the 32 MiB working budget. Save a Studio version.":status.isEmpty() ? "32 checkpoints · 32 MiB history · recovery up to 8 MiB" : status; }
    void step(boolean undo,java.util.function.Consumer<JsonElement> restore,java.util.function.Supplier<JsonElement> snapshot) {
        var next=history.fork();var value=undo?next.undo():next.redo();
        restore.accept(value);history=next;currentValue=snapshot.get().deepCopy();
        try{DraftRecovery.write(directory,key,base,currentValue);}catch(IOException e){status=e.getMessage();}
    }
    JsonElement recovery() { return recovery.orElseThrow().deepCopy(); }
    void resolved(JsonElement value) throws IOException {
        pending = false; recovery = Optional.empty(); status = "";
        history = new DraftHistory<>(value, JsonElement::deepCopy, JsonDraftSize::estimate, 32, 32L * 1024 * 1024);
        currentValue = value.deepCopy(); DraftRecovery.write(directory, key, base, value);
    }
    void discard() throws IOException { DraftRecovery.discard(directory, key); pending = false; recovery = Optional.empty(); status = ""; }
    void close() { try { discard(); } catch (IOException e) { status = e.getMessage(); } }
    void close(net.minecraft.client.gui.screens.Screen screen,Runnable done){
        var minecraft=net.minecraft.client.Minecraft.getInstance();
        Runnable finish=()->{try{discard();done.run();}catch(IOException e){status=e.getMessage();minecraft.gui.setScreen(new StudioReportScreen(screen,"Cannot discard recovery",java.util.List.of(status)));}};
        if(!pending){finish.run();return;}
        minecraft.gui.setScreen(new StudioConfirmScreen(answer->{if(answer)finish.run();else minecraft.gui.setScreen(screen);},net.minecraft.network.chat.Component.literal("Discard pending recovery?"),net.minecraft.network.chat.Component.literal("The interrupted draft will be removed. Choose Keep editing to restore or inspect it."),net.minecraft.network.chat.Component.literal("Discard recovery"),net.minecraft.network.chat.Component.literal("Keep editing")));
    }
}
