package de.betterplugins.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns message strings into components.
 * <p>
 * Supports <a href="https://docs.advntr.dev/minimessage/format">MiniMessage</a>
 * ({@code <red>}, {@code <bold>}, {@code <gradient:#ff5555:#ffaa00>text</gradient>}, {@code <#ff8800>}, ...)
 * and - for existing configs - the classic color codes ({@code &c}, {@code §c}, {@code &#ff8800}, {@code &x&f&f&8&8&0&0}).
 * Both can be mixed in one message.
 */
public final class TextFormatter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    /** &x&r&r&g&g&b&b (Spigot hex format) */
    private static final Pattern SPIGOT_HEX = Pattern.compile("[&§][xX]((?:[&§][0-9a-fA-F]){6})");
    /** &#rrggbb */
    private static final Pattern HEX = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    /** &c, &l, ... */
    private static final Pattern CODE = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");

    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset"));

    private TextFormatter() {
    }

    /** Parses MiniMessage and classic color codes. Returns an empty component for null/empty input. */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(legacyToMiniMessage(text));
    }

    /** Legacy (§) representation, e.g. for PlaceholderAPI values that other plugins insert into their texts. */
    public static String toLegacy(Component component) {
        return LEGACY_SECTION.serialize(component);
    }

    /**
     * Converts classic color codes into MiniMessage tags. Like in vanilla, a color code resets
     * the formatting (bold, italic, ...) that was active before it.
     */
    static String legacyToMiniMessage(String text) {
        if (text.indexOf('&') < 0 && text.indexOf('§') < 0) {
            return text;
        }
        Matcher spigotHex = SPIGOT_HEX.matcher(text);
        text = spigotHex.replaceAll(match -> "<reset><#" + match.group(1).replaceAll("[&§]", "") + ">");
        text = HEX.matcher(text).replaceAll("<reset><#$1>");

        Matcher matcher = CODE.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String tag = TAGS.get(code);
            boolean isColor = (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
            String replacement = isColor ? "<reset><" + tag + ">" : "<" + tag + ">";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Normalizes a language code from the config ("DE", " de " -> "de"). */
    public static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) return "en";
        String lang = language.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        // pt_br / pt_BR -> pt (Brazilian Portuguese), zh_cn / cn -> zh (simplified Chinese)
        if (lang.startsWith("pt_")) return "pt";
        if (lang.startsWith("zh_") || lang.equals("cn")) return "zh";
        return lang;
    }
}
