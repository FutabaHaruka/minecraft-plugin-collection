package org.bukkit.inventory;
import java.util.Map;
public interface PlayerInventory {
    ItemStack[] getContents();
    ItemStack[] getStorageContents();
    void setContents(ItemStack[] contents);
    void setItem(int slot, ItemStack item);
    Map<Integer, ItemStack> addItem(ItemStack... items);
    ItemStack getItemInMainHand();
}
