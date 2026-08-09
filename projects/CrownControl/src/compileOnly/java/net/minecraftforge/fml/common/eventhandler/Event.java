package net.minecraftforge.fml.common.eventhandler;
public class Event {
    public boolean isCanceled() { return false; }
    public boolean isCancelable() { return true; }
    public void setCanceled(boolean canceled) { }
}
