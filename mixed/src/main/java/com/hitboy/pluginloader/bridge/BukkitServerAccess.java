package com.hitboy.pluginloader.bridge;

import com.hitboy.pluginloader.api.PlayerHandle;
import com.hitboy.pluginloader.api.ServerAccess;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** {@link ServerAccess} on Spigot/Paper/Purpur, through Bukkit APIs that exist since 1.8. */
public final class BukkitServerAccess implements ServerAccess {
    @Override
    public void broadcast(String message) {
        Bukkit.broadcastMessage(message);
    }

    @Override
    public List<PlayerHandle> onlinePlayers() {
        List<PlayerHandle> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) players.add(new BukkitPlayer(player));
        return players;
    }

    @Override
    public PlayerHandle player(String name) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) return new BukkitPlayer(player);
        }
        return null;
    }

    @Override
    public void runCommand(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.startsWith("/") ? command.substring(1) : command);
    }
}
