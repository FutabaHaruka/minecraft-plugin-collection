package cn.licry.crowncontrol.command;

import cn.licry.crowncontrol.CrownControlPlugin;
import cn.licry.crowncontrol.bridge.PixelmonBridge;
import cn.licry.crowncontrol.config.PluginConfig;
import cn.licry.crowncontrol.model.GlobalRule;
import cn.licry.crowncontrol.model.PokemonView;
import cn.licry.crowncontrol.service.PermissionService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CrownControlCommand implements CommandExecutor, TabCompleter {
    private final CrownControlPlugin plugin;
    private final PluginConfig config;
    private final PixelmonBridge pixelmon;
    private final PermissionService permissions;

    public CrownControlCommand(CrownControlPlugin plugin, PluginConfig config,
                              PixelmonBridge pixelmon, PermissionService permissions) {
        this.plugin = plugin;
        this.config = config;
        this.pixelmon = pixelmon;
        this.permissions = permissions;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help")) {
            if (!permissions.hasPlayerCommand(sender, "crowncontrol.command.help")
                    && !sender.hasPermission("crowncontrol.admin")) return noPermission(sender);
            help(sender); return true;
        }
        if (sub.equals("reload")) {
            if (!sender.hasPermission("crowncontrol.admin.reload") && !sender.hasPermission("crowncontrol.admin")) return noPermission(sender);
            plugin.reloadRuntime();
            sender.sendMessage(color("&aCrownControl 已重载。配置：&f" + config.getConfigPath()));
            return true;
        }
        if (sub.equals("status") || sub.equals("diagnose")) {
            if (!sender.hasPermission("crowncontrol.admin") && !sender.hasPermission("crowncontrol.admin.status")) return noPermission(sender);
            status(sender); return true;
        }
        if (sub.equals("blacklist")) return handlePokemonList(sender, args, true);
        if (sub.equals("whitelist")) return handlePokemonList(sender, args, false);
        if (sub.equals("list")) {
            if (!permissions.hasPlayerCommand(sender, "crowncontrol.command.list")) return noPermission(sender);
            showRule(sender); return true;
        }
        if (sub.equals("check")) {
            if (!(sender instanceof Player)) return playerOnly(sender);
            if (!permissions.hasPlayerCommand(sender, "crowncontrol.command.check")) return noPermission(sender);
            if (args.length < 2) { sender.sendMessage(color("&c用法：/crownc check <1-6>")); return true; }
            checkPokemon((Player) sender, args[1]); return true;
        }
        sender.sendMessage(color("&c未知子命令：&f" + sub)); help(sender); return true;
    }

    private void status(CommandSender sender) {
        sender.sendMessage(color("&d========== CrownControl rc8 状态 =========="));
        sender.sendMessage(color("&7配置文件：&f" + plugin.getConfigPath()));
        sender.sendMessage(color("&7运行模式：" + (plugin.isRuntimeReady() ? "&a原生金银皇冠拦截" : "&e诊断模式")));
        sender.sendMessage(color("&7Pixelmon事件桥：" + (plugin.isForgeRegistered() ? "&a已注册" : "&c未注册")
                + " &8(" + plugin.getForgeBridgeMode() + ")"));
        sender.sendMessage(color("&7事件总线：&f" + plugin.getForgeBridgeBus() + " &7| 归属：&f" + plugin.getForgeBridgeOwner()));
        sender.sendMessage(color("&7全部BottleCapEvent：&f" + plugin.getRawItemEvents()
                + " &7| 原生金银皇冠事件：&f" + plugin.getNativeCrownEvents()
                + " &7| 业务回调：&f" + plugin.getCallbacksInvoked()
                + " &7| 回调错误：&f" + plugin.getCallbackErrors()));
        sender.sendMessage(color("&7已处理原生金银皇冠：&f" + plugin.getHandledNativeCrowns()));
        sender.sendMessage(color("&7最近事件：&f" + plugin.getLastEventDebug()));
        sender.sendMessage(color("&7Vault：" + (plugin.isVaultAvailable() ? "&a可用" : "&e不可用")
                + " &7| PlayerPoints：" + (plugin.isPointsAvailable() ? "&a可用" : "&e不可用")));
        showRule(sender);
        if (!plugin.getStartupIssues().isEmpty()) {
            sender.sendMessage(color("&c启动/兼容问题："));
            for (String issue : plugin.getStartupIssues()) sender.sendMessage(color("&8- &f" + issue));
        }
        if (!plugin.getForgeBridgeError().isEmpty()) sender.sendMessage(color("&c事件桥详情：&f" + plugin.getForgeBridgeError()));
    }

    private void showRule(CommandSender sender) {
        GlobalRule rule = config.getGlobalRule();
        sender.sendMessage(color("&d全局金银皇冠规则：" + (rule != null && rule.isEnabled() && rule.isValid() ? "&a有效" : "&c无效")));
        if (rule == null) return;
        sender.sendMessage(color("&7允许类别：&f" + joinCategoryNames(rule.getAllowedCategories())));
        sender.sendMessage(color("&7成功率：&f" + rule.getSuccessRate() + "% &7| 金币：&f" + rule.getMoney()
                + " &7| 点券：&f" + rule.getPoints() + " &7| 货币扣除时机：&f" + rule.getConsumeOn()));
        sender.sendMessage(color("&7冷却：&f" + rule.getCooldownSeconds() + "秒 &7| 冷却时机：&f" + rule.getCooldownOn()));
        sender.sendMessage(color("&7黑名单：&f" + config.getGlobalBlacklist().size()
                + " &7| 白名单：&f" + config.getGlobalWhitelist().size()));
        if (!rule.isValid()) sender.sendMessage(color("&c错误：&f" + rule.getValidationError()));
    }

    private boolean handlePokemonList(CommandSender sender, String[] args, boolean blacklist) {
        if (!sender.hasPermission("crowncontrol.admin.lists") && !sender.hasPermission("crowncontrol.admin")) return noPermission(sender);
        String title = blacklist ? "黑名单" : "白名单";
        Set<String> current = blacklist ? config.getGlobalBlacklist() : config.getGlobalWhitelist();
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(color("&d全局精灵" + title + " &7(" + current.size() + ")："));
            sender.sendMessage(color(current.isEmpty() ? "&7<空>" : "&f" + join(current, ", ")));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("clear")) {
            boolean changed = config.clearPokemonList(blacklist);
            sender.sendMessage(color(changed ? "&a已清空精灵" + title + "。" : "&e精灵" + title + "已经为空。"));
            return true;
        }
        if ((action.equals("add") || action.equals("remove")) && args.length >= 3) {
            String species = joinArgs(args, 2);
            boolean changed = action.equals("add") ? config.addPokemonListEntry(blacklist, species)
                    : config.removePokemonListEntry(blacklist, species);
            sender.sendMessage(color(changed ? "&a操作成功：&e" + species : "&e没有发生变化。"));
            return true;
        }
        sender.sendMessage(color("&c用法：/crownc " + (blacklist ? "blacklist" : "whitelist")
                + " <list|add|remove|clear> [精灵名]"));
        return true;
    }

    private void checkPokemon(Player player, String slotText) {
        if (!pixelmon.isPartyLookupAvailable()) {
            player.sendMessage(color("&c当前 Pixelmon 构建未找到队伍查询桥；原生金银皇冠拦截仍可工作。")); return;
        }
        try {
            int slot = Integer.parseInt(slotText) - 1;
            if (slot < 0 || slot > 5) throw new NumberFormatException();
            PokemonView view = pixelmon.getPokemon(player, slot);
            if (view == null) { player.sendMessage(config.message("no-pokemon")); return; }
            GlobalRule rule = config.getGlobalRule();
            player.sendMessage(color("&d========== 精灵皇冠检查 =========="));
            player.sendMessage(color("&7精灵：&f" + view.getDisplayName() + " &8(&f" + view.getSpecies() + "&8)"));
            player.sendMessage(color("&7类别：&f" + view.getCategory() + " &7| 等级：&e" + view.getLevel()));
            player.sendMessage(color("&7极限训练：&f" + pixelmon.currentHyperTrainingText(view.getPokemon())));
            player.sendMessage(color("&7黑名单：" + (config.isGlobalBlacklisted(view) ? "&c命中" : "&a未命中")));
            player.sendMessage(color("&7白名单：" + (config.getGlobalWhitelist().isEmpty() ? "&7未启用"
                    : config.isGlobalWhitelisted(view) ? "&a命中" : "&c未命中")));
            player.sendMessage(color("&7种类规则：" + (rule != null && rule.getAllowedCategories().contains(view.getCategory()) ? "&a允许" : "&c拒绝")));
        } catch (NumberFormatException ex) {
            player.sendMessage(color("&c队伍位置必须是 1-6。"));
        } catch (Exception ex) {
            player.sendMessage(config.message("internal-error"));
            plugin.getLogger().warning("Pokemon check failed: " + ex.getMessage());
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(color("&d&lCrownControl &7v" + plugin.getDescription().getVersion()));
        sender.sendMessage(color("&7只监听 Pixelmon 原生金色王冠和银色王冠；没有自定义皇冠、NBT材料或额外物品消耗。"));
        if (permissions.hasPlayerCommand(sender, "crowncontrol.command.check")) sender.sendMessage(color("&f/crownc check <1-6> &7- 检查精灵名单、类别与极限训练状态"));
        if (permissions.hasPlayerCommand(sender, "crowncontrol.command.list")) sender.sendMessage(color("&f/crownc list &7- 查看全局规则"));
        if (sender.hasPermission("crowncontrol.admin.lists") || sender.hasPermission("crowncontrol.admin")) {
            sender.sendMessage(color("&f/crownc blacklist ... &7- 管理黑名单"));
            sender.sendMessage(color("&f/crownc whitelist ... &7- 管理白名单"));
        }
        if (sender.hasPermission("crowncontrol.admin.status") || sender.hasPermission("crowncontrol.admin")) sender.sendMessage(color("&f/crownc status &7- 查看事件与配置路径"));
        if (sender.hasPermission("crowncontrol.admin.reload") || sender.hasPermission("crowncontrol.admin")) sender.sendMessage(color("&f/crownc reload &7- 重载配置"));
    }

    private static boolean noPermission(CommandSender sender) { sender.sendMessage(color("&c你没有权限执行该命令。")); return true; }
    private static boolean playerOnly(CommandSender sender) { sender.sendMessage(color("&c该命令只能由玩家执行。")); return true; }
    private static String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> root = new ArrayList<String>(); root.add("help");
            if (permissions.hasPlayerCommand(sender, "crowncontrol.command.check")) root.add("check");
            if (permissions.hasPlayerCommand(sender, "crowncontrol.command.list")) root.add("list");
            if (sender.hasPermission("crowncontrol.admin.lists") || sender.hasPermission("crowncontrol.admin")) { root.add("blacklist"); root.add("whitelist"); }
            if (sender.hasPermission("crowncontrol.admin.status") || sender.hasPermission("crowncontrol.admin")) { root.add("status"); root.add("diagnose"); }
            if (sender.hasPermission("crowncontrol.admin.reload") || sender.hasPermission("crowncontrol.admin")) root.add("reload");
            return filter(root, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("blacklist") || args[0].equalsIgnoreCase("whitelist")))
            return filter(Arrays.asList("list", "add", "remove", "clear"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("check"))
            return filter(Arrays.asList("1", "2", "3", "4", "5", "6"), args[1]);
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> values, String prefix) {
        List<String> out = new ArrayList<String>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(value);
        return out;
    }
    private static String join(Iterable<String> values, String delimiter) {
        StringBuilder out = new StringBuilder(); for (String value : values) { if (out.length() > 0) out.append(delimiter); out.append(value); } return out.toString();
    }
    private static String joinCategoryNames(Iterable<?> values) {
        StringBuilder out = new StringBuilder(); for (Object value : values) { if (out.length() > 0) out.append(", "); out.append(value); } return out.toString();
    }
    private static String joinArgs(String[] args, int start) {
        StringBuilder out = new StringBuilder(); for (int i = start; i < args.length; i++) { if (out.length() > 0) out.append(' '); out.append(args[i]); } return out.toString();
    }
}
