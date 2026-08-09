package com.wangzhu.immortalersdelightfix;

import net.minecraftforge.fml.common.Mod;

import java.io.PrintStream;

/**
 * Server-side Forge compatibility patch for Immortalers Delight.
 *
 * <p>The affected Immortalers Delight builds print debug messages from
 * EnchantalCoolerRecipe#matches every time the recipe manager checks the
 * machine inventory. This patch filters only those known debug messages and
 * leaves recipe matching untouched.</p>
 */
@Mod(ImmortalersDelightRecipeFix.MOD_ID)
public final class ImmortalersDelightRecipeFix {
    public static final String MOD_ID = "immortalers_delight_recipe_fix";
    public static final String VERSION = "1.1.0";

    public ImmortalersDelightRecipeFix() {
        PrintStream originalOut = System.out;

        if (Log4jSpamFilter.install()) {
            originalOut.println("[ImmortalersDelightRecipeFix] v" + VERSION
                    + " installed Log4j recipe-spam filter.");
            return;
        }

        StdoutFallbackFilter.install();
        originalOut.println("[ImmortalersDelightRecipeFix] v" + VERSION
                + " installed stdout fallback filter.");
    }
}
