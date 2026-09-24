package com.hitboy.pluginloader.api.events;

/** Fired when a player leaves the server. */
public final class PlayerQuitEvent {
    private final String playerName;
    private final java.util.UUID playerId;
    private String quitMessage;

    public PlayerQuitEvent(String playerName, java.util.UUID playerId, String quitMessage) {
        this.playerName = playerName;
        this.playerId = playerId;
        this.quitMessage = quitMessage;
    }

    public String playerName() {
        return playerName;
    }

    public java.util.UUID playerId() {
        return playerId;
    }

    public String quitMessage() {
        return quitMessage;
    }

    public void setQuitMessage(String quitMessage) {
        this.quitMessage = quitMessage;
    }
}
