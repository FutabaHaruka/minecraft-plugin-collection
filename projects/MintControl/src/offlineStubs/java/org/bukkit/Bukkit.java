package org.bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import java.util.UUID;
public final class Bukkit {
    public static boolean isPrimaryThread() { return true; }
    public static Player getPlayer(UUID uuid) { return null; }
    public static PluginManager getPluginManager() { return null; }
    public static ServicesManager getServicesManager() { return null; }
}
