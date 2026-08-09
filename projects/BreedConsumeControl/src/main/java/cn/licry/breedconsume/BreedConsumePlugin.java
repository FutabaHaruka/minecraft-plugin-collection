package cn.licry.breedconsume;

import cn.licry.breedconsume.config.PluginConfig;
import cn.licry.breedconsume.config.RuntimeSettings;
import cn.licry.breedconsume.listener.CatServerForgeEventListener;
import cn.licry.breedconsume.service.BreedConsumeService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class BreedConsumePlugin extends JavaPlugin implements CommandExecutor {
    private PluginConfig config;
    private volatile RuntimeSettings settings;
    private BreedConsumeService service;
    private CatServerForgeEventListener listener;
    private volatile String startupError = "none";
    private volatile boolean handlerListVerified;

    @Override
    public void onEnable() {
        try {
            config = new PluginConfig(this);
            reloadValidatedConfig();
        } catch (Throwable error) {
            startupError = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
            getLogger().severe("BreedConsumeControl failed to initialize dedicated config storage: " + startupError);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getCommand("breedconsume") != null) {
            getCommand("breedconsume").setExecutor(this);
        }

        try {
            verifyRuntimeApi();
            service = new BreedConsumeService(this);
            listener = new CatServerForgeEventListener(this, service);
            getServer().getPluginManager().registerEvents(listener, this);
            handlerListVerified = verifyForgeEventHandlerList();
            if (!handlerListVerified) {
                throw new IllegalStateException("ForgeEvent HandlerList does not contain this plugin listener");
            }
            getLogger().info("BreedConsumeControl 1.8.5 enabled using CatServer Bukkit ForgeEvent bridge; config="
                    + config.getConfigPath());
            getLogger().info("Parent-range IV generation enabled by default: each child IV is uniformly randomized between the two parents' matching IV values, inclusive. Existing pairing, item-lock, nature and parent-consume rules remain configurable.");
        } catch (Throwable error) {
            startupError = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
            getLogger().severe("BreedConsumeControl failed to initialize: " + startupError);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void reloadValidatedConfig() {
        config.reload();
        settings = RuntimeSettings.load(config, getLogger());
    }

    private RuntimeSettings settings() {
        RuntimeSettings current = settings;
        if (current == null) throw new IllegalStateException("Runtime settings have not been loaded");
        return current;
    }

    private void verifyRuntimeApi() throws ClassNotFoundException, NoSuchMethodException {
        ClassLoader loader = getClass().getClassLoader();
        Class<?> forgeWrapper = Class.forName("catserver.api.bukkit.event.ForgeEvent", false, loader);
        forgeWrapper.getMethod("getForgeEvent");
        Class.forName("com.pixelmonmod.pixelmon.api.events.BreedEvent$AddPokemon", false, loader);
        Class.forName("com.pixelmonmod.pixelmon.api.events.BreedEvent$MakeEgg", false, loader);
        Class.forName("com.pixelmonmod.pixelmon.api.events.BreedEvent$CollectEgg", false, loader);
        Class.forName("com.pixelmonmod.pixelmon.api.events.EggHatchEvent$Post", false, loader);
    }

    private boolean verifyForgeEventHandlerList() {
        try {
            Class<?> forgeWrapper = Class.forName("catserver.api.bukkit.event.ForgeEvent", false, getClass().getClassLoader());
            Object handlerList = forgeWrapper.getMethod("getHandlerList").invoke(null);
            Object registered = handlerList.getClass().getMethod("getRegisteredListeners").invoke(handlerList);
            if (registered == null || !registered.getClass().isArray()) return false;
            int length = java.lang.reflect.Array.getLength(registered);
            for (int i = 0; i < length; i++) {
                Object entry = java.lang.reflect.Array.get(registered, i);
                Object owner = entry.getClass().getMethod("getPlugin").invoke(entry);
                if (owner == this) return true;
            }
            return false;
        } catch (Throwable error) {
            getLogger().severe("Unable to verify ForgeEvent HandlerList registration: "
                    + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
            return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("breedconsume.admin")) {
            sender.sendMessage(color("&8[&d牧场控制&8] &c你没有权限。"));
            return true;
        }
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            try {
                reloadValidatedConfig();
                sender.sendMessage(color("&8[&d牧场控制&8] &a配置已重载并通过逻辑校验。"));
            } catch (Throwable error) {
                sender.sendMessage(color("&8[&d牧场控制&8] &c配置重载失败：&f"
                        + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage())));
                getLogger().severe("Config reload failed: " + error.getClass().getSimpleName()
                        + ": " + String.valueOf(error.getMessage()));
            }
            return true;
        }

        RuntimeSettings active = settings();
        if (args.length > 0 && ("items".equalsIgnoreCase(args[0]) || "道具".equals(args[0]))) {
            sender.sendMessage(color("&8[&d个体合成&8] &f道具说明"));
            sender.sendMessage(color("&e不变之石 &7= 继承携带者的原始性格；薄荷修正不会遗传。"));
            sender.sendMessage(color("&b力量负重 &7= 锁定HP IV；&b力量护腕 &7= 锁定攻击 IV；&b力量腰带 &7= 锁定防御 IV。"));
            sender.sendMessage(color("&b力量镜 &7= 锁定特攻 IV；&b力量束带 &7= 锁定特防 IV；&b力量护踝 &7= 锁定速度 IV。"));
            sender.sendMessage(color("&7双方都携带力量道具时，两个不同属性会同时锁定；同属性狗圈只能写入一个子代属性，数值相同则合并，数值不同则随机选一方。父母区间模式下其余各项仍在对应区间随机。"));
            return true;
        }
        sender.sendMessage(color("&8[&d个体合成&8] &f运行状态 &7(v1.8.5)"));
        sender.sendMessage(color("&7事件通道：&fCATSERVER_BUKKIT_FORGE_EVENT"));
        sender.sendMessage(color("&7配置模型：&fv" + config.getSchemaVersion()
                + " &7重复键：&f" + config.getDuplicateKeyCount()
                + " &7文件：&f" + config.getConfigPath()));
        sender.sendMessage(color("&7IV生成模式：&f" + active.ivGenerationMode
                + ("parent-range".equals(active.ivGenerationMode)
                ? " &7（父母对应IV最小值~最大值，含边界，均匀随机）"
                : " &7（旧版精确目标V）")));
        if (active.strictSynthesisMode) {
            sender.sendMessage(color("&7严格链：&f1+1=2、2+2=3、3+3=4、4+4=5、5+5=6 &7| 0+0→&f"
                    + active.zeroVResultV + "V"));
            sender.sendMessage(color("&7跨级配对：&c拒绝 &7| 精确目标V：&a强制 &7| 旧相邻V/偏移键：&8忽略"));
        } else {
            sender.sendMessage(color("&7灵活规则：&f0V=" + active.allowZeroVBreeding + "→" + active.zeroVResultV
                    + "V &7同V：&f" + active.allowEqualParentV
                    + " &7相邻V：&f" + active.allowAdjacentParentV
                    + " &7最大差值：&f" + active.maximumParentVDifference
                    + " &7结果基准：&f" + active.resultVBasis));
            sender.sendMessage(color("&7升级范围：&f" + active.minimumUpgradeParentV + "-" + active.maximumUpgradeParentV
                    + "V &7结果偏移：&f" + active.upgradeStep + " &7上限：&f" + active.maximumResultV + "V"));
        }
        sender.sendMessage(color("&7性格模式：&f" + active.natureInheritanceMode
                + " &7需要不变石：&f" + active.natureLockRequireEverstone));
        sender.sendMessage(color("&7道具锁定：&f" + active.itemLockModeEnabled
                + " &7单狗圈需1不变石：&f" + active.requireExactlyOneEverstone
                + " &7至少1狗圈：&f" + active.requireAtLeastOnePowerItem
                + " &7双狗圈：&f" + active.allowTwoPowerItems
                + " &7力量项需31：&f" + active.requirePowerItemPerfectIv
                + " &7角色：&f" + active.everstoneParentRole + "/" + active.powerItemParentRole));
        sender.sendMessage(color("&7父母消耗：&f" + active.consumeParentsOnEggCreated
                + " &70V消耗：&f" + active.consumeZeroVParents
                + " &7延迟：&f" + active.parentConsumeDelayTicks + " tick"));
        sender.sendMessage(color("&7监听器对象：&f" + (listener == null ? "未创建" : "已创建")
                + " &7HandlerList验证：&f" + (handlerListVerified ? "通过" : "失败")
                + " &7启动错误：&f" + startupError));
        if (listener != null) {
            sender.sendMessage(color("&7ForgeEvent总数：&f" + listener.getTotalForgeEvents()
                    + " &7其他Forge事件：&f" + listener.getOtherForgeEvents()));
            sender.sendMessage(color("&7AddPokemon：&f" + listener.getAddEvents()
                    + " &7MakeEgg：&f" + listener.getMakeEvents()
                    + " &7CollectEgg：&f" + listener.getCollectEvents()));
            sender.sendMessage(color("&7HatchPre：&f" + listener.getHatchPreEvents()
                    + " &7HatchPost：&f" + listener.getHatchPostEvents()));
            sender.sendMessage(color("&7回调异常：&f" + listener.getCallbackErrors()
                    + " &7最近底层事件：&f" + listener.getLastForgeEvent()));
        }
        if (service != null) {
            sender.sendMessage(color("&7拒绝混合闪光：&f" + service.getRejectedPairs()
                    + " &7拒绝V级配对：&f" + service.getRejectedUpgradePairs()
                    + " &7合成蛋：&f" + service.getUpgradedEggs()
                    + " &70V蛋：&f" + service.getZeroTierEggs()));
            sender.sendMessage(color("&7出蛋后已消费：&f" + service.getConsumedAtMake()
                    + " &7清理旧追踪标签：&f" + service.getLegacyTagsCleaned()));
            sender.sendMessage(color("&7已删除父母：&f" + service.getRemovedParents()
                    + " &7失败事务：&f" + service.getFailedTransactions()));
            sender.sendMessage(color("&7最近动作：&f" + service.getLastAction()));
        }
        return true;
    }

    public boolean isSynthesisUpgradeEnabled() { return settings().synthesisUpgradeMode; }
    public String getIvGenerationMode() { return settings().ivGenerationMode; }
    public boolean isParentRangeIvMode() { return "parent-range".equals(settings().ivGenerationMode); }
    public boolean isStrictSynthesisMode() { return settings().strictSynthesisMode; }
    public boolean isAllowZeroVBreeding() { return settings().allowZeroVBreeding; }
    public int getZeroVResultV() { return settings().zeroVResultV; }
    public boolean isAllowEqualParentV() { return settings().allowEqualParentV; }
    public boolean isAllowAdjacentParentV() { return settings().allowAdjacentParentV; }
    public int getMaximumParentVDifference() { return settings().maximumParentVDifference; }
    public String getResultVBasis() { return settings().resultVBasis; }
    public String getNatureInheritanceMode() { return settings().natureInheritanceMode; }
    public boolean isNatureLockRequireEverstone() { return settings().natureLockRequireEverstone; }
    public boolean isItemLockModeEnabled() { return settings().itemLockModeEnabled; }
    public boolean isItemLockApplyToZeroTier() { return settings().itemLockApplyToZeroTier; }
    public boolean isRequireExactlyOneEverstone() { return settings().requireExactlyOneEverstone; }
    public boolean isRequireExactlyOnePowerItem() { return settings().requireExactlyOnePowerItem; }
    public boolean isRequireAtLeastOnePowerItem() { return settings().requireAtLeastOnePowerItem; }
    public boolean isAllowTwoPowerItems() { return settings().allowTwoPowerItems; }
    public boolean isRequireDifferentLockParents() { return settings().requireDifferentLockParents; }
    public boolean isForceNatureFromEverstone() { return settings().forceNatureFromEverstone; }
    public boolean isForceIvFromPowerItem() { return settings().forceIvFromPowerItem; }
    public boolean isRequirePowerItemPerfectIv() { return settings().requirePowerItemPerfectIv; }
    public String getEverstoneParentRole() { return settings().everstoneParentRole; }
    public String getPowerItemParentRole() { return settings().powerItemParentRole; }

    /** Backward-compatible 1.4.x accessors. */
    public boolean isRequireEqualParentV() { return settings().requireEqualParentV; }
    public String getUnequalParentVBasis() { return settings().unequalParentBasis; }
    public int getMinimumUpgradeParentV() { return settings().minimumUpgradeParentV; }
    public int getMaximumUpgradeParentV() { return settings().maximumUpgradeParentV; }
    public int getResultVOffset() { return settings().upgradeStep; }
    /** Backward-compatible alias. Since 1.6.1 this value is the configured result V offset. */
    public int getUpgradeStep() { return getResultVOffset(); }
    public int getMaximumResultV() { return settings().maximumResultV; }
    public boolean isAllowMaximumVBreeding() { return settings().allowMaximumVBreeding; }
    public boolean isForceExactResultV() { return settings().exactResultV; }
    public boolean isShowEggIvsEnabled() { return settings().showEggIvs; }
    public boolean isShowEggIvsOnCreateEnabled() { return settings().showEggIvsOnCreate; }
    public boolean isShowEggIvsOnCollectEnabled() { return settings().showEggIvsOnCollect; }
    public boolean isShowSynthesisSuccessEnabled() { return settings().showSynthesisSuccess; }
    public boolean isShinyPairingEnabled() { return settings().shinyOnlyWithShiny; }
    public boolean isConsumeParentsEnabled() { return settings().consumeParentsOnEggCreated; }
    public boolean isConsumeZeroVParentsEnabled() { return settings().consumeZeroVParents; }
    public int getParentConsumeDelayTicks() { return settings().parentConsumeDelayTicks; }
    public boolean isFailClosedWhenConsumeFails() { return settings().failClosedWhenParentConsumeFails; }

    /** Backward-compatible alias for 1.2.x integrations. */
    public boolean isFailClosedWhenTaggingFails() { return isFailClosedWhenConsumeFails(); }

    public String getSynthesisMinimumMessage() {
        if (isStrictSynthesisMode()) return config.getString("messages.strict-synthesis-minimum",
                "&8[&d个体合成&8] &c严格升级只允许1V到5V的同V父母。当前：&f{first}V &7/ &f{second}V");
        return config.getString("messages.synthesis-minimum",
                "&8[&d个体合成&8] &c父母V数不在允许升级范围。当前：&f{first}V &7/ &f{second}V &7允许：&f{min}-{parent-max}V");
    }

    public String getSynthesisZeroDisabledMessage() {
        if (isStrictSynthesisMode()) return config.getString("messages.strict-synthesis-zero-disabled",
                "&8[&d个体合成&8] &c严格模式禁止该0V配对。当前：&f{first}V &7/ &f{second}V");
        return config.getString("messages.synthesis-zero-disabled",
                "&8[&d个体合成&8] &c当前配置禁止0V父母繁殖。当前：&f{first}V &7/ &f{second}V");
    }

    public String getSynthesisMismatchMessage() {
        if (isStrictSynthesisMode()) return config.getString("messages.strict-synthesis-mismatch",
                "&8[&d个体合成&8] &c严格模式要求两只父母V数完全相同。当前：&f{first}V &7/ &f{second}V &7示例：&f1V+1V=2V");
        return config.getString("messages.synthesis-mismatch",
                "&8[&d个体合成&8] &c父母V数不符合合成规则。当前：&f{first}V &7/ &f{second}V &7允许最大差值：&f{gap}");
    }

    public String getItemLockRequiredMessage() {
        if (isStrictSynthesisMode()) return config.getString("messages.strict-item-lock-required",
                "&8[&d个体合成&8] &c需使用一种或两种力量道具；单狗圈时另一只父母需携带不变之石，双狗圈时两个锁定都会生效。");
        return config.getString("messages.item-lock-required",
                "&8[&d个体合成&8] &c需使用一种或两种力量道具；单狗圈时另一只父母需携带不变之石，双狗圈时两个对应IV都会锁定。");
    }

    public String getItemLockRoleMismatchMessage() {
        if (isStrictSynthesisMode()) return config.getString("messages.strict-item-lock-role-mismatch",
                "&8[&d个体合成&8] &c道具携带者不符合当前 everstone-parent-role 或 power-item-parent-role 配置。");
        return config.getString("messages.item-lock-role-mismatch",
                "&8[&d个体合成&8] &c道具携带者不符合当前 everstone-parent-role 或 power-item-parent-role 配置。");
    }

    public String getPowerItemIvNotPerfectMessage() {
        if (isStrictSynthesisMode()) return config.getString("messages.strict-power-item-iv-not-perfect",
                "&8[&d个体合成&8] &c{power-item}只锁{power-stat}，该项必须为31；当前为{locked-iv}。");
        return config.getString("messages.power-item-iv-not-perfect",
                "&8[&d个体合成&8] &c力量道具锁定的{power-stat}不是31（当前{locked-iv}），请换成对应满个体父母。");
    }

    public String getPowerItemLockedMessage() {
        if (isStrictSynthesisMode()) return config.getString("messages.strict-power-item-locked",
                "&8[&d个体合成&8] &7锁定：&e不变之石继承原始性格 {nature} &8| &b{power-item}固定{power-stat} IV={locked-iv}");
        return config.getString("messages.power-item-locked",
                "&8[&d个体合成&8] &7道具锁定：&e不变之石锁性格 {nature} &8| &b{power-item}锁{power-stat} {locked-iv}");
    }

    public String getNatureLockedMessage() {
        if (isStrictSynthesisMode()) return config.getString("messages.strict-nature-locked",
                "&8[&d个体合成&8] &7蛋的原始性格已锁定：&e{nature} &7（来源：{nature-source}；薄荷修正不继承）");
        return config.getString("messages.nature-locked",
                "&8[&d个体合成&8] &7蛋性格已锁定为：&e{nature} &7（来源：{nature-source}）");
    }

    public String getSynthesisMaximumMessage() {
        if (isStrictSynthesisMode()) return config.getString("messages.strict-synthesis-maximum",
                "&8[&d个体合成&8] &e已经达到{max}V上限，不能继续严格升级。当前：&f{first}V + {second}V");
        return config.getString("messages.synthesis-maximum",
                "&8[&d个体合成&8] &e当前V数已达到配置上限，不能继续升级。当前：&f{first}V &7/ &f{second}V &7上限：&f{max}V");
    }

    public String getSynthesisFailedMessage() {
        if (isParentRangeIvMode()) return config.getString("messages.parent-range-synthesis-failed",
                "&8[&d个体合成&8] &c无法安全生成父母区间个体蛋，本次合成已取消。");
        if (isStrictSynthesisMode()) return config.getString("messages.strict-synthesis-failed",
                "&8[&d个体合成&8] &c严格IV或原始性格校验失败，本次合成已取消。");
        return config.getString("messages.synthesis-failed",
                "&8[&d个体合成&8] &c无法安全生成目标个体蛋，本次合成已取消。");
    }

    public String getSynthesisSuccessMessage() {
        if (isParentRangeIvMode()) return config.getString("messages.parent-range-synthesis-success",
                "&8[&d个体合成&8] &a区间孵蛋完成：每项IV均在父母对应最小值到最大值之间随机，结果为 {v}V。");
        if (isStrictSynthesisMode()) return config.getString("messages.strict-synthesis-success",
                "&8[&d个体合成&8] &a严格合成完成：&e{first}V + {second}V → {v}V蛋 &7（目标固定为{target}V）");
        return config.getString("messages.synthesis-success",
                "&8[&d个体合成&8] &a合成完成：&e{v}V蛋 &7（目标 {target}V）");
    }

    public String getEggIvsMessage() {
        if (isParentRangeIvMode()) return config.getString("messages.parent-range-egg-ivs",
                "&8[&d个体合成&8] &7区间随机蛋IV：{ivs} &8| &7原始性格：&e{nature}");
        if (isStrictSynthesisMode()) return config.getString("messages.strict-egg-ivs",
                "&8[&d个体合成&8] &7严格蛋IV：{ivs} &8| &7原始性格：&e{nature}");
        return config.getString("messages.egg-ivs",
                "&8[&d个体合成&8] &7蛋个体：{ivs}");
    }

    public String getMismatchMessage() {
        return config.getString("messages.shiny-mismatch", "&c闪光与普通宝可梦不能互相繁殖。");
    }

    public String getParentsConsumedMessage() {
        return config.getString("messages.parents-consumed-on-egg-created",
                "&8[&d牧场控制&8] &e蛋已生成，两只父母宝可梦已永久消耗。");
    }

    public String getConsumeFailedMessage() {
        return config.getString("messages.parent-consume-failed",
                "&8[&d牧场控制&8] &c无法安全消耗两只父母，本次出蛋已取消。");
    }

    /** Backward-compatible alias for 1.2.x integrations. */
    public String getTrackingFailedMessage() { return getConsumeFailedMessage(); }

    public String getParentsMissingMessage() {
        return config.getString("messages.parents-missing",
                "&8[&d牧场控制&8] &c未同时找到两只父母，本次未删除任何宝可梦。");
    }

    public void sendMessage(org.bukkit.entity.Player player, String message) {
        if (player != null && message != null && !message.isEmpty()) {
            player.sendMessage(color(message));
        }
    }

    private static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
