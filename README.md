# BetterPlugins Core

Shared code of all [BetterPlugins](https://modrinth.com/collection/4cB8DVqy) (BetterTimber, BetterAFK, BetterCarry, BetterRIP, BetterSort, BetterWaystones, BetterTimberEnchantment, BannerForLabyMod).

| Class | What it does |
|---|---|
| `ConfigUpdater` | Adds new options to an existing config.yml with the comments of the bundled file, keeps the values of the server owner, writes a `.old` backup, never touches a file with a syntax error |
| `UpdateMonitor` | Modrinth update check for the server's Minecraft version and loader (Paper, Purpur, Folia), optional download with SHA-512 check |
| `Messages` | Language files `messages_<language>.yml` with fallback to the bundled file and to English |
| `TextFormatter` | MiniMessage and classic `&` color codes in one text |

## Use in a plugin (Maven)

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.hassanbwo</groupId>
    <artifactId>betterplugins-core</artifactId>
    <version>1.0.0</version>
    <scope>compile</scope>
</dependency>
```

Shade it into the plugin jar and **relocate** it to the plugin's own package (`de.betterplugins.core` -> `de.betterplugins.<plugin>.core`), so plugins with different core versions never conflict on one server.

## New version

1. Change the code, `mvn verify` (all tests must pass)
2. Raise `<version>` in `pom.xml`, commit, create a git tag with the same version (e.g. `1.1.0`)
3. Raise `betterplugins-core.version` in the plugins
