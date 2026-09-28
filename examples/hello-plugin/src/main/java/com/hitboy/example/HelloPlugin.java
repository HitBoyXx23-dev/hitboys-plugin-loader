package com.hitboy.example;

import com.hitboy.pluginloader.api.HitBoyPlugin;
import com.hitboy.pluginloader.api.PlayerHandle;
import com.hitboy.pluginloader.api.PluginContext;
import com.hitboy.pluginloader.api.ScheduledTask;
import com.hitboy.pluginloader.api.events.BlockPlaceEvent;
import com.hitboy.pluginloader.api.events.PlayerDeathEvent;
import com.hitboy.pluginloader.api.events.PlayerJoinEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An example HitBoy plugin that uses the whole API. It only ever touches the small
 * {@code com.hitboy.pluginloader.api} surface, so the same JAR runs on the standalone loader
 * (vanilla server.jar) and the mixed loader (Spigot/Paper/Purpur).
 */
public final class HelloPlugin implements HitBoyPlugin {
    @Override
    public void onEnable(PluginContext context) {
        context.logger().info(context.pluginName() + " v" + context.pluginVersion() + " enabled!");

        // Events
        context.events().subscribe(PlayerJoinEvent.class, event ->
            event.setJoinMessage("Welcome, " + event.playerName() + "! (greeted by HelloPlugin)"));
        context.events().subscribe(PlayerDeathEvent.class, event -> {
            if (event.deathMessage() != null) event.setDeathMessage(event.deathMessage() + " (RIP, says HelloPlugin)");
        });
        context.events().subscribe(BlockPlaceEvent.class, event -> {
            if (event.blockType().toLowerCase().endsWith("tnt")) {
                event.setCancelled(true);
                PlayerHandle player = context.server().player(event.playerName());
                if (player != null) player.sendMessage("HelloPlugin does not allow TNT here.");
            }
        });

        // Commands: reply to whoever ran them
        context.commands().registerCommand("hbhello", "Says hello.", (sender, args) -> {
            sender.sendMessage("Hello, " + sender.name() + "! (player: " + sender.isPlayer() + ", op: " + sender.isOp() + ")");
            PlayerHandle player = sender.player();
            if (player != null) {
                sender.sendMessage(String.format("You are in %s at %.1f %.1f %.1f", player.worldName(), player.x(), player.y(), player.z()));
            }
            return true;
        });
        context.commands().registerCommand("hbwho", "Lists online players.", (sender, args) -> {
            sender.sendMessage("Online (" + context.server().onlinePlayers().size() + "):");
            for (PlayerHandle player : context.server().onlinePlayers()) {
                sender.sendMessage(String.format(" - %s in %s at %.0f %.0f %.0f%s", player.name(), player.worldName(),
                    player.x(), player.y(), player.z(), player.isOp() ? " (op)" : ""));
            }
            return true;
        });

        context.commands().registerCommand("hbkick", "Kicks a player (operators only).", (sender, args) -> {
            if (!sender.isOp()) {
                sender.sendMessage("Only operators can use /hbkick.");
                return true;
            }
            if (args.length == 0) return false;
            PlayerHandle target = context.server().player(args[0]);
            if (target == null) {
                sender.sendMessage(args[0] + " is not online.");
                return true;
            }
            target.kick(args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Kicked by HelloPlugin");
            sender.sendMessage("Kicked " + target.name() + ".");
            return true;
        });

        // Scheduler: a one-off task, and a repeating one that stops itself after three runs
        context.scheduler().runLater(() -> {
            context.logger().info("HelloPlugin: scheduled task ran one second after start.");
            context.server().runCommand("say HelloPlugin ran this console command");
        }, 20);
        AtomicInteger runs = new AtomicInteger();
        ScheduledTask[] repeating = new ScheduledTask[1];
        repeating[0] = context.scheduler().runRepeating(() -> {
            int run = runs.incrementAndGet();
            context.logger().info("HelloPlugin: repeating task run " + run + " of 3.");
            if (run == 3) repeating[0].cancel();
        }, 40, 20);
    }

    @Override
    public void onDisable() {
        // scheduled tasks are cancelled by the loader
    }
}
