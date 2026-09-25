package me.naxzyauxxy.playtimerewards.reward;

import java.util.Locale;

/**
 * One line of a tier's {@code actions} list, pre-parsed at load time.
 * Syntax: {@code [console] eco give %player% 100}, {@code [player] spawn},
 * {@code [message] <green>Hi}, {@code [broadcast] <gold>...}. No prefix = console.
 */
public record RewardAction(Type type, String value) {

    public enum Type { CONSOLE, PLAYER, MESSAGE, BROADCAST }

    public static RewardAction parse(String raw) {
        String line = raw.trim();
        if (line.startsWith("[")) {
            int end = line.indexOf(']');
            if (end > 0) {
                String tag = line.substring(1, end).trim().toUpperCase(Locale.ROOT);
                String rest = line.substring(end + 1).trim();
                for (Type type : Type.values()) {
                    if (type.name().equals(tag)) {
                        return new RewardAction(type, stripSlash(type, rest));
                    }
                }
            }
        }
        return new RewardAction(Type.CONSOLE, stripSlash(Type.CONSOLE, line));
    }

    private static String stripSlash(Type type, String value) {
        if ((type == Type.CONSOLE || type == Type.PLAYER) && value.startsWith("/")) {
            return value.substring(1);
        }
        return value;
    }
}
