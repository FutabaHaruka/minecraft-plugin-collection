package cn.licry.crowncontrol.model;

import java.util.Locale;

public enum ConsumeOn {
    ATTEMPT,
    SUCCESS,
    FAILURE;

    public static ConsumeOn parseOrNull(String value) {
        if (value == null || value.trim().isEmpty()) return ATTEMPT;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Backward-compatible fallback for non-configuration callers. */
    public static ConsumeOn parse(String value) {
        ConsumeOn parsed = parseOrNull(value);
        return parsed == null ? ATTEMPT : parsed;
    }
}
