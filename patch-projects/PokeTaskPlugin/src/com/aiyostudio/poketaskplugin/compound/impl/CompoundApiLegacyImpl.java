package com.aiyostudio.poketaskplugin.compound.impl;

import com.aiyostudio.poketaskplugin.compound.CompoundApi;
import de.tr7zw.nbtapi.NBT;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** CatServer 1.12.2 safe NBT adapter: AIR/zero stacks are not valid NBT items. */
public class CompoundApiLegacyImpl implements CompoundApi {
    @Override
    public boolean hasKey(ItemStack itemStack, final String key) {
        if (!isUsable(itemStack)) return false;
        return NBT.get(itemStack, nbt -> nbt.hasTag(key));
    }

    @Override
    public String getString(ItemStack itemStack, final String key) {
        if (!isUsable(itemStack)) return null;
        return NBT.get(itemStack, nbt -> nbt.getString(key));
    }

    @Override
    public ItemStack setString(ItemStack itemStack, final String key, final String value) {
        if (!isUsable(itemStack)) return itemStack;
        ItemStack clone = itemStack.clone();
        NBT.modify(clone, nbt -> nbt.setString(key, value));
        return clone;
    }

    @Override
    public ItemStack getSprite(final String sprite) {
        ItemStack itemStack = new ItemStack(Material.valueOf("PIXELMON_PIXELMON_SPRITE"), 1);
        NBT.modify(itemStack, nbt -> nbt.setString("SpriteName", "pixelmon:sprites/pokemon/" + sprite));
        return itemStack;
    }

    private static boolean isUsable(ItemStack itemStack) {
        return itemStack != null
                && itemStack.getType() != null
                && itemStack.getType() != Material.AIR
                && itemStack.getAmount() > 0;
    }
}
