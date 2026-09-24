package com.hitboy.example;

import com.hitboy.pluginloader.api.HitBoyPlugin;
import com.hitboy.pluginloader.api.PluginContext;
import com.hitboy.pluginloader.api.events.PlayerJoinEvent;

/**
 * A minimal example HitBoy plugin. It only ever touches the small
 * {@code com.hitboy.pluginloader.api} surface, so the same JAR runs on the
 * standalone loader (vanilla server.jar) and the mixed loader (Spigot/Paper/Purpur).
 */
public final class HelloPlugin implements HitBoyPlugin {
    @Override
    public void onEnable(PluginContext context) {
        context.logger().info(context.pluginName() + " v" + context.pluginVersion() + " enabled!");

        context.events().subscribe(PlayerJoinEvent.class, event -> {
            event.setJoinMessage("Welcome, " + event.playerName() + "! (greeted by HelloPlugin)");
        });

        context.commands().register("hbhello", "Says hello.", (senderName, args) -> {
            context.server().broadcast("Hello, " + senderName + "! (from HelloPlugin)");
            return true;
        });
    }

    @Override
    public void onDisable() {
        // nothing to clean up in this example
    }
}
