package com.hitboy.pluginloader.api.events;

/** Fired when a player breaks a block, before the break is finalized. */
public final class BlockBreakEvent {
    private final String playerName;
    private final java.util.UUID playerId;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final String blockType;
    private boolean cancelled;

    public BlockBreakEvent(
        String playerName,
        java.util.UUID playerId,
        String worldName,
        int x,
        int y,
        int z,
        String blockType
    ) {
        this.playerName = playerName;
        this.playerId = playerId;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockType = blockType;
    }

    public String playerName() {
        return playerName;
    }

    public java.util.UUID playerId() {
        return playerId;
    }

    public String worldName() {
        return worldName;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public String blockType() {
        return blockType;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
