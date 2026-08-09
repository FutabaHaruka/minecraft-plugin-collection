package cn.licry.crowncontrol.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Supplies Forge 1.12's EventBus with a temporary active ModContainer while a
 * Bukkit plugin registers a listener. EventBus.register(Object) logs a critical
 * error whenever Loader.activeModContainer() is null, then silently falls back
 * to Minecraft's container. This helper performs that fallback explicitly and
 * restores the previous loader context immediately after registration.
 */
public final class ForgeModOwnerContext {
    private static final String LOADER_CLASS = "net.minecraftforge.fml.common.Loader";

    private ForgeModOwnerContext() { }

    public static Scope enter(ClassLoader forgeClassLoader) throws Exception {
        return enter(forgeClassLoader, null);
    }

    /** Prefer a concrete mod id (for example pixelmon) as the temporary owner. */
    public static Scope enter(ClassLoader forgeClassLoader, String preferredModId) throws Exception {
        Class<?> loaderType = loadLoader(forgeClassLoader);
        Object loader = invokeStatic(loaderType, "instance");
        if (loader == null) throw new IllegalStateException("Forge Loader.instance() returned null");

        Method activeMethod = findMethod(loaderType, "activeModContainer", 0);
        Method setActiveMethod = findMethod(loaderType, "setActiveModContainer", 1);
        if (activeMethod == null || setActiveMethod == null) {
            throw new NoSuchMethodException("Forge Loader active container methods are unavailable");
        }

        Object previous = invoke(activeMethod, loader);
        if (previous != null) {
            return new Scope(loader, setActiveMethod, previous, previous, false, ownerId(previous));
        }

        Object owner = findIndexedOwner(loaderType, loader, preferredModId);
        if (owner == null) owner = findMinecraftOwner(loaderType, loader);
        if (owner == null) owner = findIndexedOwner(loaderType, loader, null);
        if (owner == null) owner = findAnyOwner(loaderType, loader);
        if (owner == null) {
            throw new IllegalStateException("No Forge ModContainer is available for EventBus registration");
        }

        invoke(setActiveMethod, loader, owner);
        Object activeNow = invoke(activeMethod, loader);
        if (activeNow != owner) {
            // Best effort restore before surfacing the incompatibility.
            invoke(setActiveMethod, loader, previous);
            throw new IllegalStateException("Forge Loader rejected the temporary active ModContainer");
        }
        return new Scope(loader, setActiveMethod, previous, owner, true, ownerId(owner));
    }

    private static Class<?> loadLoader(ClassLoader preferred) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        ClassLoader[] candidates = new ClassLoader[] {
                preferred,
                Thread.currentThread().getContextClassLoader(),
                ForgeModOwnerContext.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader loader : candidates) {
            if (loader == null) continue;
            try {
                return Class.forName(LOADER_CLASS, true, loader);
            } catch (ClassNotFoundException ex) {
                last = ex;
            }
        }
        if (last != null) throw last;
        throw new ClassNotFoundException(LOADER_CLASS);
    }

    private static Object findMinecraftOwner(Class<?> loaderType, Object loader) {
        Method method = findMethod(loaderType, "getMinecraftModContainer", 0);
        if (method == null) return null;
        try {
            return invoke(method, loader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object findIndexedOwner(Class<?> loaderType, Object loader, String preferredModId) {
        Method method = findMethod(loaderType, "getIndexedModList", 0);
        if (method == null) return null;
        try {
            Object value = invoke(method, loader);
            if (!(value instanceof Map)) return null;
            Map<Object, Object> map = (Map<Object, Object>) value;
            String[] preferred = preferredModId == null || preferredModId.trim().isEmpty()
                    ? new String[] { "pixelmon", "minecraft", "forge" }
                    : new String[] { preferredModId.trim(), "pixelmon", "minecraft", "forge" };
            for (String id : preferred) {
                Object direct = map.get(id);
                if (direct != null) return direct;
                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                    if (entry.getKey() != null && id.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                        return entry.getValue();
                    }
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object findAnyOwner(Class<?> loaderType, Object loader) {
        Method method = findMethod(loaderType, "getModList", 0);
        if (method == null) return null;
        try {
            Object value = invoke(method, loader);
            if (!(value instanceof List)) return null;
            for (Object candidate : (List<Object>) value) {
                if (candidate != null) return candidate;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static String ownerId(Object owner) {
        if (owner == null) return "none";
        Method method = findMethod(owner.getClass(), "getModId", 0);
        if (method != null) {
            try {
                Object value = invoke(method, owner);
                if (value != null && !String.valueOf(value).isEmpty()) return String.valueOf(value);
            } catch (Throwable ignored) { }
        }
        return owner.getClass().getName();
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name)
                        && method.getParameterTypes().length == parameterCount) {
                    try { method.setAccessible(true); } catch (Throwable ignored) { }
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterTypes().length == parameterCount) return method;
        }
        return null;
    }

    private static Object invokeStatic(Class<?> type, String name) throws Exception {
        Method method = findMethod(type, name, 0);
        if (method == null) throw new NoSuchMethodException(type.getName() + "#" + name + "()");
        return invoke(method, null);
    }

    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getTargetException();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw ex;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Object loader;
        private final Method setActiveMethod;
        private final Object previous;
        private final Object owner;
        private final boolean changed;
        private final String ownerId;
        private boolean closed;

        private Scope(Object loader, Method setActiveMethod, Object previous,
                      Object owner, boolean changed, String ownerId) {
            this.loader = loader;
            this.setActiveMethod = setActiveMethod;
            this.previous = previous;
            this.owner = owner;
            this.changed = changed;
            this.ownerId = ownerId;
        }

        public String getOwnerId() { return ownerId; }
        public Object getOwner() { return owner; }
        public boolean isChanged() { return changed; }

        @Override
        public synchronized void close() throws Exception {
            if (closed) return;
            closed = true;
            if (changed) invoke(setActiveMethod, loader, previous);
        }
    }
}
