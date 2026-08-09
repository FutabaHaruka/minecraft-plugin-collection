package org.bukkit.event.inventory;
import java.util.Set;
import org.bukkit.inventory.Inventory;
public class InventoryDragEvent {
    public Inventory getInventory() { return null; }
    public Set<Integer> getRawSlots() { return null; }
    public void setCancelled(boolean cancelled) {}
}
