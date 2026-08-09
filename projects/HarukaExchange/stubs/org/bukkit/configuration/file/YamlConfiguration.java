package org.bukkit.configuration.file;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
public class YamlConfiguration extends FileConfiguration {
    public static YamlConfiguration loadConfiguration(File file) { return new YamlConfiguration(); }
    public String getString(String path) { return null; }
    public String getString(String path, String def) { return def; }
    public int getInt(String path) { return 0; }
    public int getInt(String path, int def) { return def; }
    public boolean getBoolean(String path) { return false; }
    public boolean getBoolean(String path, boolean def) { return def; }
    public List<String> getStringList(String path) { return null; }
    public ConfigurationSection getConfigurationSection(String path) { return null; }
    public Set<String> getKeys(boolean deep) { return null; }
    public Object get(String path) { return null; }
    public void set(String path, Object value) {}
    public ItemStack getItemStack(String path) { return null; }
    public List<?> getList(String path) { return null; }
    public void save(File file) throws IOException {}
}
