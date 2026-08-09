package com.aystudio.core.bukkit.nms;

import com.aystudio.core.bukkit.AyCore;
import com.aystudio.core.bukkit.nms.packet.EnumPacket;
import com.aystudio.core.bukkit.util.key.KeyListener;
import com.aystudio.core.common.data.CommonData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class INMSClass {
    private final Class<?> sendPacketParamsClass;
    private final ConcurrentMap<String, Class<?>> classCache = new ConcurrentHashMap<String, Class<?>>();
    private final ConcurrentMap<EnumPacket, Constructor<?>> packetConstructorCache = new ConcurrentHashMap<EnumPacket, Constructor<?>>();
    private final ConcurrentMap<Class<?>, Method> getHandleMethodCache = new ConcurrentHashMap<Class<?>, Method>();
    private final ConcurrentMap<Class<?>, Field> playerConnectionFieldCache = new ConcurrentHashMap<Class<?>, Field>();
    private final ConcurrentMap<Class<?>, Method> sendPacketMethodCache = new ConcurrentHashMap<Class<?>, Method>();
    private final ConcurrentMap<FieldKey, Field> fieldCache = new ConcurrentHashMap<FieldKey, Field>();
    private volatile Constructor<?> chatComponentTextConstructor;

    public INMSClass() {
        Class<?> packetClass = null;
        try {
            packetClass = resolveClass("net.minecraft.server." + CommonData.coreVersion + ".Packet");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        this.sendPacketParamsClass = packetClass;
    }

    public void registerChannel(AyCore instance) {
        Bukkit.getMessenger().registerOutgoingPluginChannel(instance, "keyexecute");
        Bukkit.getMessenger().registerIncomingPluginChannel(instance, "keyexecute", new KeyListener());
    }

    public void sendTitle(Player player, String title, String subTitle, int in, int stay, int out) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            Class<?> craftPlayerClass = player.getClass();
            Method getHandle = getHandleMethodCache.get(craftPlayerClass);
            if (getHandle == null) {
                Method resolved = craftPlayerClass.getMethod("getHandle");
                Method previous = getHandleMethodCache.putIfAbsent(craftPlayerClass, resolved);
                getHandle = previous == null ? resolved : previous;
            }
            Object entityPlayer = getHandle.invoke(player);

            Class<?> entityPlayerClass = entityPlayer.getClass();
            Field connectionField = playerConnectionFieldCache.get(entityPlayerClass);
            if (connectionField == null) {
                Field resolved = entityPlayerClass.getField("playerConnection");
                Field previous = playerConnectionFieldCache.putIfAbsent(entityPlayerClass, resolved);
                connectionField = previous == null ? resolved : previous;
            }
            Object playerConnection = connectionField.get(entityPlayer);

            Class<?> enumTitleAction = classForName("net.minecraft.server.?.PacketPlayOutTitle$EnumTitleAction");
            Object titleActionMain = getField(enumTitleAction, "TITLE", false);
            Object titleActionSub = getField(enumTitleAction, "SUBTITLE", false);

            sendPacket(playerConnection, createPacket(EnumPacket.PacketPlayOutTitle_1,
                    titleActionMain, createChatComponentText(title)));
            sendPacket(playerConnection, createPacket(EnumPacket.PacketPlayOutTitle_1,
                    titleActionSub, createChatComponentText(subTitle)));
            sendPacket(playerConnection, createPacket(EnumPacket.PacketPlayOutTitle_2, in, stay, out));
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public void sendPacket(Object playerConnection, Object packet) {
        try {
            Class<?> connectionClass = playerConnection.getClass();
            Method method = sendPacketMethodCache.get(connectionClass);
            if (method == null) {
                Method resolved = connectionClass.getMethod("sendPacket", sendPacketParamsClass);
                Method previous = sendPacketMethodCache.putIfAbsent(connectionClass, resolved);
                method = previous == null ? resolved : previous;
            }
            method.invoke(playerConnection, packet);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public Object createChatComponentText(String text) {
        try {
            Constructor<?> constructor = chatComponentTextConstructor;
            if (constructor == null) {
                synchronized (this) {
                    constructor = chatComponentTextConstructor;
                    if (constructor == null) {
                        Class<?> componentClass = resolveClass("net.minecraft.server." + CommonData.coreVersion + ".ChatComponentText");
                        constructor = componentClass.getConstructor(String.class);
                        chatComponentTextConstructor = constructor;
                    }
                }
            }
            return constructor.newInstance(ChatColor.translateAlternateColorCodes('&', text));
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
                 InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Class<?> classForName(String path) {
        String resolvedPath = path.replace("?", CommonData.coreVersion);
        try {
            return resolveClass(resolvedPath);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Object createPacket(EnumPacket packet, Object... params) {
        try {
            Constructor<?> constructor = packetConstructorCache.get(packet);
            if (constructor == null) {
                Class<?> packetClass = resolveClass(packet.getClassPath().replace("?", CommonData.coreVersion));
                Object[] targetParams = packet.getParams();
                Class<?>[] parameterClasses = new Class<?>[targetParams.length];
                for (int i = 0; i < targetParams.length; i++) {
                    Object targetParam = targetParams[i];
                    if (targetParam instanceof String) {
                        parameterClasses[i] = resolveClass(((String) targetParam).replace("?", CommonData.coreVersion));
                    } else {
                        parameterClasses[i] = (Class<?>) targetParam;
                    }
                }
                Constructor<?> resolved = packetClass.getConstructor(parameterClasses);
                Constructor<?> previous = packetConstructorCache.putIfAbsent(packet, resolved);
                constructor = previous == null ? resolved : previous;
            }
            return constructor.newInstance(params);
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
                 InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Object getField(Object obj, String fieldName, boolean getClass) {
        try {
            Class<?> owner = getClass ? obj.getClass() : (Class<?>) obj;
            FieldKey key = new FieldKey(owner, fieldName);
            Field field = fieldCache.get(key);
            if (field == null) {
                Field resolved = owner.getField(fieldName);
                Field previous = fieldCache.putIfAbsent(key, resolved);
                field = previous == null ? resolved : previous;
            }
            return field.get(obj);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Class<?> resolveClass(String className) throws ClassNotFoundException {
        Class<?> cached = classCache.get(className);
        if (cached != null) {
            return cached;
        }
        Class<?> resolved = Class.forName(className);
        Class<?> previous = classCache.putIfAbsent(className, resolved);
        return previous == null ? resolved : previous;
    }

    public abstract String getVID();

    private static final class FieldKey {
        private final Class<?> owner;
        private final String name;
        private final int hash;

        private FieldKey(Class<?> owner, String name) {
            this.owner = owner;
            this.name = name;
            this.hash = 31 * owner.hashCode() + name.hashCode();
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof FieldKey)) {
                return false;
            }
            FieldKey other = (FieldKey) obj;
            return owner == other.owner && name.equals(other.name);
        }
    }
}
