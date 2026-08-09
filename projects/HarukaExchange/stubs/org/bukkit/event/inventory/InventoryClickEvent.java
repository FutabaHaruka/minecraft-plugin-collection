package org.bukkit.event.inventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
public class InventoryClickEvent {
    public Inventory getInventory() { return null; }
    public int getRawSlot() { return 0; }
    public HumanEntity getWhoClicked() { return null; }
    public void setCancelled(boolean cancelled) {}
    public boolean isRightClick() { return false; }
    public boolean isShiftClick() { return false; }
    public ItemStack getCursor() { return null; }
}
