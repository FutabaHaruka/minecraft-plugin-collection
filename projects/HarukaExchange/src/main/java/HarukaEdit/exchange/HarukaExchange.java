package HarukaEdit.exchange;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class HarukaExchange extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private ExchangeRepository repository;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        repository = new ExchangeRepository(this);
        repository.loadAll();
        PluginCommand command = getCommand("harukaexchange");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("HarukaEdit.exchange 已启用，已加载 " + repository.ids().size() + " 个兑换。版本 " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        getLogger().info("HarukaEdit.exchange 已卸载。");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("open".equals(sub)) return commandOpen(sender, args);
        if (!sender.hasPermission("harukaexchange.admin")) {
            send(sender, "no-permission");
            return true;
        }
        try {
            if ("edit".equals(sub) || "create".equals(sub)) return commandEdit(sender, args);
            if ("set".equals(sub)) return commandSet(sender, args);
            if ("clear".equals(sub)) return commandClear(sender, args);
            if ("delete".equals(sub)) return commandDelete(sender, args);
            if ("list".equals(sub)) return commandList(sender);
            if ("reload".equals(sub)) {
                reloadConfig();
                repository.loadAll();
                send(sender, "reloaded");
                return true;
            }
            if ("import-xinxin".equals(sub) || "migrate".equals(sub)) return commandImport(sender);
        } catch (Throwable error) {
            sender.sendMessage(prefix() + ChatColor.RED + "操作失败：" + error.getClass().getSimpleName() + ": " + error.getMessage());
            getLogger().warning("命令执行失败：" + error.getMessage());
            error.printStackTrace();
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private boolean commandOpen(CommandSender sender, String[] args) {
        if (!sender.hasPermission("harukaexchange.use")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "用法：/hxe open <兑换ID> [玩家]");
            return true;
        }
        Player target;
        if (args.length >= 3) target = Bukkit.getPlayerExact(args[2]);
        else target = sender instanceof Player ? (Player) sender : null;
        if (target == null) {
            send(sender, "player-not-found", "%player%", args.length >= 3 ? args[2] : "");
            return true;
        }
        ExchangeRecipe recipe = repository.get(args[1]);
        if (recipe == null) {
            send(sender, "recipe-not-found", "%id%", args[1]);
            return true;
        }
        openExchange(target, recipe);
        return true;
    }

    private boolean commandEdit(CommandSender sender, String[] args) throws IOException {
        if (!(sender instanceof Player)) {
            send(sender, "player-only");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "用法：/hxe edit <兑换ID>");
            return true;
        }
        if (!validId(args[1])) {
            send(sender, "invalid-id");
            return true;
        }
        ExchangeRecipe recipe = repository.getOrCreate(args[1]);
        repository.save(recipe);
        openEditor((Player) sender, recipe);
        return true;
    }

    private boolean commandSet(CommandSender sender, String[] args) throws IOException {
        if (!(sender instanceof Player)) {
            send(sender, "player-only");
            return true;
        }
        if (args.length < 3 || !validPart(args[2])) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "用法：/hxe set <兑换ID> <input1|input2|output>");
            return true;
        }
        if (!validId(args[1])) {
            send(sender, "invalid-id");
            return true;
        }
        ItemStack hand = ((Player) sender).getItemInHand();
        if (ItemUtil.isEmpty(hand)) {
            sender.sendMessage(prefix() + ChatColor.RED + "请先将要保存的物品拿在主手中。物品不会被消耗。");
            return true;
        }
        ExchangeRecipe recipe = repository.getOrCreate(args[1]);
        setPart(recipe, args[2], hand.clone());
        repository.save(recipe);
        send(sender, "set-success", "%id%", recipe.getId(), "%part%", args[2]);
        return true;
    }

    private boolean commandClear(CommandSender sender, String[] args) throws IOException {
        if (args.length < 3 || !validPart(args[2])) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "用法：/hxe clear <兑换ID> <input1|input2|output>");
            return true;
        }
        ExchangeRecipe recipe = repository.get(args[1]);
        if (recipe == null) {
            send(sender, "recipe-not-found", "%id%", args[1]);
            return true;
        }
        setPart(recipe, args[2], null);
        repository.save(recipe);
        send(sender, "clear-success", "%id%", recipe.getId(), "%part%", args[2]);
        return true;
    }

    private boolean commandDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "用法：/hxe delete <兑换ID>");
            return true;
        }
        if (!repository.delete(args[1])) {
            send(sender, "recipe-not-found", "%id%", args[1]);
            return true;
        }
        send(sender, "deleted", "%id%", args[1]);
        return true;
    }

    private boolean commandList(CommandSender sender) {
        List<String> ids = repository.ids();
        sender.sendMessage(prefix() + ChatColor.AQUA + "兑换列表（" + ids.size() + "）：" + ChatColor.WHITE + (ids.isEmpty() ? "无" : join(ids, ", ")));
        return true;
    }

    private boolean commandImport(CommandSender sender) throws IOException {
        LegacyImporter.Result result = new LegacyImporter(this, repository).importAll();
        if (result == null) {
            send(sender, "import-missing");
            return true;
        }
        send(sender, "import-complete", "%success%", String.valueOf(result.success), "%skipped%", String.valueOf(result.skipped));
        return true;
    }

    private void openExchange(Player player, ExchangeRecipe recipe) {
        int size = normalizedSize(getConfig().getInt("menu.size", 27));
        String title = ItemUtil.color(getConfig().getString("menu.title", "&8NPC兑换 | &f%name%")).replace("%name%", recipe.getDisplayName());
        GuiSession holder = new GuiSession(recipe.getId(), GuiSession.Mode.EXCHANGE);
        Inventory inventory = Bukkit.createInventory(holder, size, trimTitle(title));
        holder.setInventory(inventory);
        fillBorder(inventory);
        inventory.setItem(slot("input-1-slot", 10, size), recipe.getInput1());
        inventory.setItem(slot("input-2-slot", 12, size), recipe.getInput2());
        inventory.setItem(slot("arrow-slot", 14, size), ItemUtil.configuredItem(getConfig().getConfigurationSection("menu.arrow")));
        inventory.setItem(slot("output-slot", 16, size), recipe.getOutput());
        inventory.setItem(slot("info-slot", 22, size), ItemUtil.configuredItem(getConfig().getConfigurationSection("menu.info")));
        player.openInventory(inventory);
    }

    private void openEditor(Player player, ExchangeRecipe recipe) {
        int size = normalizedSize(getConfig().getInt("menu.size", 27));
        String title = ItemUtil.color(getConfig().getString("menu.editor-title", "&8编辑兑换 | &f%id%")).replace("%id%", recipe.getId());
        GuiSession holder = new GuiSession(recipe.getId(), GuiSession.Mode.EDITOR);
        Inventory inventory = Bukkit.createInventory(holder, size, trimTitle(title));
        holder.setInventory(inventory);
        fillBorder(inventory);
        inventory.setItem(slot("input-1-slot", 10, size), recipe.getInput1());
        inventory.setItem(slot("input-2-slot", 12, size), recipe.getInput2());
        inventory.setItem(slot("arrow-slot", 14, size), ItemUtil.configuredItem(getConfig().getConfigurationSection("menu.arrow")));
        inventory.setItem(slot("output-slot", 16, size), recipe.getOutput());
        inventory.setItem(slot("info-slot", 22, size), ItemUtil.configuredItem(getConfig().getConfigurationSection("menu.info")));
        player.openInventory(inventory);
        send(player, "editor-tip");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof GuiSession)) return;
        GuiSession session = (GuiSession) top.getHolder();
        int raw = event.getRawSlot();
        int size = top.getSize();
        int in1 = slot("input-1-slot", 10, size);
        int in2 = slot("input-2-slot", 12, size);
        int out = slot("output-slot", 16, size);

        if (raw >= size) {
            // 允许玩家在自己背包中正常拿取物品，但禁止 Shift 点击把物品塞进上方菜单。
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ExchangeRecipe recipe = repository.get(session.getRecipeId());
        if (recipe == null) {
            player.closeInventory();
            return;
        }

        if (session.getMode() == GuiSession.Mode.EDITOR) {
            if (raw != in1 && raw != in2 && raw != out) return;
            try {
                if (event.isRightClick()) {
                    setEditorPart(recipe, raw, in1, in2, null);
                    top.setItem(raw, null);
                } else {
                    ItemStack cursor = event.getCursor();
                    if (ItemUtil.isEmpty(cursor)) {
                        player.sendMessage(prefix() + ChatColor.RED + "请先把要保存的物品拿在鼠标上。右键槽位可清空。");
                        return;
                    }
                    ItemStack copy = cursor.clone();
                    setEditorPart(recipe, raw, in1, in2, copy);
                    top.setItem(raw, copy.clone());
                }
                repository.save(recipe);
                send(player, "saved", "%id%", recipe.getId());
            } catch (IOException error) {
                player.sendMessage(prefix() + ChatColor.RED + "保存失败：" + error.getMessage());
            }
            return;
        }

        if (raw != out) return;
        if (!recipe.isComplete()) {
            send(player, "empty-recipe");
            return;
        }
        int cap = Math.max(1, getConfig().getInt("exchange.max-shift-batch", 64));
        int trades = event.isShiftClick() ? ItemUtil.maxTrades(player.getInventory(), recipe, cap) : 1;
        if (trades <= 0 || !ItemUtil.removeForTrades(player.getInventory(), recipe, trades)) {
            send(player, "not-enough");
            return;
        }
        ItemUtil.giveOutput(player, recipe.getOutput(), trades, getConfig().getBoolean("exchange.drop-overflow-at-player", true));
        send(player, "success");
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof GuiSession)) return;
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot >= 0 && rawSlot < inventory.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof GuiSession)) return;
        // 编辑器使用“复制鼠标物品”的方式，关闭时无需返还或再次保存，避免吞物品。
    }

    private void fillBorder(Inventory inventory) {
        ItemStack border = ItemUtil.configuredItem(getConfig().getConfigurationSection("menu.border"));
        int size = inventory.getSize();
        int in1 = slot("input-1-slot", 10, size);
        int in2 = slot("input-2-slot", 12, size);
        int arrow = slot("arrow-slot", 14, size);
        int out = slot("output-slot", 16, size);
        int info = slot("info-slot", 22, size);
        for (int i = 0; i < size; i++) {
            if (i == in1 || i == in2 || i == arrow || i == out || i == info) continue;
            inventory.setItem(i, border.clone());
        }
    }

    private void setEditorPart(ExchangeRecipe recipe, int raw, int in1, int in2, ItemStack value) {
        if (raw == in1) recipe.setInput1(value);
        else if (raw == in2) recipe.setInput2(value);
        else recipe.setOutput(value);
    }

    private static void setPart(ExchangeRecipe recipe, String part, ItemStack value) {
        if ("input1".equalsIgnoreCase(part)) recipe.setInput1(value);
        else if ("input2".equalsIgnoreCase(part)) recipe.setInput2(value);
        else recipe.setOutput(value);
    }

    private static boolean validPart(String part) {
        return "input1".equalsIgnoreCase(part) || "input2".equalsIgnoreCase(part) || "output".equalsIgnoreCase(part);
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[\\p{L}\\p{N}_-]{1,48}");
    }

    private int slot(String key, int fallback, int size) {
        int value = getConfig().getInt("menu." + key, fallback);
        return value >= 0 && value < size ? value : fallback;
    }

    private static int normalizedSize(int requested) {
        int size = Math.max(9, Math.min(54, requested));
        return ((size + 8) / 9) * 9;
    }

    private static String trimTitle(String title) {
        return title.length() <= 32 ? title : title.substring(0, 32);
    }

    private String prefix() {
        return ItemUtil.color(getConfig().getString("messages.prefix", "&8[&bHarukaExchange&8] &r"));
    }

    private void send(CommandSender sender, String key, String... replacements) {
        String message = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(prefix() + ItemUtil.color(message));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(prefix() + ChatColor.AQUA + "命令帮助");
        sender.sendMessage(ChatColor.YELLOW + "/hxe open <ID> [玩家]" + ChatColor.GRAY + " - 打开兑换；控制台需指定玩家");
        if (sender.hasPermission("harukaexchange.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/hxe edit <ID>" + ChatColor.GRAY + " - 创建或编辑兑换");
            sender.sendMessage(ChatColor.YELLOW + "/hxe set <ID> <input1|input2|output>" + ChatColor.GRAY + " - 保存手中物品副本");
            sender.sendMessage(ChatColor.YELLOW + "/hxe clear <ID> <input1|input2|output>" + ChatColor.GRAY + " - 清空一格");
            sender.sendMessage(ChatColor.YELLOW + "/hxe list | delete <ID> | reload" + ChatColor.GRAY + " - 管理兑换");
            sender.sendMessage(ChatColor.YELLOW + "/hxe import-xinxin" + ChatColor.GRAY + " - 迁移旧 XinxinExchange 数据");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<String>(Arrays.asList("open", "help"));
            if (sender.hasPermission("harukaexchange.admin")) values.addAll(Arrays.asList("edit", "set", "clear", "delete", "list", "reload", "import-xinxin"));
            return filter(values, args[0]);
        }
        if (args.length == 2 && Arrays.asList("open", "edit", "set", "clear", "delete").contains(args[0].toLowerCase(Locale.ROOT))) return filter(repository.ids(), args[1]);
        if (args.length == 3 && ("set".equalsIgnoreCase(args[0]) || "clear".equalsIgnoreCase(args[0]))) return filter(Arrays.asList("input1", "input2", "output"), args[2]);
        if (args.length == 3 && "open".equalsIgnoreCase(args[0])) {
            List<String> players = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) players.add(player.getName());
            return filter(players, args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> values, String input) {
        List<String> out = new ArrayList<String>();
        String lower = input.toLowerCase(Locale.ROOT);
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(value);
        return out;
    }

    private static String join(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(separator);
            builder.append(value);
        }
        return builder.toString();
    }
}
