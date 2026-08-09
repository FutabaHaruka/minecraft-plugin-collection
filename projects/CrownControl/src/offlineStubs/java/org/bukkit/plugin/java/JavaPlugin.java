package org.bukkit.plugin.java;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;
public class JavaPlugin implements Plugin {
    public void onEnable() { }
    public void onDisable() { }
    public void saveDefaultConfig() { }
    public void reloadConfig() { }
    public void saveConfig() { }
    public FileConfiguration getConfig() { return null; }
    public Logger getLogger() { return Logger.getLogger("stub"); }
    public Server getServer() { return null; }
    public PluginCommand getCommand(String name) { return null; }
    public File getDataFolder() { return null; }
    public InputStream getResource(String name) { return null; }
    public PluginDescriptionFile getDescription() { return null; }
    public boolean isEnabled() { return true; }
}
