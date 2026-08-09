package cn.licry.mintcontrol;

import cn.licry.mintcontrol.bridge.ForgeBridgeManager;
import cn.licry.mintcontrol.bridge.PixelmonBridge;
import cn.licry.mintcontrol.bridge.PlayerPointsBridge;
import cn.licry.mintcontrol.bridge.VaultBridge;
import cn.licry.mintcontrol.command.MintControlCommand;
import cn.licry.mintcontrol.config.PluginConfig;
import cn.licry.mintcontrol.service.AuditService;
import cn.licry.mintcontrol.service.CooldownService;
import cn.licry.mintcontrol.service.CostService;
import cn.licry.mintcontrol.service.NativeMintService;
import cn.licry.mintcontrol.service.PermissionService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MintControlPlugin extends JavaPlugin implements CommandExecutor {
    private PluginConfig pluginConfig;
    private PixelmonBridge pixelmon;
    private VaultBridge vault;
    private PlayerPointsBridge points;
    private PermissionService permissions;
    private MintControlCommand commandDelegate;
    private ForgeBridgeManager forgeBridge;
    private NativeMintService nativeMintService;

    private boolean commandBound;
    private boolean runtimeReady;
    private final List<String> startupIssues = new ArrayList<String>();

    @Override public void onEnable() {
        startupIssues.clear();
        commandBound = false;
        runtimeReady = false;
        try { bindFallbackCommand(); } catch (Throwable ex) { recordIssue("command binding", ex); }
        try {
            bootstrapConfigAndDelegate();
        } catch (Throwable ex) {
            recordIssue("STRICT CONFIG STORAGE", ex);
            throw new IllegalStateException("Refusing to enable outside the exclusive " + "MintControl" + " plugin folder", ex);
        }
        try { initializeRuntime(); } catch (Throwable ex) { recordIssue("native runtime", ex); }

        if (runtimeReady) {
            getLogger().info("MintControl 1.0.0-rc17 enabled; native Pixelmon mints only; config="
                    + getConfigPath() + "; bridge=" + getForgeBridgeMode() + '.');
        } else {
            getLogger().severe("MintControl 1.0.0-rc17 loaded in DIAGNOSTIC MODE. Run /mintc status. Issues: "
                    + joinIssues());
        }
    }

    private void bindFallbackCommand() {
        PluginCommand command = getCommand("mintcontrol");
        if (command == null) {
            commandBound = false;
            startupIssues.add("plugin.yml command 'mintcontrol' was not registered");
            return;
        }
        command.setExecutor(this);
        commandBound = true;
    }

    private void bootstrapConfigAndDelegate() {
        pluginConfig = new PluginConfig(this);
        pluginConfig.reload();
        pixelmon = new PixelmonBridge(this, pluginConfig);
        vault = new VaultBridge(this);
        points = new PlayerPointsBridge(this);
        permissions = new PermissionService(pluginConfig);
        commandDelegate = new MintControlCommand(this, pluginConfig, pixelmon, permissions);
        PluginCommand command = getCommand("mintcontrol");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(commandDelegate);
            commandBound = true;
        }
    }

    private synchronized void initializeRuntime() {
        runtimeReady = false;
        if (pluginConfig == null || pixelmon == null || permissions == null) {
            startupIssues.add("command/config layer is unavailable");
            return;
        }
        boolean pixelmonAvailable = pixelmon.initialize();
        safeInitializeEconomyBridges();
        if (!pixelmonAvailable) {
            startupIssues.add("Pixelmon ItemInteractionEvent unavailable: " + pixelmon.getLastError());
            return;
        }

        CostService costs = new CostService(this, vault, points);
        CooldownService cooldowns = new CooldownService();
        AuditService audit = new AuditService(this, pluginConfig);
        nativeMintService = new NativeMintService(this, pluginConfig, pixelmon, costs, cooldowns, audit, permissions);

        forgeBridge = new ForgeBridgeManager(this);
        if (!forgeBridge.register(nativeMintService)) {
            startupIssues.add("Pixelmon listener registration failed: " + forgeBridge.getLastError());
            nativeMintService.clear(); nativeMintService = null; return;
        }
        runtimeReady = true;
    }

    private void safeInitializeEconomyBridges() {
        try { vault.initialize(); } catch (Throwable ex) { recordIssue("Vault bridge", ex); }
        try { points.initialize(); } catch (Throwable ex) { recordIssue("PlayerPoints bridge", ex); }
        if (pluginConfig.usesMoneyCosts() && !isVaultAvailable()) {
            startupIssues.add("Vault economy unavailable while money cost is enabled");
        }
        if (pluginConfig.usesPointCosts() && !isPointsAvailable()) {
            startupIssues.add("PlayerPoints unavailable while point cost is enabled");
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (commandDelegate != null) return commandDelegate.onCommand(sender, command, label, args);
            sender.sendMessage(color("&8[&d薄荷控制&8] &eMintControl rc17 正在诊断模式运行。"));
            sender.sendMessage(color("&7当前问题：&f" + joinIssues()));
            return true;
        } catch (Throwable ex) {
            recordIssue("command execution", ex);
            sender.sendMessage(color("&8[&d薄荷控制&8] &c命令异常，已记录到控制台。"));
            return true;
        }
    }

    @Override public void onDisable() {
        if (forgeBridge != null) forgeBridge.unregister();
        if (nativeMintService != null) nativeMintService.clear();
        try {
            PluginCommand command = getCommand("mintcontrol");
            if (command != null) {
                command.setExecutor(null);
                command.setTabCompleter(null);
            }
        } catch (Throwable ignored) { }

        // Break the complete config/service/command object graph on hot unload.
        // This prevents a stale command or event listener from retaining old configuration.
        nativeMintService = null;
        forgeBridge = null;
        commandDelegate = null;
        permissions = null;
        points = null;
        vault = null;
        pixelmon = null;
        pluginConfig = null;
        commandBound = false;
        runtimeReady = false;
    }

    public synchronized void reloadRuntime() {
        if (pluginConfig == null) throw new IllegalStateException("PluginConfig is unavailable");
        pluginConfig.reload();
        safeInitializeEconomyBridges();
        if (!runtimeReady) {
            if (forgeBridge != null) forgeBridge.unregister();
            initializeRuntime();
        }
    }

    private void recordIssue(String stage, Throwable ex) {
        String message = stage + ": " + ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
        startupIssues.add(message); getLogger().severe(message); ex.printStackTrace();
    }

    private String joinIssues() {
        if (startupIssues.isEmpty()) return "none";
        StringBuilder out = new StringBuilder();
        for (String issue : startupIssues) { if (out.length() > 0) out.append(" | "); out.append(issue); }
        return out.toString();
    }

    private static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    public boolean isCommandBound() { return commandBound; }
    public boolean isRuntimeReady() { return runtimeReady; }
    public boolean isForgeRegistered() { return forgeBridge != null && forgeBridge.isRegistered(); }
    public String getForgeBridgeMode() { return forgeBridge == null ? "NONE" : forgeBridge.getMode(); }
    public String getForgeBridgeError() { return forgeBridge == null ? "" : forgeBridge.getLastError(); }
    public String getForgeBridgeOwner() { return forgeBridge == null ? "none" : forgeBridge.getOwnerId(); }
    public String getForgeBridgeBus() { return forgeBridge == null ? "none" : forgeBridge.getBusName(); }
    public long getRawItemEvents() { return forgeBridge == null ? 0L : forgeBridge.getRawItemEvents(); }
    public long getNativeMintEvents() { return forgeBridge == null ? 0L : forgeBridge.getNativeMintEvents(); }
    public long getCallbacksInvoked() { return forgeBridge == null ? 0L : forgeBridge.getCallbacksInvoked(); }
    public long getCallbackErrors() { return forgeBridge == null ? 0L : forgeBridge.getCallbackErrors(); }
    public String getLastEventDebug() { return forgeBridge == null ? "never" : forgeBridge.getLastEventDebug(); }
    public long getHandledNativeMints() { return nativeMintService == null ? 0L : nativeMintService.getHandledNativeMints(); }
    public long getLastNativeEventAt() { return nativeMintService == null ? 0L : nativeMintService.getLastHandledAt(); }
    public boolean isVaultAvailable() { return vault != null && vault.isAvailable(); }
    public boolean isPointsAvailable() { return points != null && points.isAvailable(); }
    public String getConfigPath() { return pluginConfig == null ? "unavailable" : pluginConfig.getConfigPath(); }
    public List<String> getStartupIssues() { return Collections.unmodifiableList(new ArrayList<String>(startupIssues)); }
}
