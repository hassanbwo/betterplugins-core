package de.betterplugins.core;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigUpdaterTest {

    private static final Logger LOG = Logger.getLogger("test");

    @TempDir
    Path dir;

    private static YamlConfiguration template(String yaml) throws Exception {
        YamlConfiguration template = new YamlConfiguration();
        template.loadFromString(yaml);
        return template;
    }

    private File config(String yaml) throws Exception {
        File file = dir.resolve("config.yml").toFile();
        Files.writeString(file.toPath(), yaml);
        return file;
    }

    private static YamlConfiguration read(File file) {
        return YamlConfiguration.loadConfiguration(file);
    }

    @Test
    void addsNewOptionsAndKeepsValues() throws Exception {
        File file = config("speed: 5\n");
        boolean changed = ConfigUpdater.update(file, template("# the speed\nspeed: 1\n# new\nsound: true\n"), LOG, "Test", List.of(), List.of());
        assertTrue(changed);
        YamlConfiguration result = read(file);
        assertEquals(5, result.getInt("speed"), "value of the server owner is kept");
        assertTrue(result.getBoolean("sound"), "new option is added");
        assertTrue(Files.readString(file.toPath()).contains("# the speed"), "comments of the template are kept");
        assertTrue(new File(dir.toFile(), "config.yml.old").exists(), "backup is written");
    }

    @Test
    void unchangedConfigIsNotTouched() throws Exception {
        File file = config("speed: 5\n");
        assertFalse(ConfigUpdater.update(file, template("speed: 1\n"), LOG, "Test", List.of(), List.of()));
        assertEquals("speed: 5\n", Files.readString(file.toPath()));
        assertFalse(new File(dir.toFile(), "config.yml.old").exists());
    }

    @Test
    void unknownOptionsAreKept() throws Exception {
        File file = config("speed: 5\nmine: 7\n");
        ConfigUpdater.update(file, template("speed: 1\nsound: true\n"), LOG, "Test", List.of(), List.of());
        assertEquals(7, read(file).getInt("mine"));
    }

    @Test
    void obsoleteOptionsAreRemoved() throws Exception {
        File file = config("speed: 5\nold:\n  a: 1\n  b: 2\n");
        ConfigUpdater.update(file, template("speed: 1\n"), LOG, "Test", List.of("old"), List.of());
        assertFalse(read(file).isSet("old"));
        assertEquals(5, read(file).getInt("speed"));
    }

    @Test
    void userSectionIsNotCompletedWithDefaults() throws Exception {
        File file = config("worlds:\n  mine: a\n");
        ConfigUpdater.update(file, template("worlds:\n  example: x\nsound: true\n"), LOG, "Test", List.of(), List.of("worlds"));
        YamlConfiguration result = read(file);
        assertEquals("a", result.getString("worlds.mine"));
        assertFalse(result.isSet("worlds.example"), "default entries are not added to an own section");
    }

    @Test
    void missingUserSectionIsAdded() throws Exception {
        // regression: a user section that was missing completely was never added
        File file = config("speed: 5\n");
        assertTrue(ConfigUpdater.update(file, template("speed: 1\nworlds:\n  example: x\n"), LOG, "Test", List.of(), List.of("worlds")));
        assertEquals("x", read(file).getString("worlds.example"));
    }

    @Test
    void brokenYamlIsNeverRepaired() throws Exception {
        String broken = "speed: [5\n";
        File file = config(broken);
        assertFalse(ConfigUpdater.update(file, template("speed: 1\nsound: true\n"), LOG, "Test", List.of(), List.of()));
        assertEquals(broken, Files.readString(file.toPath()));
    }

    @Test
    void valueThatBecameASectionIsReplaced() throws Exception {
        File file = config("sound: true\n");
        ConfigUpdater.update(file, template("sound:\n  enabled: true\n  volume: 1.0\n"), LOG, "Test", List.of(), List.of());
        YamlConfiguration result = read(file);
        assertTrue(result.isConfigurationSection("sound"));
        assertEquals(1.0, result.getDouble("sound.volume"));
    }
}
