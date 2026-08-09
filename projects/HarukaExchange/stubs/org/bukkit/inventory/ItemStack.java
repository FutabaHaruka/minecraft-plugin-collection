package org.bukkit.inventory;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack implements Cloneable {
    public ItemStack(Material type) {}
    public ItemStack(Material type, int amount, short damage) {}
    public Material getType() { return null; }
    public int getAmount() { return 0; }
    public void setAmount(int amount) {}
    public int getMaxStackSize() { return 64; }
    public ItemMeta getItemMeta() { return null; }
    public boolean setItemMeta(ItemMeta meta) { return true; }
    public Map<String,Object> serialize() { return null; }
    @Override public ItemStack clone() { return null; }
}
