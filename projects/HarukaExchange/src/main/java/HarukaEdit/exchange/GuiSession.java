package HarukaEdit.exchange;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuiSession implements InventoryHolder {
    public enum Mode { EXCHANGE, EDITOR }

    private final String recipeId;
    private final Mode mode;
    private Inventory inventory;

    public GuiSession(String recipeId, Mode mode) {
        this.recipeId = recipeId;
        this.mode = mode;
    }

    public String getRecipeId() { return recipeId; }
    public Mode getMode() { return mode; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public Inventory getInventory() { return inventory; }
}
