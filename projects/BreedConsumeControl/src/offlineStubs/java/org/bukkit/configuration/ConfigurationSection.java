package org.bukkit.configuration;
import java.util.List;
import java.util.Set;
public interface ConfigurationSection {
    int getInt(String path, int def);
    int getInt(String path);
    long getLong(String path, long def);
    double getDouble(String path, double def);
    double getDouble(String path);
    boolean getBoolean(String path, boolean def);
    boolean getBoolean(String path);
    String getString(String path, String def);
    String getString(String path);
    List<String> getStringList(String path);
    ConfigurationSection getConfigurationSection(String path);
    Set<String> getKeys(boolean deep);
    boolean contains(String path);
    boolean isConfigurationSection(String path);
    void set(String path, Object value);
}
