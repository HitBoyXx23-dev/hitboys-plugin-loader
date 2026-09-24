package com.hitboy.pluginloader.core;

import com.google.gson.Gson;
import com.hitboy.pluginloader.api.CommandRegistry;
import com.hitboy.pluginloader.api.HitBoyPlugin;
import com.hitboy.pluginloader.api.PluginContext;
import com.hitboy.pluginloader.api.PluginEventBus;
import com.hitboy.pluginloader.api.ServerAccess;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scans a directory for HitBoy plugin jars (each containing a
 * {@code plugin.json} at the jar root), loads each one in its own
 * classloader, instantiates its {@link HitBoyPlugin} main class, and calls
 * its lifecycle methods. Mirrors the client-side {@code NativeLoader}'s
 * jar-scanning design. Shared by the standalone and mixed loaders.
 */
public final class PluginManager {
    private final Logger logger;
    private final File pluginsDirectory;
    private final File dataRoot;
    private final PluginEventBus eventBus;
    private final CommandRegistry commandRegistry;
    private final ServerAccess server;
    private final List<LoadedPlugin> loadedPlugins = new ArrayList<>();

    public PluginManager(
        Logger logger,
        File pluginsDirectory,
        File dataRoot,
        PluginEventBus eventBus,
        CommandRegistry commandRegistry,
        ServerAccess server
    ) {
        this.logger = logger;
        this.pluginsDirectory = pluginsDirectory;
        this.dataRoot = dataRoot;
        this.eventBus = eventBus;
        this.commandRegistry = commandRegistry;
        this.server = server;
    }

    /** Scans {@code pluginsDirectory} for jars and enables everything found. */
    public void loadAll() {
        if (!pluginsDirectory.exists()) {
            pluginsDirectory.mkdirs();
            return;
        }
        File[] jars = pluginsDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars == null) {
            return;
        }
        for (File jar : jars) {
            try {
                loadOne(jar);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load HitBoy plugin from " + jar.getName(), e);
            }
        }
    }

    private void loadOne(File jar) throws IOException, ReflectiveOperationException {
        PluginDescriptor descriptor;
        try (JarFile jarFile = new JarFile(jar)) {
            JarEntry entry = jarFile.getJarEntry("plugin.json");
            if (entry == null) {
                logger.warning(jar.getName() + " has no plugin.json at its root -- skipping.");
                return;
            }
            try (InputStreamReader reader =
                new InputStreamReader(jarFile.getInputStream(entry), StandardCharsets.UTF_8)) {
                descriptor = new Gson().fromJson(reader, PluginDescriptor.class);
            }
        }
        if (descriptor == null || !descriptor.isValid()) {
            logger.warning(jar.getName() + " has an invalid plugin.json (needs name, version, main) -- skipping.");
            return;
        }

        URLClassLoader classLoader = new URLClassLoader(
            new URL[] {jar.toURI().toURL()},
            getClass().getClassLoader()
        );
        Class<?> mainClass = Class.forName(descriptor.main(), true, classLoader);
        if (!HitBoyPlugin.class.isAssignableFrom(mainClass)) {
            logger.warning(descriptor.name() + "'s main class " + descriptor.main()
                + " does not implement HitBoyPlugin -- skipping.");
            classLoader.close();
            return;
        }

        HitBoyPlugin instance = (HitBoyPlugin) mainClass.getDeclaredConstructor().newInstance();
        File dataFolder = new File(dataRoot, descriptor.name());
        Logger pluginLogger = Logger.getLogger("HitBoyPlugin/" + descriptor.name());
        PluginContext context = new PluginContext(
            descriptor.name(),
            descriptor.version(),
            dataFolder,
            pluginLogger,
            eventBus,
            commandRegistry,
            server
        );

        instance.onEnable(context);
        loadedPlugins.add(new LoadedPlugin(descriptor, instance, classLoader));
        logger.info("Loaded HitBoy plugin " + descriptor.name() + " v" + descriptor.version()
            + " by " + descriptor.author());
    }

    /** Disables every loaded plugin, in reverse load order. */
    public void disableAll() {
        for (int i = loadedPlugins.size() - 1; i >= 0; i--) {
            LoadedPlugin loaded = loadedPlugins.get(i);
            try {
                loaded.instance().onDisable();
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Error disabling " + loaded.descriptor().name(), e);
            } finally {
                try {
                    loaded.classLoader().close();
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
        loadedPlugins.clear();
    }

    public List<LoadedPlugin> loadedPlugins() {
        return Collections.unmodifiableList(new ArrayList<>(loadedPlugins));
    }

    public static final class LoadedPlugin {
        private final PluginDescriptor descriptor;
        private final HitBoyPlugin instance;
        private final URLClassLoader classLoader;

        public LoadedPlugin(PluginDescriptor descriptor, HitBoyPlugin instance, URLClassLoader classLoader) {
            this.descriptor = descriptor;
            this.instance = instance;
            this.classLoader = classLoader;
        }

        public PluginDescriptor descriptor() {
            return descriptor;
        }

        public HitBoyPlugin instance() {
            return instance;
        }

        public URLClassLoader classLoader() {
            return classLoader;
        }
    }
}
