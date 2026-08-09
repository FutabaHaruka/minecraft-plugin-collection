package org.bukkit.configuration;
import java.util.List;
import java.util.Set;
public interface ConfigurationSection {
    String getString(String path);
    String getString(String path, String def);
    int getInt(String path);
    int getInt(String path, int def);
    boolean getBoolean(String path);
    boolean getBoolean(String path, boolean def);
    List<String> getStringList(String path);
    ConfigurationSection getConfigurationSection(String path);
    Set<String> getKeys(boolean deep);
    Object get(String path);
    void set(String path, Object value);
}
