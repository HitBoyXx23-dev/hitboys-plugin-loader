package com.hitboy.pluginloader.api.events;

import java.util.UUID;

/** A player died. The death message can be changed, or set to null to send none. */
public final class PlayerDeathEvent {
    private final String playerName;
    private final UUID playerId;
    private String deathMessage;

    public PlayerDeathEvent(String playerName, UUID playerId, String deathMessage) {
        this.playerName = playerName;
        this.playerId = playerId;
        this.deathMessage = deathMessage;
    }

    public String playerName() {
        return playerName;
    }

    public UUID playerId() {
        return playerId;
    }

    public String deathMessage() {
        return deathMessage;
    }

    public void setDeathMessage(String deathMessage) {
        this.deathMessage = deathMessage;
    }
}
