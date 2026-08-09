package org.bukkit;
import org.bukkit.plugin.messaging.Messenger;
public final class Bukkit {
 private static Messenger messenger;
 public static Messenger getMessenger() { return messenger; }
 public static void setMessenger(Messenger value) { messenger = value; }
}
