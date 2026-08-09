package cn.licry.mintcontrol.bridge;

import cn.licry.mintcontrol.util.Reflect;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PlayerPointsBridge {
    private final JavaPlugin plugin;
    private Object api;
    private boolean available;

    public PlayerPointsBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        try {
            Plugin points = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (points == null || !points.isEnabled()) {
                available = false;
            } else {
                Method getApi = Reflect.findMethod(points.getClass(), "getAPI", 0);
                api = getApi == null ? null : getApi.invoke(points);
                available = api != null;
            }
        } catch (Throwable ex) {
            available = false;
        }
        plugin.getLogger().info("PlayerPoints bridge: " + (available ? "available" : "unavailable"));
        return available;
    }

    public boolean isAvailable() { return available; }

    public int balance(Player player) throws Exception {
        Object value = invokeUuid("look", player.getUniqueId());
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public boolean take(Player player, int amount) throws Exception {
        if (amount <= 0) return true;
        Object result = invokeUuidAmount("take", player.getUniqueId(), amount);
        return !(result instanceof Boolean) || (Boolean) result;
    }

    public boolean give(Player player, int amount) throws Exception {
        if (amount <= 0) return true;
        Object result = invokeUuidAmount("give", player.getUniqueId(), amount);
        return !(result instanceof Boolean) || (Boolean) result;
    }

    private Object invokeUuid(String name, UUID uuid) throws Exception {
        if (!available) throw new IllegalStateException("PlayerPoints is unavailable");
        Method method = Reflect.findCompatibleMethod(api.getClass(), name, uuid);
        if (method == null) throw new NoSuchMethodException(api.getClass().getName() + "#" + name);
        return method.invoke(api, uuid);
    }

    private Object invokeUuidAmount(String name, UUID uuid, int amount) throws Exception {
        if (!available) throw new IllegalStateException("PlayerPoints is unavailable");
        Method method = Reflect.findCompatibleMethod(api.getClass(), name, uuid, amount);
        if (method == null) throw new NoSuchMethodException(api.getClass().getName() + "#" + name);
        return method.invoke(api, uuid, amount);
    }
}
