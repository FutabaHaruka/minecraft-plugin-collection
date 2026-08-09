package de.tr7zw.nbtapi.iface;
public interface ReadableItemNBT {
    boolean hasTag(String key);
    String getString(String key);
}
