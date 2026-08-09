package cn.licry.crowncontrol.model;

import java.util.Locale;

/** Defines when a native crown attempt starts cooldown. */
public enum CooldownOn {
    SUCCESS,
    ATTEMPT,
    COST,
    ALWAYS,
    NEVER;

    public static CooldownOn parseOrNull(String value) {
        if (value == null || value.trim().isEmpty()) return SUCCESS;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Backward-compatible fallback for non-configuration callers. */
    public static CooldownOn parse(String value) {
        CooldownOn parsed = parseOrNull(value);
        return parsed == null ? SUCCESS : parsed;
    }
}
