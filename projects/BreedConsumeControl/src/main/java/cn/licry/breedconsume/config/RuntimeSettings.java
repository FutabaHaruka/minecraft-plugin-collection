package cn.licry.breedconsume.config;

import java.util.Locale;
import java.util.logging.Logger;

/** Immutable, validated runtime configuration snapshot. */
public final class RuntimeSettings {
    public final boolean synthesisUpgradeMode;
    /** IV result algorithm: parent-range or exact-target-v. */
    public final String ivGenerationMode;
    /** Strict equal-tier chain: 1V+1V=2V ... 5V+5V=6V. */
    public final boolean strictSynthesisMode;
    public final boolean allowZeroVBreeding;
    public final int zeroVResultV;

    public final boolean allowEqualParentV;
    public final boolean allowAdjacentParentV;
    public final int maximumParentVDifference;
    public final String resultVBasis;

    /** Retained for source/binary compatibility with 1.4.x integrations. */
    public final boolean requireEqualParentV;
    public final String unequalParentBasis;

    public final int minimumUpgradeParentV;
    public final int maximumUpgradeParentV;
    public final int upgradeStep;
    public final int maximumResultV;
    public final boolean allowMaximumVBreeding;
    public final boolean exactResultV;

    /** Legacy/admin-selectable nature policy. Item-lock mode can take precedence. */
    public final String natureInheritanceMode;
    public final boolean natureLockRequireEverstone;

    /** Everstone + Power Item synthesis controls. */
    public final boolean itemLockModeEnabled;
    public final boolean itemLockApplyToZeroTier;
    public final boolean requireExactlyOneEverstone;
    /** Canonical v3 rule: one or two Power Items may be required/allowed. */
    public final boolean requireAtLeastOnePowerItem;
    public final boolean allowTwoPowerItems;
    /** Legacy compatibility view; true only when two-Power-Item mode is disabled. */
    public final boolean requireExactlyOnePowerItem;
    public final boolean requireDifferentLockParents;
    public final boolean forceNatureFromEverstone;
    public final boolean forceIvFromPowerItem;
    public final boolean requirePowerItemPerfectIv;
    public final String everstoneParentRole;
    public final String powerItemParentRole;

    public final boolean showEggIvs;
    public final boolean showEggIvsOnCreate;
    public final boolean showEggIvsOnCollect;
    public final boolean showSynthesisSuccess;
    public final boolean shinyOnlyWithShiny;
    public final boolean consumeParentsOnEggCreated;
    public final boolean consumeZeroVParents;
    public final int parentConsumeDelayTicks;
    public final boolean failClosedWhenParentConsumeFails;

    private RuntimeSettings(boolean synthesisUpgradeMode,
                            String ivGenerationMode,
                            boolean strictSynthesisMode,
                            boolean allowZeroVBreeding,
                            int zeroVResultV,
                            boolean allowEqualParentV,
                            boolean allowAdjacentParentV,
                            int maximumParentVDifference,
                            String resultVBasis,
                            int minimumUpgradeParentV,
                            int maximumUpgradeParentV,
                            int upgradeStep,
                            int maximumResultV,
                            boolean allowMaximumVBreeding,
                            boolean exactResultV,
                            String natureInheritanceMode,
                            boolean natureLockRequireEverstone,
                            boolean itemLockModeEnabled,
                            boolean itemLockApplyToZeroTier,
                            boolean requireExactlyOneEverstone,
                            boolean requireAtLeastOnePowerItem,
                            boolean allowTwoPowerItems,
                            boolean requireDifferentLockParents,
                            boolean forceNatureFromEverstone,
                            boolean forceIvFromPowerItem,
                            boolean requirePowerItemPerfectIv,
                            String everstoneParentRole,
                            String powerItemParentRole,
                            boolean showEggIvs,
                            boolean showEggIvsOnCreate,
                            boolean showEggIvsOnCollect,
                            boolean showSynthesisSuccess,
                            boolean shinyOnlyWithShiny,
                            boolean consumeParentsOnEggCreated,
                            boolean consumeZeroVParents,
                            int parentConsumeDelayTicks,
                            boolean failClosedWhenParentConsumeFails) {
        this.synthesisUpgradeMode = synthesisUpgradeMode;
        this.ivGenerationMode = ivGenerationMode;
        this.strictSynthesisMode = strictSynthesisMode;
        this.allowZeroVBreeding = allowZeroVBreeding;
        this.zeroVResultV = zeroVResultV;
        this.allowEqualParentV = allowEqualParentV;
        this.allowAdjacentParentV = allowAdjacentParentV;
        this.maximumParentVDifference = maximumParentVDifference;
        this.resultVBasis = resultVBasis;
        this.requireEqualParentV = !allowAdjacentParentV || maximumParentVDifference == 0;
        this.unequalParentBasis = resultVBasis;
        this.minimumUpgradeParentV = minimumUpgradeParentV;
        this.maximumUpgradeParentV = maximumUpgradeParentV;
        this.upgradeStep = upgradeStep;
        this.maximumResultV = maximumResultV;
        this.allowMaximumVBreeding = allowMaximumVBreeding;
        this.exactResultV = exactResultV;
        this.natureInheritanceMode = natureInheritanceMode;
        this.natureLockRequireEverstone = natureLockRequireEverstone;
        this.itemLockModeEnabled = itemLockModeEnabled;
        this.itemLockApplyToZeroTier = itemLockApplyToZeroTier;
        this.requireExactlyOneEverstone = requireExactlyOneEverstone;
        this.requireAtLeastOnePowerItem = requireAtLeastOnePowerItem;
        this.allowTwoPowerItems = allowTwoPowerItems;
        this.requireExactlyOnePowerItem = requireAtLeastOnePowerItem && !allowTwoPowerItems;
        this.requireDifferentLockParents = requireDifferentLockParents;
        this.forceNatureFromEverstone = forceNatureFromEverstone;
        this.forceIvFromPowerItem = forceIvFromPowerItem;
        this.requirePowerItemPerfectIv = requirePowerItemPerfectIv;
        this.everstoneParentRole = everstoneParentRole;
        this.powerItemParentRole = powerItemParentRole;
        this.showEggIvs = showEggIvs;
        this.showEggIvsOnCreate = showEggIvsOnCreate;
        this.showEggIvsOnCollect = showEggIvsOnCollect;
        this.showSynthesisSuccess = showSynthesisSuccess;
        this.shinyOnlyWithShiny = shinyOnlyWithShiny;
        this.consumeParentsOnEggCreated = consumeParentsOnEggCreated;
        this.consumeZeroVParents = consumeZeroVParents;
        this.parentConsumeDelayTicks = parentConsumeDelayTicks;
        this.failClosedWhenParentConsumeFails = failClosedWhenParentConsumeFails;
    }

    public static RuntimeSettings load(PluginConfig config, Logger logger) {
        boolean synthesis = config.getBoolean("rules.enabled", true);
        String ivGenerationMode = normalizedPolicy(config.getString("rules.iv-generation-mode", "parent-range"));
        if (!"parent-range".equals(ivGenerationMode) && !"exact-target-v".equals(ivGenerationMode)) {
            warn(logger, "rules.iv-generation-mode must be parent-range or exact-target-v; using parent-range");
            ivGenerationMode = "parent-range";
        }
        boolean strictSynthesis = config.getBoolean("rules.strict-equal-upgrade-mode", true);
        boolean allowZero = config.getBoolean("rules.zero-v.enabled", true);
        int maxResult = bounded(config, logger, "rules.upgrade.maximum-result-v", 6, 0, 6);
        int zeroResult = bounded(config, logger, "rules.zero-v.result-v", 0, 0, 6);
        if (zeroResult > maxResult) {
            warn(logger, "rules.zero-v.result-v=" + zeroResult
                    + " exceeds rules.upgrade.maximum-result-v=" + maxResult + "; using " + maxResult);
            zeroResult = maxResult;
        }

        boolean allowEqual = config.getBoolean("rules.flexible-mode.allow-equal-parent-v", true);
        boolean allowAdjacent = config.getBoolean("rules.flexible-mode.allow-adjacent-parent-v", false);
        int maxDifference = bounded(config, logger,
                "rules.flexible-mode.maximum-parent-v-difference", 0, 0, 6);
        if (maxDifference == 0 && allowAdjacent) {
            warn(logger, "flexible-mode allows adjacent parents but maximum-parent-v-difference=0; adjacent pairs remain disabled");
        }
        String resultBasis = normalizedPolicy(config.getString("rules.flexible-mode.result-v-basis", "higher"));
        if (!"lower".equals(resultBasis) && !"higher".equals(resultBasis)) {
            warn(logger, "rules.flexible-mode.result-v-basis must be lower or higher; using higher");
            resultBasis = "higher";
        }

        int minParent = bounded(config, logger, "rules.upgrade.minimum-parent-v", 1, 0, 6);
        int maxParent = bounded(config, logger, "rules.upgrade.maximum-parent-v", 5, 0, 6);
        if (minParent > maxParent) {
            warn(logger, "rules.upgrade.minimum-parent-v exceeds maximum-parent-v; using maximum=" + minParent);
            maxParent = minParent;
        }
        int step = bounded(config, logger, "rules.flexible-mode.result-v-offset", 1, 0, 6);
        boolean allowMaximum = config.getBoolean("rules.upgrade.allow-maximum-v-breeding", false);
        // exact-target-v is exact by definition. The duplicate force-exact-result-v switch was removed in schema v2.
        boolean exact = "exact-target-v".equals(ivGenerationMode);

        boolean itemLock = config.getBoolean("rules.item-lock.enabled", true);
        boolean itemLockZero = config.getBoolean("rules.item-lock.apply-to-zero-zero", false);
        boolean exactEverstone = config.getBoolean("rules.item-lock.require-exactly-one-everstone", true);
        boolean requireAtLeastPower = config.getBoolean("rules.item-lock.require-at-least-one-power-item", true);
        boolean allowTwoPower = config.getBoolean("rules.item-lock.allow-two-power-items", true);
        boolean differentParents = config.getBoolean("rules.item-lock.require-different-parents", true);
        boolean requirePowerPerfect = config.getBoolean("rules.item-lock.require-power-item-perfect-iv", true);
        String everstoneRole = normalizedPolicy(config.getString("rules.item-lock.everstone-parent-role", "any"));
        if (!isParentRole(everstoneRole)) {
            warn(logger, "rules.item-lock.everstone-parent-role must be any, lower-v or higher-v; using any");
            everstoneRole = "any";
        }
        String powerRole = normalizedPolicy(config.getString("rules.item-lock.power-item-parent-role", "any"));
        if (!isParentRole(powerRole)) {
            warn(logger, "rules.item-lock.power-item-parent-role must be any, lower-v or higher-v; using any");
            powerRole = "any";
        }
        if (!requireAtLeastPower && allowTwoPower) {
            warn(logger, "item-lock allows two Power Items but does not require any; pairs without a Power Item will use no IV locks");
        }

        // Schema v2 has one source of truth: enabling item-lock always locks the Everstone nature
        // and the Power Item IV. The old duplicate force-* and nature-* switches are migrated away.
        String natureMode = itemLock ? "everstone" : "native";
        boolean natureRequiresEverstone = itemLock && exactEverstone;
        boolean forceNature = itemLock;
        boolean forcePowerIv = itemLock;

        boolean showCreate = config.getBoolean("rules.display.egg-ivs-on-create", true);
        boolean showCollect = config.getBoolean("rules.display.egg-ivs-on-collect", true);
        boolean show = showCreate || showCollect;
        boolean showSuccess = config.getBoolean("rules.display.synthesis-success", true);
        boolean shiny = config.getBoolean("rules.shiny-only-with-shiny", true);

        boolean consume = config.getBoolean("rules.parent-consume.enabled", true);
        boolean consumeZero = config.getBoolean("rules.parent-consume.consume-zero-v", true);
        int delay = bounded(config, logger, "rules.parent-consume.delay-ticks", 1, 1, 1200);
        boolean failClosed = config.getBoolean("rules.parent-consume.fail-closed", true);

        RuntimeSettings result = new RuntimeSettings(synthesis, ivGenerationMode, strictSynthesis, allowZero, zeroResult,
                allowEqual, allowAdjacent, maxDifference, resultBasis,
                minParent, maxParent, step, maxResult, allowMaximum, exact,
                natureMode, natureRequiresEverstone,
                itemLock, itemLockZero, exactEverstone, requireAtLeastPower, allowTwoPower, differentParents,
                forceNature, forcePowerIv, requirePowerPerfect, everstoneRole, powerRole,
                show, showCreate, showCollect, showSuccess,
                shiny, consume, consumeZero, delay, failClosed);
        logger.info("Validated synthesis settings: enabled=" + synthesis
                + ", ivGeneration=" + ivGenerationMode
                + ", strictEqualUpgrade=" + strictSynthesis
                + ", zero=" + allowZero + "->" + zeroResult + "V"
                + ", flexible[equal=" + allowEqual + ",adjacent=" + allowAdjacent
                + ",maxGap=" + maxDifference + ",basis=" + resultBasis + ",offset=" + step + "]"
                + ", parentRange=" + minParent + "-" + maxParent + "V"
                + ", maxResult=" + maxResult + "V"
                + ", itemLock=" + itemLock + "[everstoneWhenSingle=" + exactEverstone
                + ",requirePower=" + requireAtLeastPower + ",allowTwoPower=" + allowTwoPower
                + ",different=" + differentParents
                + ",perfectPowerIv=" + requirePowerPerfect
                + ",roles=" + everstoneRole + "/" + powerRole + "]"
                + ", consume=" + consume + ", consumeZero=" + consumeZero
                + ", delay=" + delay + "t");
        return result;
    }

    private static boolean isParentRole(String value) {
        return "any".equals(value) || "lower-v".equals(value) || "higher-v".equals(value);
    }

    private static boolean isNatureMode(String value) {
        return "native".equals(value) || "everstone".equals(value)
                || "lower-v".equals(value) || "higher-v".equals(value)
                || "first-parent".equals(value) || "second-parent".equals(value);
    }

    private static int bounded(PluginConfig config, Logger logger, String path,
                               int def, int min, int max) {
        int value = config.getInt(path, def);
        if (value < min || value > max) {
            int fixed = value < min ? min : max;
            warn(logger, path + "=" + value + " is outside " + min + ".." + max
                    + "; using " + fixed);
            return fixed;
        }
        return value;
    }

    private static String normalizedPolicy(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void warn(Logger logger, String message) {
        if (logger != null) logger.warning("BreedConsumeControl config: " + message);
    }
}
