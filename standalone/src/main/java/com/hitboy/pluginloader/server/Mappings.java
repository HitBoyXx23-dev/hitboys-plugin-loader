package com.hitboy.pluginloader.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Translates Mojang's official names to the names inside the running server JAR.
 *
 * <p>Minecraft 26.1 and newer ship unobfuscated, so names are used as-is. Older releases are
 * obfuscated; for those, Mojang's official server mappings are downloaded once from Mojang and cached
 * under {@code hitboy/mappings}. The mappings are never redistributed with HitBoy.
 */
final class Mappings {
    private static final String VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private final String version;
    private final Map<String, String> classes = new HashMap<>();
    private final Map<String, String> methods = new HashMap<>();
    private final Map<String, String> fields = new HashMap<>();
    private final boolean identity;

    private Mappings(String version, boolean identity) {
        this.version = version;
        this.identity = identity;
    }

    static Mappings forServerJar(File serverJar, File cacheDirectory) throws IOException {
        String version;
        String innerJarPath;
        try (JarFile jar = new JarFile(serverJar)) {
            version = readVersion(jar);
            innerJarPath = readInnerJarPath(jar);
        }
        if (!isObfuscated(serverJar, innerJarPath)) {
            return new Mappings(version, true);
        }
        File cached = new File(cacheDirectory, version + "-server.txt");
        if (!cached.isFile()) {
            Files.createDirectories(cacheDirectory.toPath());
            HitBoyServerHooks.log("Minecraft " + version + " is obfuscated; downloading Mojang's official server mappings...");
            download(mappingsUrl(version), cached);
        }
        Mappings mappings = new Mappings(version, false);
        mappings.parse(cached);
        return mappings;
    }

    String version() {
        return version;
    }

    boolean isObfuscated() {
        return !identity;
    }

    /** Runtime (dotted) class name for a Mojang class name. */
    String className(String mojangName) {
        if (identity) return mojangName;
        String mapped = classes.get(mojangName);
        return mapped == null ? mojangName : mapped;
    }

    /** Runtime internal (slashed) class name for a Mojang class name. */
    String internalName(String mojangName) {
        return className(mojangName).replace('.', '/');
    }

    /**
     * Runtime method name.
     *
     * @param parameters Java-style parameter types, comma-separated, as in Mojang's mapping files,
     *                   e.g. {@code net.minecraft.core.BlockPos} or {@code net.minecraft.network.chat.Component,boolean}
     */
    String methodName(String owner, String name, String parameters) {
        if (identity) return name;
        String mapped = methods.get(owner + "#" + name + "(" + parameters + ")");
        if (mapped == null) {
            throw new IllegalStateException("No mapping for " + owner + "#" + name + "(" + parameters + ") in Minecraft " + version);
        }
        return mapped;
    }

    /** Runtime field name. */
    String fieldName(String owner, String name) {
        if (identity) return name;
        String mapped = fields.get(owner + "#" + name);
        if (mapped == null) throw new IllegalStateException("No mapping for field " + owner + "#" + name + " in Minecraft " + version);
        return mapped;
    }

    /** Runtime JVM descriptor for a descriptor written with Mojang class names. */
    String descriptor(String mojangDescriptor) {
        if (identity) return mojangDescriptor;
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < mojangDescriptor.length(); index++) {
            char character = mojangDescriptor.charAt(index);
            result.append(character);
            if (character == 'L') {
                int end = mojangDescriptor.indexOf(';', index);
                String name = mojangDescriptor.substring(index + 1, end).replace('/', '.');
                result.append(internalName(name)).append(';');
                index = end;
            }
        }
        return result.toString();
    }

    private void parse(File file) throws IOException {
        String currentClass = null;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                int arrow = line.indexOf(" -> ");
                if (arrow < 0) continue;
                if (!line.startsWith(" ")) {
                    currentClass = line.substring(0, arrow);
                    classes.put(currentClass, line.substring(arrow + 4, line.length() - 1));
                    continue;
                }
                if (currentClass == null) continue;
                if (line.indexOf('(') < 0) {
                    // "    type name -> x" (a field)
                    String field = line.substring(0, arrow).trim();
                    fields.put(currentClass + "#" + field.substring(field.lastIndexOf(' ') + 1), line.substring(arrow + 4).trim());
                    continue;
                }
                // "    12:34:void name(a.B,int) -> x" or "    void name(a.B,int) -> x"
                String signature = line.substring(0, arrow).trim();
                signature = signature.substring(signature.lastIndexOf(':') + 1);
                String nameAndParameters = signature.substring(signature.indexOf(' ') + 1);
                methods.put(currentClass + "#" + nameAndParameters, line.substring(arrow + 4).trim());
            }
        }
    }

    private static String readVersion(JarFile jar) throws IOException {
        JarEntry entry = jar.getJarEntry("version.json");
        if (entry == null) throw new IOException("This does not look like Mojang's server.jar (no version.json).");
        try (InputStream input = jar.getInputStream(entry)) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            return json.get("id").getAsString();
        }
    }

    private static String readInnerJarPath(JarFile jar) throws IOException {
        JarEntry entry = jar.getJarEntry("META-INF/versions.list");
        if (entry == null) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null ? null : "META-INF/versions/" + line.split("\t")[2];
        }
    }

    private static boolean isObfuscated(File serverJar, String innerJarPath) throws IOException {
        String probe = "net/minecraft/server/players/PlayerList.class";
        try (JarFile jar = new JarFile(serverJar)) {
            if (innerJarPath == null) return jar.getJarEntry(probe) == null;
            try (ZipInputStream inner = new ZipInputStream(jar.getInputStream(jar.getJarEntry(innerJarPath)))) {
                ZipEntry entry;
                while ((entry = inner.getNextEntry()) != null) {
                    if (probe.equals(entry.getName())) return false;
                }
            }
        }
        return true;
    }

    private static String mappingsUrl(String version) throws IOException {
        JsonArray versions = readJson(VERSION_MANIFEST).getAsJsonArray("versions");
        for (JsonElement element : versions) {
            JsonObject entry = element.getAsJsonObject();
            if (!version.equals(entry.get("id").getAsString())) continue;
            JsonObject downloads = readJson(entry.get("url").getAsString()).getAsJsonObject("downloads");
            if (!downloads.has("server_mappings")) break;
            return downloads.getAsJsonObject("server_mappings").get("url").getAsString();
        }
        throw new IOException("Mojang does not publish server mappings for Minecraft " + version
            + ", so the standalone HitBoy loader cannot patch it.");
    }

    private static JsonObject readJson(String url) throws IOException {
        try (InputStream input = new URL(url).openStream()) {
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void download(String url, File destination) throws IOException {
        File temporary = new File(destination.getPath() + ".part");
        try (InputStream input = new URL(url).openStream()) {
            Files.copy(input, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
