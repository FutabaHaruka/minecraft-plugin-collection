package org.bukkit.configuration.file;
import java.io.File;
import java.io.IOException;
public abstract class YamlConfiguration extends FileConfiguration {
    public static YamlConfiguration loadConfiguration(File file) { return null; }
    public abstract void save(File file) throws IOException;
}
