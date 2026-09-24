package com.hitboy.pluginloader.api.events;

/** Fired when a player sends a chat message, before it is broadcast. */
public final class PlayerChatEvent {
    private final String playerName;
    private final java.util.UUID playerId;
    private String message;
    private boolean cancelled;

    public PlayerChatEvent(String playerName, java.util.UUID playerId, String message) {
        this.playerName = playerName;
        this.playerId = playerId;
        this.message = message;
    }

    public String playerName() {
        return playerName;
    }

    public java.util.UUID playerId() {
        return playerId;
    }

    public String message() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
