package org.bukkit.entity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.PlayerInventory;
import java.util.UUID;
public interface Player extends CommandSender {
    UUID getUniqueId();
    String getName();
    boolean isOp();
    PlayerInventory getInventory();
    void updateInventory();
    World getWorld();
    Location getLocation();
}
