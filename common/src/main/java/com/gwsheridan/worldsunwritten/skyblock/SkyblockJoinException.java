package com.gwsheridan.worldsunwritten.skyblock;

/** A Skyblock join refusal with a message suitable for the player. */
public final class SkyblockJoinException extends IllegalStateException {
    public SkyblockJoinException(String message) {
        super(message);
    }
}
