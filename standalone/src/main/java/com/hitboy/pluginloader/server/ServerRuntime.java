package com.hitboy.pluginloader.server;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;

/** Public because the agent class stays in the app class loader while this one loads from bootstrap. Holds the agent's instrumentation and installs the vanilla server hooks once the server JAR is known. */
public final class ServerRuntime {
    private static Instrumentation instrumentation;
    private static Mappings mappings;
    private static ServerTransformer transformer;

    private ServerRuntime() {
    }

    public static void setInstrumentation(Instrumentation value) {
        instrumentation = value;
    }

    public static boolean hasInstrumentation() {
        return instrumentation != null;
    }

    public static synchronized void start(File serverJar) throws IOException {
        if (mappings != null) return;
        HitBoyServerHooks.configureLogging();
        mappings = Mappings.forServerJar(serverJar, new File("hitboy" + File.separator + "mappings"));
        transformer = new ServerTransformer(mappings);
        instrumentation.addTransformer(transformer);
        HitBoyServerHooks.log("HitBoy's Plugin Loader starting Minecraft " + mappings.version()
            + (mappings.isObfuscated() ? " (using Mojang's official mappings)" : ""));
    }

    static Mappings mappings() {
        return mappings;
    }

    static ServerTransformer transformer() {
        return transformer;
    }
}
