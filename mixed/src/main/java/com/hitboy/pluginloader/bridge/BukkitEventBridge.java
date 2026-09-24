package com.hitboy.pluginloader.bridge;

import com.hitboy.pluginloader.api.PluginEventBus;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The single real Bukkit {@link Listener} the loader registers. It converts
 * Bukkit's own events into HitBoy's simplified event types and publishes
 * them on {@link PluginEventBus} -- individual HitBoy plugins never see raw
 * Bukkit event objects, so their code stays stable across server versions.
 */
public final class BukkitEventBridge implements Listener {
    private final PluginEventBus eventBus;

    public BukkitEventBridge(PluginEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        com.hitboy.pluginloader.api.events.PlayerJoinEvent hitboyEvent =
            new com.hitboy.pluginloader.api.events.PlayerJoinEvent(
                player.getName(),
                player.getUniqueId(),
                event.getJoinMessage()
            );
        eventBus.publish(hitboyEvent);
        event.setJoinMessage(hitboyEvent.joinMessage());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        com.hitboy.pluginloader.api.events.PlayerQuitEvent hitboyEvent =
            new com.hitboy.pluginloader.api.events.PlayerQuitEvent(
                player.getName(),
                player.getUniqueId(),
                event.getQuitMessage()
            );
        eventBus.publish(hitboyEvent);
        event.setQuitMessage(hitboyEvent.quitMessage());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        com.hitboy.pluginloader.api.events.PlayerChatEvent hitboyEvent =
            new com.hitboy.pluginloader.api.events.PlayerChatEvent(
                player.getName(),
                player.getUniqueId(),
                event.getMessage()
            );
        eventBus.publish(hitboyEvent);
        if (hitboyEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }
        event.setMessage(hitboyEvent.message());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        com.hitboy.pluginloader.api.events.BlockBreakEvent hitboyEvent =
            new com.hitboy.pluginloader.api.events.BlockBreakEvent(
                player.getName(),
                player.getUniqueId(),
                event.getBlock().getWorld().getName(),
                event.getBlock().getX(),
                event.getBlock().getY(),
                event.getBlock().getZ(),
                event.getBlock().getType().name()
            );
        eventBus.publish(hitboyEvent);
        if (hitboyEvent.isCancelled()) {
            event.setCancelled(true);
        }
    }
}
