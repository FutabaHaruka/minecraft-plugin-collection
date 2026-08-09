package cn.licry.mintcontrol.util;

import java.util.Locale;

public final class SpeciesNames {
    private SpeciesNames() { }

    public static String normalize(String value) {
        return value == null ? "" : value.replace("-", "").replace("_", "")
                .replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
