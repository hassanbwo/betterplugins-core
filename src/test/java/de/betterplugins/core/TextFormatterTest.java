package de.betterplugins.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextFormatterTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void normalizesLanguages() {
        assertEquals("en", TextFormatter.normalizeLanguage(null));
        assertEquals("en", TextFormatter.normalizeLanguage("  "));
        assertEquals("de", TextFormatter.normalizeLanguage(" DE "));
        assertEquals("pt", TextFormatter.normalizeLanguage("pt_BR"));
        assertEquals("pt", TextFormatter.normalizeLanguage("pt-br"));
        assertEquals("zh", TextFormatter.normalizeLanguage("zh_CN"));
        assertEquals("zh", TextFormatter.normalizeLanguage("cn"));
    }

    @Test
    void emptyTextIsEmptyComponent() {
        assertEquals(Component.empty(), TextFormatter.parse(null));
        assertEquals(Component.empty(), TextFormatter.parse(""));
    }

    @Test
    void legacyCodesBecomeMiniMessage() {
        assertEquals("<reset><red>Hi", TextFormatter.legacyToMiniMessage("&cHi"));
        assertEquals("<reset><#ff8800>x", TextFormatter.legacyToMiniMessage("&#ff8800x"));
        assertEquals("<reset><#ff8800>x", TextFormatter.legacyToMiniMessage("&x&f&f&8&8&0&0x"));
        assertEquals("<bold>b", TextFormatter.legacyToMiniMessage("&lb"));
        assertEquals("plain", TextFormatter.legacyToMiniMessage("plain"));
    }

    @Test
    void parsesMiniMessageAndLegacyMixed() {
        Component component = TextFormatter.parse("<gold>Gold</gold> &cred");
        assertEquals("Gold red", plain(component));
        assertTrue(TextFormatter.toLegacy(component).contains("§6Gold"));
    }

    @Test
    void colorIsApplied() {
        Component component = TextFormatter.parse("&#ff8800x");
        assertEquals("x", plain(component));
        assertEquals(TextColor.color(0xff8800), findColor(component));
        assertEquals(NamedTextColor.RED, findColor(TextFormatter.parse("<red>r")));
    }

    private static TextColor findColor(Component component) {
        if (component.color() != null) return component.color();
        for (Component child : component.children()) {
            TextColor color = findColor(child);
            if (color != null) return color;
        }
        return null;
    }
}
