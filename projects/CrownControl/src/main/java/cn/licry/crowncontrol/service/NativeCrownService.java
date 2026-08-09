package cn.licry.crowncontrol.service;

import cn.licry.crowncontrol.bridge.PixelmonBridge;
import cn.licry.crowncontrol.config.PluginConfig;
import cn.licry.crowncontrol.cost.CostCheck;
import cn.licry.crowncontrol.cost.CostReceipt;
import cn.licry.crowncontrol.model.ConsumeOn;
import cn.licry.crowncontrol.model.CooldownOn;
import cn.licry.crowncontrol.model.GlobalRule;
import cn.licry.crowncontrol.model.PokemonView;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Applies the same policy pipeline as the native mint interceptor to native Pixelmon Gold/Silver
 * Bottle Cap events. The plugin never writes IVs, opens Pixelmon selection GUIs,
 * or removes/restores crown items; Pixelmon remains responsible for the native
 * hyper-training operation and crown consumption.
 */
public final class NativeCrownService {
    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final PixelmonBridge pixelmon;
    private final CostService costs;
    private final CooldownService cooldowns;
    private final AuditService audit;
    private final PermissionService permissions;
    private final Random random = new Random();
    private final DecimalFormat amountFormat = new DecimalFormat("0.##");
    private final Set<UUID> processing = Collections.synchronizedSet(new HashSet<UUID>());
    private final AtomicLong handledNativeCrowns = new AtomicLong();
    private volatile long lastHandledAt;

    public NativeCrownService(JavaPlugin plugin, PluginConfig config, PixelmonBridge pixelmon,
                              CostService costs, CooldownService cooldowns, AuditService audit,
                              PermissionService permissions) {
        this.plugin = plugin;
        this.config = config;
        this.pixelmon = pixelmon;
        this.costs = costs;
        this.cooldowns = cooldowns;
        this.audit = audit;
        this.permissions = permissions;
    }

    /** Returning true cancels Pixelmon's native BottleCapEvent. */
    public boolean intercept(Player player, Object nativePokemon, Object nativeItemStack) {
        handledNativeCrowns.incrementAndGet();
        lastHandledAt = System.currentTimeMillis();
        String crownName = nativeCrownName(nativeItemStack);
        GlobalRule rule = config.getGlobalRule();
        PokemonView pokemon = null;
        CostReceipt receipt = null;
        double roll = -1.0D;
        boolean[] oldTraining = null;
        String oldTrainingText = "unavailable";

        if (player == null || nativePokemon == null) return true;
        if (rule == null || !rule.isEnabled() || !rule.isValid()) {
            send(player, "invalid-config");
            return true;
        }
        if (!processing.add(player.getUniqueId())) {
            send(player, "processing");
            return true;
        }

        try {
            pokemon = pixelmon.fromNativePokemon(nativePokemon);
            if (pokemon == null) {
                send(player, "no-pokemon");
                return finishNow(player, crownName, rule, null, "NO_POKEMON", roll,
                        oldTrainingText, oldTrainingText, "");
            }

            oldTraining = pixelmon.currentHyperTraining(nativePokemon);
            oldTrainingText = PixelmonBridge.formatHyperTraining(oldTraining);
            if (oldTraining == null || oldTraining.length == 0) {
                send(player, "verification-unavailable");
                return finishNow(player, crownName, rule, pokemon, "VERIFICATION_UNAVAILABLE", roll,
                        oldTrainingText, oldTrainingText,
                        "Pixelmon hyper-training state reader unavailable");
            }

            String listDenied = validateGlobalLists(pokemon,
                    permissions.bypassPokemonBlacklist(player),
                    permissions.bypassPokemonWhitelist(player));
            if (listDenied != null) {
                player.sendMessage(listDenied);
                return finishNow(player, crownName, rule, pokemon, "LIST_DENIED", roll,
                        oldTrainingText, oldTrainingText, "");
            }

            String categoryDenied = validateGlobalCategory(rule, pokemon);
            if (categoryDenied != null) {
                player.sendMessage(categoryDenied);
                return finishNow(player, crownName, rule, pokemon, "CATEGORY_DENIED", roll,
                        oldTrainingText, oldTrainingText, "");
            }

            if (!permissions.canUseCrown(player)) {
                send(player, "no-permission");
                return finishNow(player, crownName, rule, pokemon, "PERMISSION_DENIED", roll,
                        oldTrainingText, oldTrainingText, "");
            }

            long remaining = cooldowns.remainingSeconds(player, rule);
            if (remaining > 0) {
                player.sendMessage(replace(config.message("cooldown"), "{seconds}", String.valueOf(remaining)));
                return finishNow(player, crownName, rule, pokemon, "COOLDOWN", roll,
                        oldTrainingText, oldTrainingText, "remaining=" + remaining);
            }

            boolean bypassCost = player.hasPermission("crowncontrol.bypass.cost");
            CostCheck check = costs.precheck(player, rule, bypassCost);
            if (!check.isSuccess()) {
                String message = config.message(check.getMessageKey());
                message = replace(message, "{amount}", amountFormat.format(check.getAmount()));
                player.sendMessage(message);
                return finishNow(player, crownName, rule, pokemon, "COST_DENIED", roll,
                        oldTrainingText, oldTrainingText, check.getMessageKey());
            }

            roll = random.nextDouble() * 100.0D;
            boolean chanceSuccess = player.hasPermission("crowncontrol.bypass.chance")
                    || roll < rule.getSuccessRate();

            if (!chanceSuccess) {
                if (shouldCharge(rule.getConsumeOn(), false)) {
                    receipt = costs.charge(player, rule, bypassCost);
                }
                boolean costConsumed = receipt != null && receipt.hasCost();
                if (shouldStartCooldown(rule.getCooldownOn(), false, costConsumed, true)) {
                    cooldowns.start(player, rule);
                }
                send(player, "intercept-chance-failed");
                audit.log(player, crownName, rule, pokemon, "CHANCE_FAILED", roll,
                        oldTrainingText, oldTrainingText, "currencyCharged=" + costConsumed);
                processing.remove(player.getUniqueId());
                return true;
            }

            if (shouldCharge(rule.getConsumeOn(), true)) {
                receipt = costs.charge(player, rule, bypassCost);
            }
            final CostReceipt capturedReceipt = receipt;
            final PokemonView capturedPokemon = pokemon;
            final boolean[] capturedOldTraining = oldTraining.clone();
            final String capturedOldTrainingText = oldTrainingText;
            final double capturedRoll = roll;
            final String capturedCrownName = crownName;

            String allowed = config.message("intercept-allowed");
            allowed = replace(allowed, "{species}", pokemon.getDisplayName());
            allowed = replace(allowed, "{crown}", crownName);
            player.sendMessage(allowed);

            if (shouldStartCooldown(rule.getCooldownOn(), true,
                    capturedReceipt != null && capturedReceipt.hasCost(), true)
                    && rule.getCooldownOn() != CooldownOn.SUCCESS) {
                cooldowns.start(player, rule);
            }

            scheduleNativeVerification(player, capturedCrownName, rule, capturedPokemon,
                    nativePokemon, capturedReceipt, capturedRoll, capturedOldTraining,
                    capturedOldTrainingText, 0);
            return false;
        } catch (Throwable ex) {
            if (receipt != null) costs.refund(player, receipt);
            processing.remove(player.getUniqueId());
            send(player, "internal-error");
            plugin.getLogger().severe("Native crown interception failed for " + player.getName() + ": " + ex);
            ex.printStackTrace();
            if (pokemon != null) audit.log(player, crownName, rule, pokemon, "ERROR", roll,
                    oldTrainingText, oldTrainingText, ex.toString());
            return true;
        }
    }

    private void scheduleNativeVerification(final Player player, final String crownName,
                                            final GlobalRule rule, final PokemonView pokemon,
                                            final Object nativePokemon, final CostReceipt receipt,
                                            final double roll, final boolean[] oldTraining,
                                            final String oldTrainingText, final int elapsedTicks) {
        final int interval = config.nativeVerificationTicks();
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                verifyNativeResult(player, crownName, rule, pokemon, nativePokemon, receipt,
                        roll, oldTraining, oldTrainingText, elapsedTicks + interval);
            }
        }, interval);
    }

    private void verifyNativeResult(Player player, String crownName, GlobalRule rule,
                                    PokemonView pokemon, Object nativePokemon,
                                    CostReceipt receipt, double roll, boolean[] oldTraining,
                                    String oldTrainingText, int elapsedTicks) {
        try {
            boolean[] newTraining = pixelmon.currentHyperTraining(nativePokemon);
            String newTrainingText = PixelmonBridge.formatHyperTraining(newTraining);
            if (hasNewHyperTraining(oldTraining, newTraining)) {
                if (rule.getCooldownOn() == CooldownOn.SUCCESS) cooldowns.start(player, rule);
                String changedStats = changedStats(oldTraining, newTraining);
                String message = config.message("native-success");
                message = replace(message, "{species}", pokemon.getDisplayName());
                message = replace(message, "{crown}", crownName);
                message = replace(message, "{stats}", changedStats);
                message = replace(message, "{old}", oldTrainingText);
                message = replace(message, "{new}", newTrainingText);
                player.sendMessage(message);
                audit.log(player, crownName, rule, pokemon, "NATIVE_SUCCESS", roll,
                        oldTrainingText, newTrainingText,
                        "hyperTrained=" + changedStats + "; Pixelmon handled native crown consumption");
                processing.remove(player.getUniqueId());
                return;
            }

            if (elapsedTicks < config.nativeVerificationTimeoutTicks()) {
                scheduleNativeVerification(player, crownName, rule, pokemon, nativePokemon,
                        receipt, roll, oldTraining, oldTrainingText, elapsedTicks);
                return;
            }

            if (receipt != null) costs.refund(player, receipt);
            send(player, "native-no-change");
            audit.log(player, crownName, rule, pokemon, "NATIVE_NO_CHANGE", roll,
                    oldTrainingText, newTrainingText,
                    "verification timeout=" + elapsedTicks
                            + " ticks; currency refunded; native item not managed by plugin");
        } catch (Throwable ex) {
            if (receipt != null) costs.refund(player, receipt);
            send(player, "native-verification-error");
            plugin.getLogger().severe("Unable to verify native crown result for " + player.getName() + ": " + ex);
            audit.log(player, crownName, rule, pokemon, "VERIFY_ERROR", roll,
                    oldTrainingText, oldTrainingText, ex.toString());
            processing.remove(player.getUniqueId());
        } finally {
            if (elapsedTicks >= config.nativeVerificationTimeoutTicks()) {
                processing.remove(player.getUniqueId());
            }
        }
    }

    private String validateGlobalLists(PokemonView pokemon, boolean bypassBlacklist, boolean bypassWhitelist) {
        if (!bypassBlacklist && config.isGlobalBlacklisted(pokemon)) {
            return replace(config.message("global-blacklisted"), "{species}", pokemon.getDisplayName());
        }
        if (!bypassWhitelist && !config.getGlobalWhitelist().isEmpty()
                && !config.isGlobalWhitelisted(pokemon)) {
            return replace(config.message("not-global-whitelisted"), "{species}", pokemon.getDisplayName());
        }
        return null;
    }

    private String validateGlobalCategory(GlobalRule rule, PokemonView pokemon) {
        if (pokemon.isEgg() && !config.allowEggs()) return config.message("egg-denied");
        if (!rule.getAllowedCategories().contains(pokemon.getCategory())) {
            String message = replace(config.message("category-denied"), "{category}", pokemon.getCategory().name());
            return replace(message, "{species}", pokemon.getDisplayName());
        }
        return null;
    }

    private boolean finishNow(Player player, String crownName, GlobalRule rule, PokemonView pokemon,
                              String outcome, double roll, String oldTraining,
                              String newTraining, String detail) {
        if (pokemon != null) audit.log(player, crownName, rule, pokemon, outcome, roll,
                oldTraining, newTraining, detail);
        processing.remove(player.getUniqueId());
        return true;
    }

    private static boolean shouldCharge(ConsumeOn mode, boolean success) {
        return mode == ConsumeOn.ATTEMPT || (mode == ConsumeOn.SUCCESS && success)
                || (mode == ConsumeOn.FAILURE && !success);
    }

    private static boolean shouldStartCooldown(CooldownOn mode, boolean success,
                                               boolean costConsumed, boolean attempted) {
        if (mode == null || mode == CooldownOn.NEVER) return false;
        if (mode == CooldownOn.SUCCESS) return success;
        if (mode == CooldownOn.COST) return costConsumed;
        if (mode == CooldownOn.ATTEMPT) return attempted;
        return mode == CooldownOn.ALWAYS;
    }

    static boolean hasNewHyperTraining(boolean[] before, boolean[] after) {
        if (before == null || after == null) return false;
        int length = Math.min(before.length, after.length);
        for (int i = 0; i < length; i++) if (!before[i] && after[i]) return true;
        return false;
    }

    static String changedStats(boolean[] before, boolean[] after) {
        if (before == null || after == null) return "unknown";
        String[] names = {"HP", "攻击", "防御", "特攻", "特防", "速度"};
        StringBuilder out = new StringBuilder();
        int length = Math.min(before.length, after.length);
        for (int i = 0; i < length; i++) {
            if (!before[i] && after[i]) {
                if (out.length() > 0) out.append(',');
                out.append(i < names.length ? names[i] : String.valueOf(i));
            }
        }
        return out.length() == 0 ? "无" : out.toString();
    }

    private static String nativeCrownName(Object stack) {
        Object display = invokeZeroArg(stack, "getDisplayName", "func_82833_r");
        return display == null || String.valueOf(display).trim().isEmpty()
                ? "Pixelmon原生金银皇冠" : String.valueOf(display);
    }

    private static Object invokeZeroArg(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            Class<?> current = target.getClass();
            while (current != null) {
                try {
                    Method method = current.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                } catch (Throwable ignored) {
                    break;
                }
            }
        }
        return null;
    }

    private void send(Player player, String key) { player.sendMessage(config.message(key)); }
    private static String replace(String source, String token, String value) {
        return source == null ? "" : source.replace(token, value == null ? "" : value);
    }

    public long getHandledNativeCrowns() { return handledNativeCrowns.get(); }
    public long getLastHandledAt() { return lastHandledAt; }
    public void notifyHandlerError(Player player) { if (player != null) send(player, "internal-error"); }
    public void notifyNonCancelable(Player player) { if (player != null) send(player, "event-not-cancelable"); }
    public void notifyUnsafeThread(Player player) { if (player != null) send(player, "unsafe-event-thread"); }
    public void clear() { processing.clear(); }
}
