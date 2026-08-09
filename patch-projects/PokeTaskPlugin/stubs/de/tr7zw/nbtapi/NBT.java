package de.tr7zw.nbtapi;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.inventory.ItemStack;
import de.tr7zw.nbtapi.iface.ReadableItemNBT;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
public final class NBT {
    public static <T> T get(ItemStack item, Function<ReadableItemNBT,T> function) { return null; }
    public static void modify(ItemStack item, Consumer<ReadWriteItemNBT> consumer) {}
}
