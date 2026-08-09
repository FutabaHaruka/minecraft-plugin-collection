package com.pixelmonmod.pixelmon.api.events.pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.enums.items.EnumBottleCap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.Event;
public class BottleCapEvent extends Event {
    public EntityPlayer getPlayer() { return null; }
    public Pokemon getPokemon() { return null; }
    public EnumBottleCap getBottleCap() { return null; }
    public ItemStack getItemStack() { return null; }
}
