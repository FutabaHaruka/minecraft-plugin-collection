package org.bukkit.inventory.meta;
import java.util.List;
public interface ItemMeta {
    void setDisplayName(String name);
    void setLore(List<String> lore);
}
