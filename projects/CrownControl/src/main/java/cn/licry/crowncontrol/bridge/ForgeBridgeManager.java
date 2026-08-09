package cn.licry.crowncontrol.bridge;

import cn.licry.crowncontrol.util.HybridClassResolver;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Registers the Forge/Pixelmon listener by defining only the tiny runtime bridge
 * classes in Forge's LaunchClassLoader.
 *
 * <p>Forge 1.12.2 generates ASM event handlers that resolve the listener class through
 * the LaunchClassLoader. A private child loader therefore registers successfully but
 * fails later with NoClassDefFoundError when the event fires. This manager copies only
 * {@code cn.licry.crowncontrol.runtime.*} class bytes from this plugin's exact code
 * source and defines those classes directly in the LaunchClassLoader. The complete
 * plugin JAR is never appended as a URL, so plugin.yml and configuration resources are
 * not exposed through Forge's global resource lookup.</p>
 */
public final class ForgeBridgeManager {
    private static final String RUNTIME_PREFIX = "cn.licry.crowncontrol.runtime.";
    private static final String RUNTIME_PATH_PREFIX = "cn/licry/crowncontrol/runtime/";
    private static final String HOOK_CLASS = RUNTIME_PREFIX + "LaunchCrownEventHook";

    private final JavaPlugin plugin;
    private Object target;
    private String mode = "NONE";
    private String lastError = "";
    private String ownerId = "none";
    private String busName = "none";

    public ForgeBridgeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean register(Object service) {
        unregister();
        try {
            registerLaunchVisibleRuntime(service);
            mode = "RESOURCE_FREE_LAUNCH_BYTECODE_BRIDGE";
            lastError = "";
            return true;
        } catch (Throwable ex) {
            mode = "NONE";
            ownerId = "none";
            busName = "none";
            lastError = "launch-visible-runtime-bridge: " + root(ex);
            return false;
        }
    }

    private void registerLaunchVisibleRuntime(Object service) throws Exception {
        ClassLoader launch = HybridClassResolver.findLaunchClassLoader(plugin);
        if (launch == null) throw new ClassNotFoundException("net.minecraft.launchwrapper.Launch.classLoader");

        Map<String, byte[]> runtimeBytecode = readOwnRuntimeBytecode();
        if (!runtimeBytecode.containsKey(HOOK_CLASS)) {
            throw new ClassNotFoundException("Own runtime hook bytecode is missing: " + HOOK_CLASS);
        }

        Class<?> hookClass = defineRuntimeClassesInLaunch(launch, runtimeBytecode);
        Class<?> visible = Class.forName(HOOK_CLASS, false, launch);
        if (visible != hookClass) {
            throw new IllegalStateException("Runtime hook is not resolvable through LaunchClassLoader: "
                    + hookClass.getClassLoader());
        }

        Object hook = hookClass.newInstance();
        Method register = hookClass.getMethod("register", Object.class, Object.class);
        register.invoke(hook, plugin, service);
        ownerId = stringMetric(hookClass, hook, "getRegistrationOwner", "unknown");
        busName = stringMetric(hookClass, hook, "getRegistrationBus", "unknown");
        target = hook;
    }

    /**
     * Defines only the runtime bridge classes in Forge's loader. No plugin JAR URL or
     * resource path is attached to the loader.
     */
    private static Class<?> defineRuntimeClassesInLaunch(ClassLoader launch,
                                                          Map<String, byte[]> definitions) throws Exception {
        Method defineClass = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, byte[].class, int.class, int.class);
        defineClass.setAccessible(true);

        java.util.List<String> names = new java.util.ArrayList<String>(definitions.keySet());
        Collections.sort(names);
        synchronized (launch) {
            for (String name : names) {
                if (isVisible(name, launch)) continue;
                byte[] bytes = definitions.get(name);
                try {
                    defineClass.invoke(launch, name, bytes, 0, bytes.length);
                } catch (InvocationTargetException ex) {
                    Throwable cause = ex.getTargetException();
                    if (!(cause instanceof LinkageError) || !isVisible(name, launch)) throw ex;
                }
                if (!isVisible(name, launch)) {
                    throw new ClassNotFoundException("Defined runtime class is not launch-visible: " + name);
                }
            }
        }
        return Class.forName(HOOK_CLASS, true, launch);
    }

    private static boolean isVisible(String name, ClassLoader loader) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /** Reads only runtime class bytes from the exact JAR/classes directory that defined this main plugin. */
    private Map<String, byte[]> readOwnRuntimeBytecode() throws IOException {
        URL location = plugin.getClass().getProtectionDomain().getCodeSource() == null
                ? null : plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
        if (location == null) throw new IOException("Plugin code source is unavailable");
        if (!"file".equalsIgnoreCase(location.getProtocol())) {
            throw new IOException("Unsupported plugin code-source protocol: " + location);
        }

        final File codeSource;
        try {
            URI uri = location.toURI();
            codeSource = new File(uri).getCanonicalFile();
        } catch (URISyntaxException ex) {
            throw new IOException("Invalid plugin code-source URL: " + location, ex);
        }

        Map<String, byte[]> classes = new LinkedHashMap<String, byte[]>();
        if (codeSource.isDirectory()) {
            File runtimeRoot = new File(codeSource, RUNTIME_PATH_PREFIX).getCanonicalFile();
            if (!runtimeRoot.toPath().startsWith(codeSource.toPath()) || !runtimeRoot.isDirectory()) {
                throw new IOException("Runtime classes directory is missing: " + runtimeRoot);
            }
            collectDirectoryClasses(codeSource, runtimeRoot, classes);
        } else if (codeSource.isFile()) {
            readJarClasses(codeSource, classes);
        } else {
            throw new IOException("Plugin code source is not a file or directory: " + codeSource);
        }

        if (classes.isEmpty()) throw new IOException("No CrownControl runtime bridge classes were found in own code source");
        return Collections.unmodifiableMap(classes);
    }

    private static void collectDirectoryClasses(File codeSource, File directory,
                                                Map<String, byte[]> output) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("Unable to list runtime classes directory: " + directory);
        for (File file : files) {
            File canonical = file.getCanonicalFile();
            if (!canonical.toPath().startsWith(codeSource.toPath())) {
                throw new IOException("Runtime class path escaped plugin code source: " + canonical);
            }
            if (canonical.isDirectory()) {
                collectDirectoryClasses(codeSource, canonical, output);
            } else if (canonical.isFile() && canonical.getName().endsWith(".class")) {
                String relative = codeSource.toPath().relativize(canonical.toPath()).toString().replace(File.separatorChar, '/');
                if (!relative.startsWith(RUNTIME_PATH_PREFIX) || relative.contains("..")) {
                    throw new IOException("Unsafe runtime class path: " + relative);
                }
                output.put(toClassName(relative), Files.readAllBytes(canonical.toPath()));
            }
        }
    }

    private static void readJarClasses(File jarFile, Map<String, byte[]> output) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(RUNTIME_PATH_PREFIX)
                        || !name.endsWith(".class") || name.contains("..")) continue;
                try (InputStream in = jar.getInputStream(entry)) {
                    output.put(toClassName(name), readFully(in));
                }
            }
        }
    }

    private static String toClassName(String resourcePath) {
        return resourcePath.substring(0, resourcePath.length() - ".class".length()).replace('/', '.');
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            if (read > 0) out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String stringMetric(Class<?> hookClass, Object hook, String method, String fallback) {
        try {
            Object value = hookClass.getMethod(method).invoke(hook);
            return value == null ? fallback : String.valueOf(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public synchronized void unregister() {
        Object oldTarget = target;
        target = null;
        try {
            if (oldTarget != null) {
                Method method = oldTarget.getClass().getMethod("unregister");
                method.invoke(oldTarget);
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Unable to unregister Forge listener: " + root(ex));
        } finally {
            mode = "NONE";
            ownerId = "none";
            busName = "none";
        }
    }

    private static String root(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException
                && ((InvocationTargetException) current).getTargetException() != null) {
            current = ((InvocationTargetException) current).getTargetException();
        }
        return current.getClass().getSimpleName() + ": " + String.valueOf(current.getMessage());
    }

    private long longMetric(String method) {
        Object current = target;
        if (current == null) return 0L;
        try {
            Object value = current.getClass().getMethod(method).invoke(current);
            return value instanceof Number ? ((Number) value).longValue() : 0L;
        } catch (Throwable ignored) { return 0L; }
    }

    private String stringMetric(String method) {
        Object current = target;
        if (current == null) return "never";
        try {
            Object value = current.getClass().getMethod(method).invoke(current);
            return value == null ? "never" : String.valueOf(value);
        } catch (Throwable ignored) { return "unavailable"; }
    }

    public long getRawItemEvents() { return longMetric("getRawItemEvents"); }
    public long getNativeCrownEvents() { return longMetric("getNativeCrownEvents"); }
    public long getCallbacksInvoked() { return longMetric("getCallbacksInvoked"); }
    public long getCallbackErrors() { return longMetric("getCallbackErrors"); }
    public String getLastEventDebug() { return stringMetric("getLastEventDebug"); }

    public String getMode() { return mode; }
    public String getLastError() { return lastError; }
    public String getOwnerId() { return ownerId; }
    public String getBusName() { return busName; }
    public boolean isRegistered() { return target != null; }

}
