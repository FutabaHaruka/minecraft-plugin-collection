package cn.licry.mintcontrol.model;

import java.util.Locale;

public enum PokemonCategory {
    NORMAL,
    LEGENDARY,
    MYTHICAL,
    ULTRA_BEAST;

    /**
     * Tolerant configuration parser. Accepts ULTRA_BEAST, ultra-beast,
     * ultra beast and common aliases.
     */
    public static PokemonCategory parseOrNull(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replace('.', '_');
        while (normalized.contains("__")) normalized = normalized.replace("__", "_");

        if ("ULTRABEAST".equals(normalized) || "UB".equals(normalized)) normalized = "ULTRA_BEAST";
        if ("MYTHIC".equals(normalized) || "MYTH".equals(normalized)) normalized = "MYTHICAL";
        if ("LEGEND".equals(normalized)) normalized = "LEGENDARY";
        if ("ORDINARY".equals(normalized) || "COMMON".equals(normalized)) normalized = "NORMAL";

        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Legacy fallback for internal callers; invalid input becomes NORMAL. */
    public static PokemonCategory parse(String value) {
        PokemonCategory parsed = parseOrNull(value);
        return parsed == null ? NORMAL : parsed;
    }
}
