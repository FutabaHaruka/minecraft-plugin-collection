package cn.licry.crowncontrol.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves classes across Bukkit plugin, server and Forge LaunchClassLoader
 * boundaries used by 1.12.2 hybrid servers such as CatServer.
 */
public final class HybridClassResolver {
    private HybridClassResolver() { }

    public static Class<?> load(JavaPlugin plugin, String name) throws ClassNotFoundException {
        ClassNotFoundException failure = null;
        for (ClassLoader loader : candidateLoaders(plugin)) {
            if (loader == null) continue;
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ex) {
                failure = ex;
            } catch (LinkageError ex) {
                failure = new ClassNotFoundException(name + " via " + describe(loader) + ": " + ex, ex);
            }
        }
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ex) {
            if (failure != null) ex.addSuppressed(failure);
            throw ex;
        }
    }

    public static Set<ClassLoader> candidateLoaders(JavaPlugin plugin) {
        Set<ClassLoader> loaders = new LinkedHashSet<ClassLoader>();
        if (plugin != null) {
            loaders.add(plugin.getClass().getClassLoader());
            try {
                if (plugin.getServer() != null) loaders.add(plugin.getServer().getClass().getClassLoader());
            } catch (Throwable ignored) { }
        }
        loaders.add(Thread.currentThread().getContextClassLoader());
        loaders.add(HybridClassResolver.class.getClassLoader());
        loaders.add(ClassLoader.getSystemClassLoader());
        ClassLoader launch = findLaunchClassLoader(plugin, loaders);
        if (launch != null) loaders.add(launch);
        loaders.remove(null);
        return loaders;
    }

    public static ClassLoader findLaunchClassLoader(JavaPlugin plugin) {
        return findLaunchClassLoader(plugin, candidateBaseLoaders(plugin));
    }

    private static Set<ClassLoader> candidateBaseLoaders(JavaPlugin plugin) {
        Set<ClassLoader> loaders = new LinkedHashSet<ClassLoader>();
        if (plugin != null) {
            loaders.add(plugin.getClass().getClassLoader());
            try {
                if (plugin.getServer() != null) loaders.add(plugin.getServer().getClass().getClassLoader());
            } catch (Throwable ignored) { }
        }
        loaders.add(Thread.currentThread().getContextClassLoader());
        loaders.add(HybridClassResolver.class.getClassLoader());
        loaders.add(ClassLoader.getSystemClassLoader());
        loaders.remove(null);
        return loaders;
    }

    private static ClassLoader findLaunchClassLoader(JavaPlugin plugin, Set<ClassLoader> loaders) {
        for (ClassLoader loader : loaders) {
            try {
                Class<?> launch = Class.forName("net.minecraft.launchwrapper.Launch", false, loader);
                Field field;
                try {
                    field = launch.getField("classLoader");
                } catch (NoSuchFieldException ex) {
                    field = launch.getDeclaredField("classLoader");
                    field.setAccessible(true);
                }
                Object value = field.get(null);
                if (value instanceof ClassLoader) return (ClassLoader) value;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    public static String describe(ClassLoader loader) {
        return loader == null ? "<bootstrap>" : loader.getClass().getName();
    }
}
