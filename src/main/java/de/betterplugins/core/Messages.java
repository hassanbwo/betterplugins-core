package de.betterplugins.core;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Language files: {@code messages_<language>.yml} in the plugin folder.
 * <p>
 * The bundled languages are written to the plugin folder on startup (existing files are never overwritten).
 * Server owners can also add their own file, e.g. {@code messages_fr.yml}. Keys that are missing in the
 * selected file fall back to the bundled file of that language and finally to English.
 */
public final class Messages {

    public static final List<String> BUNDLED_LANGUAGES = List.of("en", "de", "fr", "es", "pl", "ru", "pt", "tr", "zh");

    private final JavaPlugin plugin;
    private volatile YamlConfiguration messages = new YamlConfiguration();
    private volatile String language = "en";

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public File file(String language) {
        return new File(plugin.getDataFolder(), "messages_" + language + ".yml");
    }

    /** Writes the bundled language files that do not exist yet and adds new texts to existing ones. */
    public void saveBundledFiles() {
        for (String lang : BUNDLED_LANGUAGES) {
            String name = "messages_" + lang + ".yml";
            if (!file(lang).exists()) {
                plugin.saveResource(name, false);
            } else {
                ConfigUpdater.update(plugin, name, List.of(), List.of());
            }
        }
    }

    public void load(String configuredLanguage) {
        saveBundledFiles();
        String lang = TextFormatter.normalizeLanguage(configuredLanguage);
        File file = file(lang);
        if (!file.exists()) {
            plugin.getLogger().warning("Language file " + file.getName() + " not found, using English (messages_en.yml).");
            lang = "en";
            file = file("en");
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = bundled(lang);
        if (defaults == null) {
            defaults = new YamlConfiguration();
        }
        if (!lang.equals("en")) {
            YamlConfiguration english = bundled("en");
            if (english != null) defaults.setDefaults(english);
        }
        loaded.setDefaults(defaults);

        this.messages = loaded;
        this.language = lang;
    }

    private YamlConfiguration bundled(String lang) {
        InputStream stream = plugin.getResource("messages_" + lang + ".yml");
        if (stream == null) return null;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            return null;
        }
    }

    public String language() {
        return language;
    }

    /** Raw message text (MiniMessage/legacy codes not parsed yet), empty string if not set. */
    public String raw(String path) {
        String value = messages.getString(path);
        return value == null ? "" : value;
    }

    /** Parsed message. */
    public Component get(String path) {
        return TextFormatter.parse(raw(path));
    }
}
