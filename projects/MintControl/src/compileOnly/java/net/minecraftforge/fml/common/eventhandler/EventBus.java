package net.minecraftforge.fml.common.eventhandler;
public class EventBus {
    public static Object lastRegistered;
    public static Object lastUnregistered;
    public void register(Object target) { lastRegistered = target; }
    public void unregister(Object target) { lastUnregistered = target; }
}
