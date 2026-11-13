package de.betterplugins.core;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Adds new options of a plugin update to an existing YAML file (config.yml, messages_xx.yml) without touching
 * the values of the server owner.
 * <p>
 * The file is only rewritten if options are missing: the bundled file (with all its comments and the right order)
 * is used as a template and every value of the server owner is copied into it. The old file is kept as
 * {@code <name>.old}. Unknown options are kept as well and only reported in the console.
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    /**
     * @param name         file name in the plugin folder and in the jar, e.g. "config.yml"
     * @param obsoleteKeys options of old versions that are removed (including everything below them)
     * @param userSections sections whose entries are chosen by the server owner (e.g. a list of enchantments):
     *                     they are taken over completely and never completed with the default entries
     * @return true if the file was changed
     */
    public static boolean update(JavaPlugin plugin, String name, Collection<String> obsoleteKeys, Collection<String> userSections) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) return false;
        YamlConfiguration template = bundled(plugin, name);
        if (template == null) return false;
        return update(file, template, plugin.getLogger(), plugin.getName(), obsoleteKeys, userSections);
    }

    /**
     * Same as {@link #update(JavaPlugin, String, Collection, Collection)}, without a plugin (used by the tests).
     *
     * @param file     the config file of the server owner
     * @param template the bundled default config (with comments) - it is changed
     */
    public static boolean update(File file, YamlConfiguration template, java.util.logging.Logger logger, String pluginName,
                                 Collection<String> obsoleteKeys, Collection<String> userSections) {
        if (!file.exists()) return false;
        String name = file.getName();

        YamlConfiguration current = new YamlConfiguration();
        try {
            current.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            // never "repair" a file with a syntax error - the server owner would lose all values
            logger.warning(name + " could not be read (" + e.getMessage().lines().findFirst().orElse("")
                    + "), it is not updated. Please fix the file.");
            return false;
        }

        List<String> added = new ArrayList<>();
        for (String key : template.getKeys(true)) {
            if (template.isConfigurationSection(key) || insideUserSection(key, userSections)) continue;
            if (!current.isSet(key)) added.add(key);
        }
        for (String section : userSections) {
            // a user section that is missing completely is added with its default entries
            if (template.isSet(section) && !current.isSet(section)) added.add(section);
        }
        List<String> removed = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        List<String> keep = new ArrayList<>();
        for (String key : current.getKeys(true)) {
            if (current.isConfigurationSection(key)) continue;
            if (matches(key, obsoleteKeys)) {
                removed.add(key);
            } else if (!fitsInto(template, key) && !insideUserSection(key, userSections)) {
                removed.add(key); // e.g. a value that became a section in a newer version
            } else {
                if (!template.isSet(key) && !insideUserSection(key, userSections)) unknown.add(key);
                keep.add(key);
            }
        }
        if (added.isEmpty() && removed.isEmpty()) return false;

        // template with comments + all values of the server owner
        for (String section : userSections) {
            if (current.isConfigurationSection(section) && template.isConfigurationSection(section)) {
                // remove only the default entries, the section keeps its place and comments
                for (String child : template.getConfigurationSection(section).getKeys(false)) {
                    template.set(section + "." + child, null);
                }
            }
        }
        for (String key : keep) {
            template.set(key, current.get(key));
        }
        template.options().width(Integer.MAX_VALUE); // keep long texts on one line

        File backup = new File(file.getParentFile(), name + ".old");
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            template.save(file);
        } catch (IOException e) {
            logger.warning(name + " could not be updated: " + e.getMessage());
            return false;
        }
        if (!added.isEmpty()) {
            logger.info(name + " updated: " + added.size() + " new option(s) added (" + String.join(", ", added)
                    + "). Your values were kept, backup: " + backup.getName());
        }
        if (!removed.isEmpty()) {
            logger.info(name + ": " + removed.size() + " option(s) of older versions removed ("
                    + String.join(", ", removed) + "), backup: " + backup.getName());
        }
        if (!unknown.isEmpty()) {
            logger.warning(name + ": unknown option(s) kept, they are not used by "
                    + pluginName + ": " + String.join(", ", unknown));
        }
        return true;
    }

    private static YamlConfiguration bundled(JavaPlugin plugin, String name) {
        InputStream stream = plugin.getResource(name);
        if (stream == null) return null;
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            yaml.load(reader);
            return yaml;
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Bundled " + name + " could not be read: " + e.getMessage());
            return null;
        }
    }

    /** True if key is one of the given keys or below one of them. */
    private static boolean matches(String key, Collection<String> keys) {
        for (String k : keys) {
            if (key.equals(k) || key.startsWith(k + ".")) return true;
        }
        return false;
    }

    private static boolean insideUserSection(String key, Collection<String> userSections) {
        for (String section : userSections) {
            if (key.startsWith(section + ".")) return true;
        }
        return false;
    }

    /** False if the key would overwrite a section of the template or needs a section where the template has a value. */
    private static boolean fitsInto(YamlConfiguration template, String key) {
        if (template.isConfigurationSection(key)) return false;
        int dot = key.lastIndexOf('.');
        while (dot > 0) {
            String parent = key.substring(0, dot);
            if (template.isSet(parent) && !template.isConfigurationSection(parent)) return false;
            dot = parent.lastIndexOf('.');
        }
        return true;
    }
}
