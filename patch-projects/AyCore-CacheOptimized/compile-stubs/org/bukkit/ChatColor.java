package org.bukkit;
public final class ChatColor {
 public static String translateAlternateColorCodes(char alt, String text) { return text == null ? null : text.replace(alt, '\u00a7'); }
}
