package de.betterplugins.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * Checks Modrinth for a newer release of the plugin and - if enabled in the config ({@code auto_update}) -
 * downloads it into the update folder ({@code plugins/update}). The server installs it on the next restart.
 * <p>
 * Only releases (no beta/alpha) for the Minecraft version and server software of this server are used,
 * downloads are verified with the SHA-512 hash from Modrinth. Runs fully asynchronous and never throws.
 */
public class UpdateMonitor {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final JavaPlugin plugin;
    private final String pluginName;
    private final String currentVersion;
    private final String modrinthProjectId;
    private final String modrinthSlug;
    private final String userAgent;
    /** The newer version found on Modrinth, null if the plugin is up-to-date (or the check failed). */
    private volatile String newerVersion;
    /** True once the newer version is in the update folder. */
    private volatile boolean downloaded;

    public UpdateMonitor(JavaPlugin plugin, String modrinthProjectId, String modrinthSlug) {
        this.plugin = plugin;
        this.pluginName = plugin.getPluginMeta().getName();
        this.currentVersion = plugin.getPluginMeta().getVersion();
        this.modrinthProjectId = modrinthProjectId;
        this.modrinthSlug = modrinthSlug;
        this.userAgent = "hassanbwo/" + pluginName + "/" + currentVersion;
    }

    /**
     * @param pluginJar the jar of the running plugin ({@code JavaPlugin#getFile()}), the update gets the same
     *                  file name so that the server replaces it on the next restart
     * @param config    the {@code auto_update} section of the config (may be null = only notify)
     */
    public void checkForUpdates(File pluginJar, ConfigurationSection config) {
        boolean autoUpdate = config != null && config.getBoolean("enabled", false);
        boolean majorVersions = config != null && config.getBoolean("major_versions", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.modrinth.com/v2/project/" + modrinthProjectId + "/version"))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", userAgent)
                .GET()
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        plugin.getLogger().warning("UpdateMonitor failed (HTTP " + response.statusCode() + ").");
                        return;
                    }
                    handleVersions(JsonParser.parseString(response.body()).getAsJsonArray(), pluginJar, autoUpdate, majorVersions);
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("UpdateMonitor failed.");
                    return null;
                });
    }

    private void handleVersions(JsonArray versions, File pluginJar, boolean autoUpdate, boolean majorVersions) {
        String gameVersion = plugin.getServer().getMinecraftVersion();
        String loader = serverLoader();
        JsonObject latestRelease = null;
        JsonObject latestCompatible = null;
        for (JsonElement element : versions) { // Modrinth returns the newest version first
            JsonObject version = element.getAsJsonObject();
            if (!"release".equals(string(version, "version_type"))) continue;
            if (latestRelease == null) latestRelease = version;
            if (contains(version, "game_versions", List.of(gameVersion)) && contains(version, "loaders", compatibleLoaders(loader))) {
                latestCompatible = version;
                break;
            }
        }

        if (latestCompatible == null || compareVersions(string(latestCompatible, "version_number"), currentVersion) <= 0) {
            if (latestRelease != null && compareVersions(string(latestRelease, "version_number"), currentVersion) > 0) {
                plugin.getLogger().info(pluginName + " " + string(latestRelease, "version_number")
                        + " is available, but not for Minecraft " + gameVersion + " (" + loader + "): " + getDownloadUrl());
            } else {
                plugin.getLogger().info(pluginName + " is up-to-date!");
            }
            return;
        }

        String latestVersion = string(latestCompatible, "version_number");
        newerVersion = latestVersion;
        boolean major = majorOf(latestVersion) > majorOf(currentVersion);
        if (!autoUpdate || (major && !majorVersions)) {
            plugin.getLogger().warning("A new version of " + pluginName + " is available: " + latestVersion
                    + " (The version (" + currentVersion + ") you are using is outdated)");
            plugin.getLogger().warning("Download the latest version here: " + getDownloadUrl()
                    + (autoUpdate ? " (big update - not downloaded automatically, see auto_update.major_versions)"
                    : " (or enable auto_update in the config.yml)"));
            return;
        }
        download(latestCompatible, latestVersion, pluginJar);
    }

    private void download(JsonObject version, String latestVersion, File pluginJar) {
        JsonObject file = primaryFile(version);
        if (file == null || pluginJar == null) {
            plugin.getLogger().warning("Auto update of " + pluginName + " " + latestVersion + " failed (no file), download it here: " + getDownloadUrl());
            return;
        }
        String url = string(file, "url");
        String sha512 = file.has("hashes") ? string(file.getAsJsonObject("hashes"), "sha512") : "";
        // same file name as the running jar: the server only replaces jars with the same name from the update folder
        Path target = plugin.getServer().getUpdateFolderFile().toPath().resolve(pluginJar.getName());

        try {
            if (Files.isRegularFile(target) && sha512.equalsIgnoreCase(sha512(Files.readAllBytes(target)))) {
                downloaded = true;
                plugin.getLogger().warning(pluginName + " " + latestVersion + " is already downloaded and will be installed on the next server restart.");
                return;
            }
        } catch (IOException ignored) {
            // download it again
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", userAgent)
                .GET()
                .build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> {
                    byte[] data = response.body();
                    if (response.statusCode() != 200 || data.length == 0) {
                        plugin.getLogger().warning("Auto update of " + pluginName + " failed (HTTP " + response.statusCode() + ").");
                        return;
                    }
                    if (sha512.isEmpty() || !sha512.equalsIgnoreCase(sha512(data))) {
                        plugin.getLogger().warning("Auto update of " + pluginName + " failed: the download does not match the checksum from Modrinth, it was discarded.");
                        return;
                    }
                    try {
                        Files.createDirectories(target.getParent());
                        Path temp = target.resolveSibling(target.getFileName() + ".download");
                        Files.write(temp, data);
                        try {
                            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        } catch (AtomicMoveNotSupportedException e) {
                            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        plugin.getLogger().warning("Auto update of " + pluginName + " failed: " + e.getMessage());
                        return;
                    }
                    downloaded = true;
                    plugin.getLogger().warning(pluginName + " " + latestVersion + " has been downloaded (you are using " + currentVersion
                            + ") and will be installed on the next server restart. Changelog: " + getDownloadUrl() + "/changelog");
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Auto update of " + pluginName + " failed.");
                    return null;
                });
    }

    /** The newer version on Modrinth, or null if there is none. */
    public String getNewerVersion() {
        return newerVersion;
    }

    /** True if the newer version was downloaded and is installed on the next restart. */
    public boolean isDownloaded() {
        return downloaded;
    }

    public String getDownloadUrl() {
        return "https://modrinth.com/plugin/" + modrinthSlug;
    }

    private static String serverLoader() {
        if (classExists("io.papermc.paper.threadedregions.RegionizedServer")) return "folia";
        if (classExists("org.purpurmc.purpur.PurpurConfig")) return "purpur";
        return "paper";
    }

    /** Folia needs a Folia build, Purpur also runs Paper plugins. */
    private static List<String> compatibleLoaders(String loader) {
        return switch (loader) {
            case "folia" -> List.of("folia");
            case "purpur" -> List.of("purpur", "paper", "spigot", "bukkit");
            default -> List.of("paper", "spigot", "bukkit");
        };
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static JsonObject primaryFile(JsonObject version) {
        if (!version.has("files")) return null;
        JsonArray files = version.getAsJsonArray("files");
        for (JsonElement file : files) {
            JsonObject object = file.getAsJsonObject();
            if (object.has("primary") && object.get("primary").getAsBoolean()) return object;
        }
        return files.isEmpty() ? null : files.get(0).getAsJsonObject();
    }

    private static boolean contains(JsonObject object, String member, List<String> values) {
        if (!object.has(member)) return false;
        for (JsonElement element : object.getAsJsonArray(member)) {
            if (values.contains(element.getAsString())) return true;
        }
        return false;
    }

    private static String string(JsonObject object, String member) {
        JsonElement element = object.get(member);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static String sha512(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(data));
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    static long majorOf(String version) {
        String[] parts = version.replaceAll("^[^0-9]+", "").split("[^0-9]+");
        return part(parts, 0);
    }

    /**
     * Compares version strings numerically, e.g. "4.10" > "4.9" and "v.4.2" == "4.2".
     * Everything that is not a digit is treated as a separator.
     */
    static int compareVersions(String a, String b) {
        String[] pa = a.replaceAll("^[^0-9]+", "").split("[^0-9]+");
        String[] pb = b.replaceAll("^[^0-9]+", "").split("[^0-9]+");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            long va = part(pa, i);
            long vb = part(pb, i);
            if (va != vb) return Long.compare(va, vb);
        }
        return 0;
    }

    private static long part(String[] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty()) return 0;
        try {
            return Long.parseLong(parts[index]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
