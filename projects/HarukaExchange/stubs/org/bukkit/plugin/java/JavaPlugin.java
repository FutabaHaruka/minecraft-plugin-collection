package org.bukkit.plugin.java;
import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
public class JavaPlugin {
    public void onEnable() {}
    public void onDisable() {}
    public void saveDefaultConfig() {}
    public void reloadConfig() {}
    public FileConfiguration getConfig() { return null; }
    public File getDataFolder() { return null; }
    public PluginCommand getCommand(String name) { return null; }
    public Server getServer() { return null; }
    public Logger getLogger() { return null; }
    public PluginDescriptionFile getDescription() { return null; }
}
