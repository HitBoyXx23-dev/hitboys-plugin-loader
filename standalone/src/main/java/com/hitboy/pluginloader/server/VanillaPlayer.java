package com.hitboy.pluginloader.server;

import com.hitboy.pluginloader.api.PlayerHandle;

import java.util.UUID;

/** A vanilla {@code ServerPlayer} as a {@link PlayerHandle}. */
final class VanillaPlayer implements PlayerHandle {
    private static final String ENTITY = "net.minecraft.world.entity.Entity";
    private static final String COMPONENT = "net.minecraft.network.chat.Component";

    private final VanillaBridge bridge;
    private final Object player;

    VanillaPlayer(VanillaBridge bridge, Object player) {
        this.bridge = bridge;
        this.player = player;
    }

    @Override
    public String name() {
        return bridge.playerName(player);
    }

    @Override
    public UUID id() {
        return bridge.playerId(player);
    }

    @Override
    public void sendMessage(String message) {
        bridge.call(player, "net.minecraft.server.level.ServerPlayer", "sendSystemMessage", COMPONENT, bridge.literal(message));
    }

    @Override
    public String worldName() {
        return bridge.worldName(bridge.call(player, ENTITY, "level", ""));
    }

    @Override
    public double x() {
        return (Double) bridge.call(player, ENTITY, "getX", "");
    }

    @Override
    public double y() {
        return (Double) bridge.call(player, ENTITY, "getY", "");
    }

    @Override
    public double z() {
        return (Double) bridge.call(player, ENTITY, "getZ", "");
    }

    @Override
    public boolean isOp() {
        return bridge.isOp(player);
    }

    @Override
    public void kick(String reason) {
        Object connection = bridge.connectionOf(player);
        bridge.call(connection, "net.minecraft.server.network.ServerCommonPacketListenerImpl", "disconnect", COMPONENT, bridge.literal(reason));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VanillaPlayer && ((VanillaPlayer) other).player == player;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(player);
    }

    @Override
    public String toString() {
        return name();
    }
}
