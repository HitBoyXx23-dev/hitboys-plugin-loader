package com.hitboy.pluginloader.api.events;

import java.util.UUID;

/** A player is placing a block. Cancel it to stop the placement. */
public final class BlockPlaceEvent {
    private final String playerName;
    private final UUID playerId;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final String blockType;
    private boolean cancelled;

    public BlockPlaceEvent(String playerName, UUID playerId, String worldName, int x, int y, int z, String blockType) {
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

    public UUID playerId() {
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

    /** The block being placed, such as {@code minecraft:stone} (standalone) or {@code STONE} (Bukkit). */
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
