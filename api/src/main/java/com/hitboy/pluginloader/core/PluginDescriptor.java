package com.hitboy.pluginloader.core;

/**
 * Parsed form of a HitBoy plugin's {@code plugin.json} descriptor -- the
 * loader's equivalent of Bukkit's {@code plugin.yml}. Kept intentionally
 * small: name, version, main class, and an optional description/author.
 */
public final class PluginDescriptor {
    private String name;
    private String version;
    private String main;
    private String description;
    private String author;

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public String main() {
        return main;
    }

    public String description() {
        return description == null ? "" : description;
    }

    public String author() {
        return author == null ? "unknown" : author;
    }

    public boolean isValid() {
        return hasText(name) && hasText(version) && hasText(main);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
