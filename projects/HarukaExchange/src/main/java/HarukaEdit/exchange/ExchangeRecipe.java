package HarukaEdit.exchange;

import org.bukkit.inventory.ItemStack;

public final class ExchangeRecipe {
    private final String id;
    private String displayName;
    private ItemStack input1;
    private ItemStack input2;
    private ItemStack output;

    public ExchangeRecipe(String id) {
        this.id = id;
        this.displayName = id;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName == null || displayName.isEmpty() ? id : displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public ItemStack getInput1() { return ItemUtil.cloneOrNull(input1); }
    public void setInput1(ItemStack input1) { this.input1 = ItemUtil.cloneOrNull(input1); }
    public ItemStack getInput2() { return ItemUtil.cloneOrNull(input2); }
    public void setInput2(ItemStack input2) { this.input2 = ItemUtil.cloneOrNull(input2); }
    public ItemStack getOutput() { return ItemUtil.cloneOrNull(output); }
    public void setOutput(ItemStack output) { this.output = ItemUtil.cloneOrNull(output); }

    public boolean isComplete() {
        return !ItemUtil.isEmpty(input1) && !ItemUtil.isEmpty(output);
    }
}
