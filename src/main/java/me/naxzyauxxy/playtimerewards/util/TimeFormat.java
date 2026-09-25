package me.naxzyauxxy.playtimerewards.util;

import java.util.Locale;

/**
 * Formats and parses playtime durations.
 *
 * <ul>
 *   <li>{@link #full(long)}      -> "1d 20h 49m 29s"  (scoreboard / PAPI format)</li>
 *   <li>{@link #compact(long)}   -> "1d 12h"          (tier labels: every non-zero unit)</li>
 *   <li>{@link #brief(long,int)} -> "1d 20h"          (largest N units, floored)</li>
 *   <li>{@link #briefCeil(long,int)} -> "3h 10m"      (largest N units, rounded up - for countdowns)</li>
 * </ul>
 * No unit is ever zero-padded ("1d", never "01d").
 */
public final class TimeFormat {

    private static final long[] UNIT_SECONDS = {86_400L, 3_600L, 60L, 1L};

    /** Immutable formatting style, swapped atomically on reload. */
    public record Style(String day, String hour, String minute, String second,
                        String separator, boolean hideLeadingZeroUnits) {
        public static final Style DEFAULT = new Style("d", "h", "m", "s", " ", true);

        String suffix(int unit) {
            return switch (unit) {
                case 0 -> day;
                case 1 -> hour;
                case 2 -> minute;
                default -> second;
            };
        }
    }

    private static volatile Style style = Style.DEFAULT;

    private TimeFormat() {
    }

    public static void configure(Style newStyle) {
        style = newStyle == null ? Style.DEFAULT : newStyle;
    }

    private static long[] split(long totalSeconds) {
        long s = Math.max(0L, totalSeconds);
        long[] values = new long[4];
        for (int i = 0; i < 4; i++) {
            values[i] = s / UNIT_SECONDS[i];
            s %= UNIT_SECONDS[i];
        }
        return values;
    }

    /** "[D]d [H]h [M]m [S]s", e.g. {@code 1d 20h 49m 29s}. */
    public static String full(long totalSeconds) {
        Style st = style;
        long[] v = split(totalSeconds);
        StringBuilder sb = new StringBuilder(24);
        boolean started = !st.hideLeadingZeroUnits();
        for (int i = 0; i < 4; i++) {
            if (!started && v[i] == 0 && i < 3) {
                continue;
            }
            started = true;
            if (!sb.isEmpty()) {
                sb.append(st.separator());
            }
            sb.append(v[i]).append(st.suffix(i));
        }
        return sb.toString();
    }

    /** Every non-zero unit, e.g. {@code 1d 12h}, {@code 30m}. */
    public static String compact(long totalSeconds) {
        Style st = style;
        long[] v = split(totalSeconds);
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 4; i++) {
            if (v[i] == 0) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(st.separator());
            }
            sb.append(v[i]).append(st.suffix(i));
        }
        return sb.isEmpty() ? "0" + st.second() : sb.toString();
    }

    /** The {@code maxUnits} largest units starting at the first non-zero one, floored. */
    public static String brief(long totalSeconds, int maxUnits) {
        Style st = style;
        long[] v = split(totalSeconds);
        int first = firstNonZero(v);
        if (first < 0) {
            return "0" + st.second();
        }
        int last = Math.min(first + Math.max(1, maxUnits) - 1, 3);
        StringBuilder sb = new StringBuilder(12);
        for (int i = first; i <= last; i++) {
            if (v[i] == 0) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(st.separator());
            }
            sb.append(v[i]).append(st.suffix(i));
        }
        return sb.toString();
    }

    /**
     * Like {@link #brief(long, int)} but rounds UP to the smallest displayed unit, so a
     * countdown of 3h 9m 53s reads "3h 10m" and never shows "0m" while time remains.
     */
    public static String briefCeil(long totalSeconds, int maxUnits) {
        if (totalSeconds <= 0) {
            return brief(0, maxUnits);
        }
        long[] v = split(totalSeconds);
        int first = firstNonZero(v);
        int last = Math.min(first + Math.max(1, maxUnits) - 1, 3);
        long unit = UNIT_SECONDS[last];
        long rounded = ((totalSeconds + unit - 1) / unit) * unit;
        return brief(rounded, maxUnits);
    }

    private static int firstNonZero(long[] v) {
        for (int i = 0; i < v.length; i++) {
            if (v[i] != 0) {
                return i;
            }
        }
        return -1;
    }

    /** Component values (not totals) - handy for PAPI {@code _days}, {@code _hours}, ... */
    public static long component(long totalSeconds, int unitIndex) {
        return split(totalSeconds)[unitIndex];
    }

    /**
     * Parses "1d12h", "2d 3h 15m", "90s", "1w". A bare number is read as seconds.
     *
     * @return seconds, or -1 when the input is invalid
     */
    public static long parse(String input) {
        if (input == null) {
            return -1;
        }
        String s = input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.isEmpty()) {
            return -1;
        }
        long total = 0;
        long number = -1;
        boolean anyUnit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                number = (number < 0 ? 0 : number) * 10 + (c - '0');
                if (number > 1_000_000_000L) {
                    return -1;
                }
                continue;
            }
            if (number < 0) {
                return -1;
            }
            long mult = switch (c) {
                case 'w' -> 604_800L;
                case 'd' -> 86_400L;
                case 'h' -> 3_600L;
                case 'm' -> 60L;
                case 's' -> 1L;
                default -> -1L;
            };
            if (mult < 0) {
                return -1;
            }
            total += number * mult;
            number = -1;
            anyUnit = true;
        }
        if (number >= 0) {
            total += number; // trailing bare number = seconds
        } else if (!anyUnit) {
            return -1;
        }
        return total;
    }
}
