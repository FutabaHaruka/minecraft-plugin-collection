package cn.licry.mintcontrol.service;

import cn.licry.mintcontrol.model.GlobalRule;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One shared cooldown for every mint because the policy is global. */
public final class CooldownService {
    private final Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();

    public long remainingSeconds(Player player, GlobalRule rule) {
        if (rule.getCooldownSeconds() <= 0 || player.hasPermission("mintcontrol.bypass.cooldown")) return 0;
        UUID key = player.getUniqueId();
        Long until = cooldowns.get(key);
        if (until == null) return 0;
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldowns.remove(key);
            return 0;
        }
        return (remaining + 999L) / 1000L;
    }

    public void start(Player player, GlobalRule rule) {
        if (rule.getCooldownSeconds() <= 0 || player.hasPermission("mintcontrol.bypass.cooldown")) return;
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + rule.getCooldownSeconds() * 1000L);
    }
}
