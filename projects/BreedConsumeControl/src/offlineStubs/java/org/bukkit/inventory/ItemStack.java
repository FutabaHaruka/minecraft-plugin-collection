package org.bukkit.inventory;
import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack implements Cloneable {
    public int getAmount() { return 0; }
    public void setAmount(int amount) { }
    public int getTypeId() { return 0; }
    public short getDurability() { return 0; }
    public boolean hasItemMeta() { return false; }
    public ItemMeta getItemMeta() { return null; }
    public ItemStack clone() { return new ItemStack(); }
}
