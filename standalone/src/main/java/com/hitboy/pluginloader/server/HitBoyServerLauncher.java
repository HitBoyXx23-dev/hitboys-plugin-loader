package com.hitboy.pluginloader.server;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * {@code java -jar hitboys-plugin-loader.jar [--server server.jar] [server arguments...]}
 *
 * <p>Starts Mojang's vanilla {@code server.jar} in this JVM with HitBoy's hooks installed.
 */
public final class HitBoyServerLauncher {
    private HitBoyServerLauncher() {
    }

    public static void main(String[] arguments) throws Exception {
        File serverJar = new File("server.jar");
        List<String> serverArguments = new ArrayList<>();
        for (int index = 0; index < arguments.length; index++) {
            if ("--server".equals(arguments[index]) && index + 1 < arguments.length) {
                serverJar = new File(arguments[++index]);
            } else {
                serverArguments.add(arguments[index]);
            }
        }
        if (!serverJar.isFile()) {
            System.err.println("[HitBoy] Vanilla server JAR not found: " + serverJar.getAbsolutePath());
            System.err.println("[HitBoy] Put Mojang's server.jar next to this file, or pass --server <path>.");
            System.exit(1);
        }
        if (!ServerRuntime.hasInstrumentation()) {
            System.err.println("[HitBoy] The HitBoy agent did not start. Run with: java -jar hitboys-plugin-loader.jar");
            System.exit(1);
        }
        ServerRuntime.start(serverJar);

        String mainClassName;
        try (JarFile jar = new JarFile(serverJar)) {
            mainClassName = jar.getManifest().getMainAttributes().getValue("Main-Class");
        }
        URLClassLoader serverLoader = new URLClassLoader(new URL[] {serverJar.toURI().toURL()}, ClassLoader.getSystemClassLoader());
        Thread.currentThread().setContextClassLoader(serverLoader);
        Method main = Class.forName(mainClassName, true, serverLoader).getMethod("main", String[].class);
        main.invoke(null, (Object) serverArguments.toArray(new String[0]));
    }
}
