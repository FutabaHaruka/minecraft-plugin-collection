package cn.licry.mintcontrol.service;

import cn.licry.mintcontrol.bridge.PixelmonBridge;
import cn.licry.mintcontrol.config.PluginConfig;
import cn.licry.mintcontrol.cost.CostCheck;
import cn.licry.mintcontrol.cost.CostReceipt;
import cn.licry.mintcontrol.model.ConsumeOn;
import cn.licry.mintcontrol.model.CooldownOn;
import cn.licry.mintcontrol.model.GlobalRule;
import cn.licry.mintcontrol.model.PokemonView;
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
 * Applies one global policy to native Pixelmon mint events. The Forge-side hook
 * filters native Pixelmon mints; this class never identifies configured items,
 * never opens a GUI and never removes/restores inventory items.
 */
public final class NativeMintService {
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
    private final AtomicLong handledNativeMints = new AtomicLong();
    private volatile long lastHandledAt;

    public NativeMintService(JavaPlugin plugin, PluginConfig config, PixelmonBridge pixelmon,
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

    /** Returning true cancels Pixelmon's native event. */
    public boolean intercept(Player player, Object nativePokemon, Object nativeItemStack) {
        handledNativeMints.incrementAndGet();
        lastHandledAt = System.currentTimeMillis();
        String mintName = nativeMintName(nativeItemStack);
        GlobalRule rule = config.getGlobalRule();
        PokemonView pokemon = null;
        CostReceipt receipt = null;
        double roll = -1.0D;
        String oldNature = "Unknown";

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
                return finishNow(player, mintName, rule, null, "NO_POKEMON", roll, oldNature, oldNature, "");
            }
            oldNature = pokemon.getNature();

            String listDenied = validateGlobalLists(pokemon,
                    permissions.bypassPokemonBlacklist(player),
                    permissions.bypassPokemonWhitelist(player));
            if (listDenied != null) {
                player.sendMessage(listDenied);
                return finishNow(player, mintName, rule, pokemon, "LIST_DENIED", roll, oldNature, oldNature, "");
            }

            String categoryDenied = validateGlobalCategory(rule, pokemon);
            if (categoryDenied != null) {
                player.sendMessage(categoryDenied);
                return finishNow(player, mintName, rule, pokemon, "CATEGORY_DENIED", roll, oldNature, oldNature, "");
            }

            if (!permissions.canUseMint(player)) {
                send(player, "no-permission");
                return finishNow(player, mintName, rule, pokemon, "PERMISSION_DENIED", roll, oldNature, oldNature, "");
            }

            long remaining = cooldowns.remainingSeconds(player, rule);
            if (remaining > 0) {
                player.sendMessage(replace(config.message("cooldown"), "{seconds}", String.valueOf(remaining)));
                return finishNow(player, mintName, rule, pokemon, "COOLDOWN", roll, oldNature, oldNature, "remaining=" + remaining);
            }

            boolean bypassCost = player.hasPermission("mintcontrol.bypass.cost");
            CostCheck check = costs.precheck(player, rule, bypassCost);
            if (!check.isSuccess()) {
                String message = config.message(check.getMessageKey());
                message = replace(message, "{amount}", amountFormat.format(check.getAmount()));
                player.sendMessage(message);
                return finishNow(player, mintName, rule, pokemon, "COST_DENIED", roll, oldNature, oldNature, check.getMessageKey());
            }

            roll = random.nextDouble() * 100.0D;
            boolean chanceSuccess = player.hasPermission("mintcontrol.bypass.chance")
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
                audit.log(player, mintName, rule, pokemon, "CHANCE_FAILED", roll,
                        oldNature, oldNature, "currencyCharged=" + costConsumed);
                processing.remove(player.getUniqueId());
                return true;
            }

            if (shouldCharge(rule.getConsumeOn(), true)) {
                receipt = costs.charge(player, rule, bypassCost);
            }
            final CostReceipt capturedReceipt = receipt;
            final PokemonView capturedPokemon = pokemon;
            final String capturedOldNature = oldNature;
            final double capturedRoll = roll;
            final String capturedMintName = mintName;

            String allowed = config.message("intercept-allowed");
            allowed = replace(allowed, "{species}", pokemon.getDisplayName());
            allowed = replace(allowed, "{mint}", mintName);
            player.sendMessage(allowed);

            if (shouldStartCooldown(rule.getCooldownOn(), true,
                    capturedReceipt != null && capturedReceipt.hasCost(), true)
                    && rule.getCooldownOn() != CooldownOn.SUCCESS) {
                cooldowns.start(player, rule);
            }

            plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                @Override public void run() {
                    verifyNativeResult(player, capturedMintName, rule, capturedPokemon, nativePokemon,
                            capturedReceipt, capturedRoll, capturedOldNature);
                }
            }, config.nativeVerificationTicks());
            return false;
        } catch (Throwable ex) {
            if (receipt != null) costs.refund(player, receipt);
            processing.remove(player.getUniqueId());
            send(player, "internal-error");
            plugin.getLogger().severe("Native mint interception failed for " + player.getName() + ": " + ex);
            ex.printStackTrace();
            if (pokemon != null) audit.log(player, mintName, rule, pokemon, "ERROR", roll,
                    oldNature, oldNature, ex.toString());
            return true;
        }
    }

    private void verifyNativeResult(Player player, String mintName, GlobalRule rule,
                                    PokemonView pokemon, Object nativePokemon,
                                    CostReceipt receipt, double roll, String oldNature) {
        try {
            String newNature = pixelmon.currentNature(nativePokemon);
            boolean natureChanged = !same(oldNature, newNature);
            if (!natureChanged) {
                // The native Pixelmon operation did not complete. Currency is refunded;
                // the native mint item itself remains entirely Pixelmon-managed.
                if (receipt != null) costs.refund(player, receipt);
                send(player, "native-no-change");
                audit.log(player, mintName, rule, pokemon, "NATIVE_NO_CHANGE", roll,
                        oldNature, newNature, "currency refunded; native item not managed by plugin");
                return;
            }

            if (rule.getCooldownOn() == CooldownOn.SUCCESS) cooldowns.start(player, rule);
            String message = config.message("native-success");
            message = replace(message, "{species}", pokemon.getDisplayName());
            message = replace(message, "{old}", oldNature);
            message = replace(message, "{new}", newNature);
            player.sendMessage(message);
            audit.log(player, mintName, rule, pokemon, "NATIVE_SUCCESS", roll,
                    oldNature, newNature, "Pixelmon handled native mint consumption");
        } catch (Throwable ex) {
            if (receipt != null) costs.refund(player, receipt);
            send(player, "native-verification-error");
            plugin.getLogger().severe("Unable to verify native mint result for " + player.getName() + ": " + ex);
            audit.log(player, mintName, rule, pokemon, "VERIFY_ERROR", roll,
                    oldNature, oldNature, ex.toString());
        } finally {
            processing.remove(player.getUniqueId());
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

    private boolean finishNow(Player player, String mintName, GlobalRule rule, PokemonView pokemon,
                              String outcome, double roll, String oldNature, String newNature, String detail) {
        if (pokemon != null) audit.log(player, mintName, rule, pokemon, outcome, roll, oldNature, newNature, detail);
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

    private static String nativeMintName(Object stack) {
        Object display = invokeZeroArg(stack, "getDisplayName", "func_82833_r");
        return display == null || String.valueOf(display).trim().isEmpty()
                ? "Pixelmon原生薄荷" : String.valueOf(display);
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
    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b == null ? "" : b);
    }

    public long getHandledNativeMints() { return handledNativeMints.get(); }
    public long getLastHandledAt() { return lastHandledAt; }
    public void notifyHandlerError(Player player) { if (player != null) send(player, "internal-error"); }
    public void notifyNonCancelable(Player player) { if (player != null) send(player, "event-not-cancelable"); }
    public void notifyUnsafeThread(Player player) { if (player != null) send(player, "unsafe-event-thread"); }
    public void clear() { processing.clear(); }
}
