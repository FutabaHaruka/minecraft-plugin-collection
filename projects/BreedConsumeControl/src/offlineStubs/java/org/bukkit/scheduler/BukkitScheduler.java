package org.bukkit.scheduler;
import org.bukkit.plugin.Plugin;
public interface BukkitScheduler {
    BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay);
}
