package me.naxzyauxxy.playtimerewards.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** MiniMessage helpers. One shared, thread-safe MiniMessage instance. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static MiniMessage mm() {
        return MM;
    }

    public static Component parse(String input, TagResolver... resolvers) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MM.deserialize(input, resolvers);
    }

    /** Parses text for item names/lore: italic is switched off unless explicitly requested. */
    public static Component item(String input, TagResolver... resolvers) {
        return parse(input, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Escapes user-controlled input (e.g. player names) before it is embedded in MiniMessage. */
    public static String escape(String input) {
        return MM.escapeTags(input == null ? "" : input);
    }
}
