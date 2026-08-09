package net.minecraftforge.fml.common.eventhandler;
public class Event {
    public enum Result { DENY, DEFAULT, ALLOW }
    public boolean isCancelable() { return false; }
    public boolean isCanceled() { return false; }
    public void setCanceled(boolean cancel) { }
    public boolean hasResult() { return false; }
    public Result getResult() { return Result.DEFAULT; }
    public void setResult(Result value) { }
}
