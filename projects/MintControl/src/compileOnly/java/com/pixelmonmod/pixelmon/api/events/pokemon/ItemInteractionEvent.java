package com.pixelmonmod.pixelmon.api.events.pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.Event;
public class ItemInteractionEvent extends Event {
    public EntityPlayer getPlayer() { return null; }
    public Pokemon getPokemon() { return null; }
    public ItemStack getItemStack() { return null; }
}
