package cn.licry.fakecreativeguard;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeCreativeGuard extends JavaPlugin implements Listener {
    public static final String BYPASS_PERMISSION = "fakecreativeguard.bypass";
    private static final String KICK_MESSAGE = "§c检测到异常创造模式/数据包，连接已中断。";

    private final Set<UUID> processing = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Object logLock = new Object();
    private File securityLog;
    private boolean protocolHookEnabled;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("无法创建插件数据目录: " + getDataFolder().getAbsolutePath());
        }
        securityLog = new File(getDataFolder(), "security.log");

        getServer().getPluginManager().registerEvents(this, this);

        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            try {
                ProtocolHook.enable(this);
                protocolHookEnabled = true;
                getLogger().info("ProtocolLib 数据包防护已启用：拦截 SET_CREATIVE_SLOT。 ");
            } catch (Throwable t) {
                protocolHookEnabled = false;
                getLogger().warning("ProtocolLib 存在，但数据包钩子启用失败；Bukkit 状态/容器防护仍然有效。");
                getLogger().warning(t.getClass().getName() + ": " + String.valueOf(t.getMessage()));
            }
        } else {
            getLogger().warning("未检测到 ProtocolLib：无法在最前端拦截 SET_CREATIVE_SLOT，Bukkit 状态/容器防护仍然有效。建议安装与 1.12.2 兼容的 ProtocolLib 4.x。");
        }

        getLogger().info("FakeCreativeGuard 已启用（纯事件驱动，无每 Tick 全服扫描）。不会读取或修改 PlotSquared/Essentials/LuckPerms 的任何配置。 ");
    }

    @Override
    public void onDisable() {
        if (protocolHookEnabled) {
            try {
                ProtocolHook.disable(this);
            } catch (Throwable ignored) {
            }
        }
        processing.clear();
    }

    public boolean isAllowedCreative(Player player) {
        return player != null && player.hasPermission(BYPASS_PERMISSION);
    }

    // 前后各拦一次，避免其他插件在事件链中把取消状态重新放开。
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onGameModeChangeEarly(PlayerGameModeChangeEvent event) {
        guardGameModeChange(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGameModeChangeLate(PlayerGameModeChangeEvent event) {
        guardGameModeChange(event);
    }

    private void guardGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.CREATIVE && !isAllowedCreative(event.getPlayer())) {
            event.setCancelled(true);
            handleViolation(event.getPlayer(), "GAMEMODE_CHANGE", "attempted unauthorized switch to CREATIVE", true);
        }
    }

    // 第一层：尽可能早于地皮保护插件取消未授权创造玩家的容器交互。
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onContainerInteractEarly(PlayerInteractEvent event) {
        guardContainerInteract(event);
    }

    // 第二层：在大多数插件处理后再次强制取消，避免其他插件把事件重新放行。
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onContainerInteractLate(PlayerInteractEvent event) {
        guardContainerInteract(event);
    }

    private void guardContainerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE || isAllowedCreative(player)) {
            return;
        }

        // 未授权创造状态本身就是异常：不允许它继续触发任何方块交互。
        // 这样即使是 CatServer/Forge 的 Mod 容器没有实现 Bukkit InventoryHolder，
        // 也不会因为类型识别失败而漏过。
        event.setCancelled(true);

        Block block = event.getClickedBlock();
        if (block == null) {
            handleViolation(player, "ILLEGAL_CREATIVE_INTERACT", "unauthorized CREATIVE interaction", true);
            return;
        }

        String kind = "block";
        try {
            if (block.getState() instanceof InventoryHolder) {
                kind = "container";
            }
        } catch (Throwable ignored) {
            kind = "modded/unknown block";
        }
        handleViolation(player, "ILLEGAL_CREATIVE_INTERACT", "unauthorized CREATIVE tried " + kind + " at " + formatBlock(block), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && !isAllowedCreative(player)) {
            event.setCancelled(true);
            handleViolation(player, "INVENTORY_OPEN", "unauthorized CREATIVE opened inventory type=" + event.getInventory().getType(), true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 防止玩家带着异常创造状态跨重连进入。
        if (player.getGameMode() == GameMode.CREATIVE && !isAllowedCreative(player)) {
            handleViolation(player, "JOIN_GAMEMODE_GUARD", "joined while unauthorized CREATIVE", true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        processing.remove(event.getPlayer().getUniqueId());
    }

    /**
     * 由 ProtocolHook 在收到创造背包包时调用。必须从主线程处理 Bukkit 状态与踢出。
     */
    public void onIllegalCreativePacket(final Player player, final String packetName) {
        if (player == null) {
            return;
        }
        getServer().getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                handleViolation(player, "PACKET_" + packetName, "illegal creative inventory packet while mode=" + player.getGameMode(), true);
            }
        });
    }

    public void handleViolation(Player player, String type, String detail, boolean kick) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) {
            return;
        }

        try {
            // 先把创造状态拉回生存，再踢，避免踢出/保存过程中留下创造状态。
            if (player.getGameMode() == GameMode.CREATIVE && !isAllowedCreative(player)) {
                try {
                    player.setGameMode(GameMode.SURVIVAL);
                } catch (Throwable ignored) {
                }
            }

            String line = buildLogLine(player, type, detail);
            writeSecurityLog(line);
            getLogger().warning("[SECURITY] " + line);

            if (kick && player.isOnline()) {
                try {
                    player.kickPlayer(KICK_MESSAGE);
                } catch (Throwable ignored) {
                }
            }
        } finally {
            // 延迟几 tick 再释放，避免同一次攻击触发多个事件导致刷屏/重复 kick。
            getServer().getScheduler().runTaskLater(this, new Runnable() {
                @Override
                public void run() {
                    processing.remove(uuid);
                }
            }, 20L);
        }
    }

    private String buildLogLine(Player player, String type, String detail) {
        Location loc = player.getLocation();
        String ip = "unknown";
        try {
            if (player.getAddress() != null && player.getAddress().getAddress() != null) {
                ip = player.getAddress().getAddress().getHostAddress();
            }
        } catch (Throwable ignored) {
        }

        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date())
                + " | type=" + safe(type)
                + " | player=" + safe(player.getName())
                + " | uuid=" + player.getUniqueId()
                + " | ip=" + safe(ip)
                + " | world=" + safe(player.getWorld().getName())
                + " | xyz=" + round(loc.getX()) + "," + round(loc.getY()) + "," + round(loc.getZ())
                + " | gamemode=" + player.getGameMode()
                + " | detail=" + safe(detail);
    }

    private void writeSecurityLog(String line) {
        synchronized (logLock) {
            PrintWriter out = null;
            try {
                out = new PrintWriter(new FileWriter(securityLog, true));
                out.println(line);
            } catch (IOException e) {
                getLogger().warning("写入 security.log 失败: " + e.getMessage());
            } finally {
                if (out != null) {
                    out.close();
                }
            }
        }
    }

    private static String formatBlock(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + "," + block.getY() + "," + block.getZ() + " type=" + block.getType();
    }

    private static String safe(Object value) {
        if (value == null) return "null";
        return String.valueOf(value).replace('\n', ' ').replace('\r', ' ').replace('|', '/');
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
