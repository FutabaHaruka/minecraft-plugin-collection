package HarukaEdit.exchange;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ItemUtil {
    private ItemUtil() {}

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    public static ItemStack cloneOrNull(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    public static boolean sameExactItem(ItemStack a, ItemStack b) {
        if (isEmpty(a) || isEmpty(b)) return false;
        Map<String, Object> left = new LinkedHashMap<String, Object>(a.serialize());
        Map<String, Object> right = new LinkedHashMap<String, Object>(b.serialize());
        // 兑换配方中的数量代表“需要多少个”，同类判断只忽略 ItemStack 最外层数量。
        // NBT 或自定义序列化数据内部若也存在 amount 字段，仍会参与比较。
        left.remove("amount");
        right.remove("amount");
        return left.equals(right);
    }

    public static int count(Inventory inventory, ItemStack sample) {
        if (isEmpty(sample)) return Integer.MAX_VALUE;
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            if (sameExactItem(item, sample)) total += item.getAmount();
        }
        return total;
    }

    public static int maxTrades(Inventory inventory, ExchangeRecipe recipe, int cap) {
        ItemStack a = recipe.getInput1();
        ItemStack b = recipe.getInput2();
        if (isEmpty(a)) return 0;
        int max;
        if (!isEmpty(b) && sameExactItem(a, b)) {
            int required = a.getAmount() + b.getAmount();
            max = required <= 0 ? 0 : count(inventory, a) / required;
        } else {
            max = count(inventory, a) / Math.max(1, a.getAmount());
            if (!isEmpty(b)) max = Math.min(max, count(inventory, b) / Math.max(1, b.getAmount()));
        }
        return Math.max(0, Math.min(cap, max));
    }

    public static boolean removeForTrades(Inventory inventory, ExchangeRecipe recipe, int trades) {
        if (trades <= 0) return false;
        ItemStack a = recipe.getInput1();
        ItemStack b = recipe.getInput2();
        if (maxTrades(inventory, recipe, trades) < trades) return false;
        if (!isEmpty(b) && sameExactItem(a, b)) {
            return removeAmount(inventory, a, (a.getAmount() + b.getAmount()) * trades);
        }
        if (!removeAmount(inventory, a, a.getAmount() * trades)) return false;
        if (!isEmpty(b) && !removeAmount(inventory, b, b.getAmount() * trades)) return false;
        return true;
    }

    private static boolean removeAmount(Inventory inventory, ItemStack sample, int amount) {
        int left = amount;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (!sameExactItem(item, sample)) continue;
            int take = Math.min(left, item.getAmount());
            int remain = item.getAmount() - take;
            left -= take;
            if (remain <= 0) inventory.setItem(i, null);
            else {
                item.setAmount(remain);
                inventory.setItem(i, item);
            }
        }
        return left == 0;
    }

    public static void giveOutput(Player player, ItemStack output, int trades, boolean dropOverflow) {
        int total = output.getAmount() * trades;
        int max = Math.max(1, output.getMaxStackSize());
        while (total > 0) {
            int amount = Math.min(max, total);
            ItemStack stack = output.clone();
            stack.setAmount(amount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            if (dropOverflow && leftovers != null) {
                for (ItemStack leftover : leftovers.values()) {
                    if (!isEmpty(leftover)) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
            total -= amount;
        }
    }

    public static ItemStack configuredItem(ConfigurationSection section) {
        if (section == null) return new ItemStack(Material.STONE);
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) material = Material.STONE;
        int data = section.getInt("data", 0);
        ItemStack item = new ItemStack(material, 1, (short) data);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = section.getString("name", "");
            if (!name.isEmpty()) meta.setDisplayName(color(name));
            List<String> lore = section.getStringList("lore");
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<String>();
                for (String line : lore) colored.add(color(line));
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
