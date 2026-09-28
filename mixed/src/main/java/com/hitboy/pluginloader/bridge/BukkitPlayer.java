package com.hitboy.pluginloader.bridge;

import com.hitboy.pluginloader.api.PlayerHandle;
import org.bukkit.entity.Player;

import java.util.UUID;

/** A Bukkit {@link Player} as a {@link PlayerHandle}. */
final class BukkitPlayer implements PlayerHandle {
    private final Player player;

    BukkitPlayer(Player player) {
        this.player = player;
    }

    @Override
    public String name() {
        return player.getName();
    }

    @Override
    public UUID id() {
        return player.getUniqueId();
    }

    @Override
    public void sendMessage(String message) {
        player.sendMessage(message);
    }

    @Override
    public String worldName() {
        return player.getWorld().getName();
    }

    @Override
    public double x() {
        return player.getLocation().getX();
    }

    @Override
    public double y() {
        return player.getLocation().getY();
    }

    @Override
    public double z() {
        return player.getLocation().getZ();
    }

    @Override
    public boolean isOp() {
        return player.isOp();
    }

    @Override
    public void kick(String reason) {
        player.kickPlayer(reason);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BukkitPlayer && ((BukkitPlayer) other).player.getUniqueId().equals(player.getUniqueId());
    }

    @Override
    public int hashCode() {
        return player.getUniqueId().hashCode();
    }

    @Override
    public String toString() {
        return player.getName();
    }
}
