# HitBoy's Plugin Loader

Server-side plugin loaders for Minecraft, with their own plugin API. This is
the server counterpart of [HitBoy's Mod Loader](https://github.com/HitBoyXx23-dev/hitboys-mod-loader).
Just as the mod loader is not Fabric, Forge, or NeoForge, the plugin loader is
not Bukkit, Spigot, Paper, or Purpur.

## Downloads

- [HitBoy's Plugin Loader](https://github.com/HitBoyXx23-dev/hitboys-plugin-loader/releases/latest/download/hitboys-plugin-loader.jar): runs Mojang's vanilla `server.jar`
- [HitBoy's Mixed Plugin Loader](https://github.com/HitBoyXx23-dev/hitboys-plugin-loader/releases/latest/download/hitboys-mixed-plugin-loader.jar): runs HitBoy plugins next to Spigot, Paper, and Purpur plugins
- [HitBoy Plugin API](https://github.com/HitBoyXx23-dev/hitboys-plugin-loader/releases/latest/download/hitboys-plugin-api.jar) (for plugin developers)
- [Example plugin](https://github.com/HitBoyXx23-dev/hitboys-plugin-loader/releases/latest/download/hello-plugin.jar)

## Which one do I need?

| Loader | Server | HitBoy plugins | Bukkit/Spigot/Paper/Purpur plugins |
|---|---|---|---|
| `hitboys-plugin-loader.jar` | Mojang's vanilla `server.jar` | Yes | No |
| `hitboys-mixed-plugin-loader.jar` | Spigot, Paper, or Purpur | Yes | Yes, together |

A HitBoy plugin JAR works on both loaders without changes.

## Standalone loader (vanilla server.jar)

1. Download Mojang's `server.jar` and put `hitboys-plugin-loader.jar` in the same folder.
2. Start the server through the loader:

   ```powershell
   java -jar hitboys-plugin-loader.jar nogui
   ```

   Use `--server <path>` if the server JAR has another name. Server arguments
   such as `nogui` are passed through. Hosting panels that must start
   `server.jar` directly can use
   `java -javaagent:hitboys-plugin-loader.jar -jar server.jar nogui` instead.
3. Put HitBoy plugins in the `hitboy-plugins` folder. Each plugin gets a data
   folder at `hitboy-plugins/<PluginName>/`.
4. `/hitboyplugins` (or `/hbp`) lists loaded plugins.

The loader starts the vanilla server in the same JVM and patches a few server
methods as they load (server start and stop, commands, join, quit, chat, and
block breaking). `server.jar` on disk is never modified. Minecraft 26.1 and
newer ship unobfuscated. For older versions, the loader downloads Mojang's
official server mappings from Mojang on first start and caches them in
`hitboy/mappings`. Mojang's license does not allow redistributing those
mappings, so they are never bundled.

Tested with Mojang's vanilla server for **26.3** and **1.21.11**: plugins load,
commands work (including after `/reload`), and join and quit events fire for
real players. Other versions from 1.20.5 onward use the same server methods
but are untested. The loader logs a warning if a hook is missing on your
version.

## Mixed loader (Spigot/Paper/Purpur)

1. Put `hitboys-mixed-plugin-loader.jar` in the server's `plugins` folder,
   next to your normal Bukkit plugins.
2. Start the server once.
3. Put HitBoy plugins in `plugins/HitBoysMixedPluginLoader/plugins/`.
4. `/hitboyplugins` lists them; `/hitboyplugins reload` reloads them.

It is compiled as Java 8 bytecode and uses Bukkit APIs that have existed since
1.8.9, so it loads on legacy servers too.

## Writing a plugin

A HitBoy plugin is a JAR with a `plugin.json` at its root and a main class that
implements `com.hitboy.pluginloader.api.HitBoyPlugin`. Compile against
`hitboys-plugin-api.jar` (Java 8 or newer).

```json
{
  "name": "HelloPlugin",
  "version": "1.0.0",
  "main": "com.hitboy.example.HelloPlugin",
  "description": "Optional.",
  "author": "Optional."
}
```

```java
public final class HelloPlugin implements HitBoyPlugin {
    @Override
    public void onEnable(PluginContext context) {
        context.events().subscribe(PlayerJoinEvent.class, event ->
            event.setJoinMessage("Welcome, " + event.playerName() + "!"));

        context.commands().register("hbhello", "Says hello.", (sender, args) -> {
            context.server().broadcast("Hello, " + sender + "!");
            return true;
        });
    }
}
```

`PluginContext` provides a logger, a data folder, the event bus, the command
registry, and `server().broadcast(...)`. Events: `PlayerJoinEvent`,
`PlayerQuitEvent`, `PlayerChatEvent` (cancel or change the message), and
`BlockBreakEvent` (cancellable). See `examples/hello-plugin`.

## Building

```powershell
mvn clean install
Set-Location examples\hello-plugin
mvn clean package
```

Outputs:

- `standalone/target/hitboys-plugin-loader.jar`
- `mixed/target/hitboys-mixed-plugin-loader.jar`
- `api/target/hitboys-plugin-api.jar`
- `examples/hello-plugin/target/hello-plugin.jar`

## Limits

- The standalone loader cannot run Bukkit plugins. Vanilla has no Bukkit API;
  use the mixed loader on Spigot, Paper, or Purpur for that.
- No plugin dependency ordering or permission nodes yet.
- Chat and block-break events were confirmed to patch cleanly, but in testing
  only join, quit, and commands were exercised by a real client.

## License

See `LICENSE`.
