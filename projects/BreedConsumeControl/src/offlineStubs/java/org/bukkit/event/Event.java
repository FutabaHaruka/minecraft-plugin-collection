package org.bukkit.event;
public abstract class Event {
    private final boolean async;
    protected Event() { this(false); }
    protected Event(boolean async) { this.async = async; }
    public final boolean isAsynchronous() { return async; }
    public abstract HandlerList getHandlers();
}
