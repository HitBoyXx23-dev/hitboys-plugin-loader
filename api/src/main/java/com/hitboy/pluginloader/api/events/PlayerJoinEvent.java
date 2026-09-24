package com.hitboy.pluginloader.api.events;

/**
 * Fired when a player joins the server. This is HitBoy's own simplified event
 * type -- decoupled from Bukkit's {@code PlayerJoinEvent} so plugin code does
 * not need to import Bukkit types directly.
 */
public final class PlayerJoinEvent {
    private final String playerName;
    private final java.util.UUID playerId;
    private String joinMessage;

    public PlayerJoinEvent(String playerName, java.util.UUID playerId, String joinMessage) {
        this.playerName = playerName;
        this.playerId = playerId;
        this.joinMessage = joinMessage;
    }

    public String playerName() {
        return playerName;
    }

    public java.util.UUID playerId() {
        return playerId;
    }

    public String joinMessage() {
        return joinMessage;
    }

    public void setJoinMessage(String joinMessage) {
        this.joinMessage = joinMessage;
    }
}
