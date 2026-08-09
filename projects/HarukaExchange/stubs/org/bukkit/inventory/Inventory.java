package org.bukkit.inventory;
import java.util.HashMap;
public interface Inventory {
    InventoryHolder getHolder();
    int getSize();
    ItemStack[] getContents();
    void setItem(int slot, ItemStack item);
    ItemStack getItem(int slot);
    HashMap<Integer, ItemStack> addItem(ItemStack... items);
}
