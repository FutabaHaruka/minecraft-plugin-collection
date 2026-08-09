package cn.licry.mintcontrol.bridge;

import cn.licry.mintcontrol.util.Reflect;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class VaultBridge {
    private final JavaPlugin plugin;
    private Object economy;
    private boolean available;

    public VaultBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean initialize() {
        try {
            Class econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider provider = Bukkit.getServicesManager().getRegistration(econClass);
            economy = provider == null ? null : provider.getProvider();
            available = economy != null;
        } catch (Throwable ex) {
            available = false;
        }
        plugin.getLogger().info("Vault economy bridge: " + (available ? "available" : "unavailable"));
        return available;
    }

    public boolean isAvailable() { return available; }

    public double balance(Player player) throws Exception {
        Object result = invokePlayerMethod("getBalance", player);
        return result instanceof Number ? ((Number) result).doubleValue() : 0.0D;
    }

    public boolean has(Player player, double amount) throws Exception {
        if (amount <= 0) return true;
        Method method = Reflect.findCompatibleMethod(economy.getClass(), "has", player, amount);
        if (method != null) {
            Object result = method.invoke(economy, player, amount);
            return result instanceof Boolean && (Boolean) result;
        }
        return balance(player) >= amount;
    }

    public boolean withdraw(Player player, double amount) throws Exception {
        if (amount <= 0) return true;
        Object response = invokePlayerMethod("withdrawPlayer", player, amount);
        return transactionSuccess(response);
    }

    public boolean deposit(Player player, double amount) throws Exception {
        if (amount <= 0) return true;
        Object response = invokePlayerMethod("depositPlayer", player, amount);
        return transactionSuccess(response);
    }

    private Object invokePlayerMethod(String name, Object... args) throws Exception {
        if (!available) throw new IllegalStateException("Vault economy is unavailable");
        Method method = Reflect.findCompatibleMethod(economy.getClass(), name, args);
        if (method == null && args.length > 0 && args[0] instanceof Player) {
            Object[] byName = args.clone();
            byName[0] = ((Player) args[0]).getName();
            method = Reflect.findCompatibleMethod(economy.getClass(), name, byName);
            if (method != null) return method.invoke(economy, byName);
        }
        if (method == null) throw new NoSuchMethodException(economy.getClass().getName() + "#" + name);
        return method.invoke(economy, args);
    }

    private static boolean transactionSuccess(Object response) {
        if (response == null) return false;
        if (response instanceof Boolean) return (Boolean) response;
        try {
            Method method = Reflect.findMethod(response.getClass(), "transactionSuccess", 0);
            if (method != null) {
                Object result = method.invoke(response);
                return result instanceof Boolean && (Boolean) result;
            }
        } catch (Throwable ignored) {
        }
        return true;
    }
}
