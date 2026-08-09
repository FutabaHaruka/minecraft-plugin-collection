package org.bukkit.entity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
public interface Player extends HumanEntity {
    String getName();
    void openInventory(Inventory inventory);
    void closeInventory();
    PlayerInventory getInventory();
    ItemStack getItemInHand();
    World getWorld();
    Location getLocation();
}
