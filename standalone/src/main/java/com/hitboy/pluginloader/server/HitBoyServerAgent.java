package com.hitboy.pluginloader.server;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

/**
 * Java agent entry point. Runs either through {@code Launcher-Agent-Class} when started as
 * {@code java -jar hitboys-plugin-loader.jar}, or through {@code -javaagent:hitboys-plugin-loader.jar}
 * in front of {@code -jar server.jar}.
 *
 * <p>Mojang's bundler starts the server in a class loader whose parent is the platform class loader,
 * so the loader JAR is appended to the bootstrap search path first. Every other HitBoy class is then
 * loaded from the bootstrap loader and is visible to patched server code.
 */
public final class HitBoyServerAgent {
    private HitBoyServerAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) throws Exception {
        install(instrumentation);
        String classPath = System.getProperty("java.class.path", "");
        String serverJar = classPath.split(File.pathSeparator)[0];
        ServerRuntime.start(new File(serverJar));
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) throws Exception {
        install(instrumentation);
    }

    private static void install(Instrumentation instrumentation) throws Exception {
        File loaderJar = new File(HitBoyServerAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(loaderJar));
        ServerRuntime.setInstrumentation(instrumentation);
    }
}
