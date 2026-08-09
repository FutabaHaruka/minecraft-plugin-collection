package org.bukkit.inventory.meta;
import java.util.List;
public interface ItemMeta {
    boolean hasDisplayName();
    String getDisplayName();
    boolean hasLore();
    List<String> getLore();
}
