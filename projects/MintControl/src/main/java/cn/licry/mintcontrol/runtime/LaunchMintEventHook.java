package cn.licry.mintcontrol.runtime;

import com.pixelmonmod.pixelmon.api.events.pokemon.ItemInteractionEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/** LaunchClassLoader-side listener for native Pixelmon mint events only. */
public final class LaunchMintEventHook {
    private Object plugin;
    private Object service;
    private Method intercept;
    private Method notifyNonCancelable;
    private Method notifyHandlerError;
    private Method notifyUnsafeThread;
    private Logger logger;
    private String registrationOwner = "none";
    private String registrationBus = "none";
    private Object eventBus;
    private Method unregisterMethod;

    private final AtomicLong rawItemEvents = new AtomicLong();
    private final AtomicLong nativeMintEvents = new AtomicLong();
    private final AtomicLong callbacksInvoked = new AtomicLong();
    private final AtomicLong callbackErrors = new AtomicLong();
    private volatile String lastEventDebug = "never";

    public void register(Object plugin, Object service) throws Exception {
        this.plugin = plugin;
        this.service = service;
        this.intercept = find(service.getClass(), "intercept", 3);
        this.notifyNonCancelable = findOptional(service.getClass(), "notifyNonCancelable", 1);
        this.notifyHandlerError = findOptional(service.getClass(), "notifyHandlerError", 1);
        this.notifyUnsafeThread = findOptional(service.getClass(), "notifyUnsafeThread", 1);
        this.logger = (Logger) plugin.getClass().getMethod("getLogger").invoke(plugin);

        Class<?> pixelmon = Class.forName("com.pixelmonmod.pixelmon.Pixelmon", true, getClass().getClassLoader());
        java.lang.reflect.Field busField;
        try { busField = pixelmon.getField("EVENT_BUS"); }
        catch (NoSuchFieldException ex) { busField = pixelmon.getDeclaredField("EVENT_BUS"); busField.setAccessible(true); }
        eventBus = busField.get(null);
        if (eventBus == null) throw new IllegalStateException("Pixelmon.EVENT_BUS is null");
        Method registerMethod = findCompatible(eventBus.getClass(), "register", this);
        unregisterMethod = findCompatible(eventBus.getClass(), "unregister", this);
        if (registerMethod == null) throw new NoSuchMethodException(eventBus.getClass().getName() + "#register(Object)");
        registrationBus = "PIXELMON_EVENT_BUS";

        ForgeModOwnerContext.Scope scope = ForgeModOwnerContext.enter(pixelmon.getClassLoader(), "pixelmon");
        try {
            registrationOwner = scope.getOwnerId();
            registerMethod.invoke(eventBus, this);
        } finally {
            scope.close();
        }
        logger.info("MintControl rc17 native mint hook registered through resource-free bytecode bridge.");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onPixelmonItemInteraction(ItemInteractionEvent event) {
        rawItemEvents.incrementAndGet();
        if (event == null || event.isCanceled()) return;
        // A hot-unloaded listener can remain referenced by a broken loader/event bus.
        // Once unregister() clears the callback state, it must become completely inert.
        if (service == null || intercept == null) {
            lastEventDebug = "ignored-detached-listener";
            return;
        }

        Object nativeStack = null;
        Object player = null;
        try {
            nativeStack = event.getItemStack();
            String itemDebug = describeNativeItem(nativeStack);
            if (!isNativePixelmonMint(nativeStack, itemDebug)) {
                lastEventDebug = "ignored-non-mint; " + itemDebug;
                return;
            }
            nativeMintEvents.incrementAndGet();

            player = resolveBukkitPlayer(event);
            if (player == null) {
                cancel(event);
                lastEventDebug = "player-unresolved; " + itemDebug;
                logger.severe("Native Pixelmon mint event cancelled because Bukkit Player could not be resolved: " + itemDebug);
                return;
            }
            if (!isPrimaryThread()) {
                invokeOptional(notifyUnsafeThread, player);
                cancel(event);
                lastEventDebug = "unsafe-thread; " + itemDebug;
                return;
            }

            callbacksInvoked.incrementAndGet();
            Object result = intercept.invoke(service, player, event.getPokemon(), nativeStack);
            if (Boolean.TRUE.equals(result)) {
                if (event.isCancelable()) event.setCanceled(true);
                else invokeOptional(notifyNonCancelable, player);
            }
            lastEventDebug = "native-mint callback=" + result + "; " + itemDebug;
        } catch (Throwable ex) {
            callbackErrors.incrementAndGet();
            invokeOptional(notifyHandlerError, player);
            cancel(event);
            Throwable root = unwrap(ex);
            lastEventDebug = "callback-error=" + root + "; " + describeNativeItem(nativeStack);
            if (logger != null) logger.severe("MintControl native mint callback failed: " + root);
        }
    }

    /** Native identity: Pixelmon-owned item whose class/registry/translation/display contains mint. */
    private static boolean isNativePixelmonMint(Object nativeStack, String description) {
        if (nativeStack == null) return false;
        Object item = invokeZeroArg(nativeStack, "getItem", "func_77973_b");
        if (item == null) return false;
        String className = item.getClass().getName().toLowerCase(Locale.ROOT);
        String registry = String.valueOf(invokeZeroArg(item, "getRegistryName")).toLowerCase(Locale.ROOT);
        String translation = String.valueOf(invokeZeroArg(item, "getTranslationKey", "getUnlocalizedName", "func_77658_a"))
                .toLowerCase(Locale.ROOT);
        String display = String.valueOf(invokeZeroArg(nativeStack, "getDisplayName", "func_82833_r"))
                .toLowerCase(Locale.ROOT);
        String all = className + '|' + registry + '|' + translation + '|' + display + '|'
                + (description == null ? "" : description.toLowerCase(Locale.ROOT));
        boolean pixelmonOwned = className.startsWith("com.pixelmonmod.pixelmon.")
                || registry.startsWith("pixelmon:") || registry.contains("pixelmonmod");
        return pixelmonOwned && (all.contains("mint") || all.contains("薄荷"));
    }

    private Object resolveBukkitPlayer(ItemInteractionEvent event) {
        Object nativePlayer = safePlayer(event);
        Object direct = invokeZeroArg(nativePlayer, "getBukkitEntity");
        if (acceptsPlayer(direct)) return direct;
        Object uuidValue = invokeZeroArg(nativePlayer, "getUniqueID", "getUniqueId");
        if (!(uuidValue instanceof UUID)) return null;
        UUID uuid = (UUID) uuidValue;
        try {
            Object server = plugin.getClass().getMethod("getServer").invoke(plugin);
            Method getPlayer = findCompatible(server.getClass(), "getPlayer", uuid);
            Object byUuid = getPlayer == null ? null : getPlayer.invoke(server, uuid);
            if (acceptsPlayer(byUuid)) return byUuid;
            Object online = invokeZeroArg(server, "getOnlinePlayers");
            if (online instanceof Collection) {
                for (Object candidate : (Collection<?>) online) if (sameUuid(candidate, uuid) && acceptsPlayer(candidate)) return candidate;
            } else if (online != null && online.getClass().isArray()) {
                for (int i = 0; i < Array.getLength(online); i++) {
                    Object candidate = Array.get(online, i);
                    if (sameUuid(candidate, uuid) && acceptsPlayer(candidate)) return candidate;
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private boolean acceptsPlayer(Object candidate) {
        if (candidate == null || intercept == null) return false;
        Class<?>[] parameters = intercept.getParameterTypes();
        return parameters.length == 3 && parameters[0].isInstance(candidate);
    }

    private boolean isPrimaryThread() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, plugin.getClass().getClassLoader());
            Object result = bukkit.getMethod("isPrimaryThread").invoke(null);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable ignored) { return true; }
    }

    private static boolean sameUuid(Object candidate, UUID expected) {
        Object value = invokeZeroArg(candidate, "getUniqueId", "getUniqueID");
        return expected.equals(value);
    }

    private static String describeNativeItem(Object nativeStack) {
        if (nativeStack == null) return "stack=null";
        Object item = invokeZeroArg(nativeStack, "getItem", "func_77973_b");
        Object registry = invokeZeroArg(item, "getRegistryName");
        Object display = invokeZeroArg(nativeStack, "getDisplayName", "func_82833_r");
        Object data = invokeZeroArg(nativeStack, "getMetadata", "func_77960_j", "getItemDamage");
        return "itemClass=" + (item == null ? "null" : item.getClass().getName())
                + ", registry=" + registry + ", data=" + data + ", display=" + display;
    }

    private static Object safePlayer(ItemInteractionEvent event) {
        try { return event == null ? null : event.getPlayer(); }
        catch (Throwable ignored) { return null; }
    }

    private static void cancel(ItemInteractionEvent event) {
        if (event != null && event.isCancelable()) event.setCanceled(true);
    }

    private void invokeOptional(Method method, Object argument) {
        if (method == null || service == null) return;
        try { method.invoke(service, argument); } catch (Throwable ignored) { }
    }

    private static Object invokeZeroArg(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            Method method = findZeroArg(target.getClass(), name);
            if (method == null) continue;
            try { return method.invoke(target); } catch (Throwable ignored) { }
        }
        return null;
    }

    private static Method findZeroArg(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == 0) {
                    try { method.setAccessible(true); } catch (Throwable ignored) { }
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findOptional(Class<?> type, String name, int count) {
        try { return find(type, name, count); } catch (Throwable ignored) { return null; }
    }

    private static Method find(Class<?> type, String name, int count) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == count) {
                    method.setAccessible(true); return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(type.getName() + "#" + name + "/" + count);
    }

    private static Method findCompatible(Class<?> type, String name, Object argument) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterTypes().length != 1) continue;
                Class<?> parameter = method.getParameterTypes()[0];
                if (argument == null || wrap(parameter).isInstance(argument)) {
                    try { method.setAccessible(true); } catch (Throwable ignored) { }
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException
                && ((InvocationTargetException) current).getTargetException() != null) {
            current = ((InvocationTargetException) current).getTargetException();
        }
        return current;
    }

    public synchronized void unregister() {
        try { if (eventBus != null && unregisterMethod != null) unregisterMethod.invoke(eventBus, this); }
        catch (Throwable ignored) { }
        // Always detach every reference to the Bukkit plugin and its config/service graph.
        // Even if a third-party hot loader prevents EventBus.unregister from succeeding,
        // the stale listener is inert and cannot retain or execute old configuration.
        eventBus = null;
        unregisterMethod = null;
        service = null;
        plugin = null;
        intercept = null;
        notifyNonCancelable = null;
        notifyHandlerError = null;
        notifyUnsafeThread = null;
        logger = null;
        registrationOwner = "none";
        registrationBus = "none";
        lastEventDebug = "detached";
    }

    public String getRegistrationOwner() { return registrationOwner; }
    public String getRegistrationBus() { return registrationBus; }
    public long getRawItemEvents() { return rawItemEvents.get(); }
    public long getNativeMintEvents() { return nativeMintEvents.get(); }
    public long getCallbacksInvoked() { return callbacksInvoked.get(); }
    public long getCallbackErrors() { return callbackErrors.get(); }
    public String getLastEventDebug() { return lastEventDebug; }
}
