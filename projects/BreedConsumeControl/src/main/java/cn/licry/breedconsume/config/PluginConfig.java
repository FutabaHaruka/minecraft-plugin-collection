package cn.licry.breedconsume.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Dedicated, folder-isolated configuration store. */
public final class PluginConfig {
    private static final String EXPECTED_PLUGIN_NAME = "BreedConsumeControl";
    private static final String EXPECTED_MAIN_CLASS = "cn.licry.breedconsume.BreedConsumePlugin";
    private static final String DEFAULT_CONFIG_RESOURCE = "defaults/breedconsumecontrol-1.8.5.yml";
    private static final String EXPECTED_DATA_FOLDER = "BreedConsumeControl";
    private static final String OWNER_FILE_NAME = ".plugin-owner";
    private static final String OWNER_TOKEN =
            "BreedConsumeControl\ncn.licry.breedconsume.BreedConsumePlugin\n";

    private final JavaPlugin plugin;
    private final File assignedDataFolder;
    private final File pluginsRoot;
    private final File dataFolder;
    private final File ownerFile;
    private final File configFile;
    private YamlConfiguration yaml;

    public PluginConfig(JavaPlugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("plugin");
        this.plugin = plugin;
        verifyRuntimeIdentity();

        File assigned = plugin.getDataFolder();
        if (assigned == null) throw new IllegalStateException("Bukkit returned a null plugin data folder");
        this.assignedDataFolder = normalizedAbsolute(assigned);

        this.pluginsRoot = resolvePluginsRoot(plugin, assignedDataFolder);

        // Trust only the plugins root. The Bukkit-assigned leaf can be wrong on some hybrid servers.
        File dedicated = normalizedAbsolute(new File(pluginsRoot, EXPECTED_DATA_FOLDER));
        validateDedicatedFolder(dedicated);
        this.dataFolder = canonical(dedicated);
        this.ownerFile = resolveOwnedFileInternal(OWNER_FILE_NAME);
        this.configFile = resolveOwnedFileInternal("config.yml");
        verifyOwnership();

        if (!assignedDataFolder.equals(dedicated)) {
            plugin.getLogger().warning("Bukkit assigned data folder " + assignedDataFolder
                    + "; BreedConsumeControl will ignore that leaf and use dedicated folder " + dedicated);
        }
    }

    public synchronized void reload() {
        verifyOwnership();
        ensureDefaultConfig();
        verifyOwnership();
        yaml = YamlConfiguration.loadConfiguration(configFile);
        if (yaml == null) throw new IllegalStateException("Unable to load config: " + absolute(configFile));
        YamlConfiguration defaults = loadBundledDefaults();
        if (ConfigSchema.normalizeIfNeeded(plugin, configFile, yaml, defaults)) {
            verifyOwnership();
            yaml = YamlConfiguration.loadConfiguration(configFile);
            if (yaml == null) throw new IllegalStateException("Unable to reload normalized config: " + absolute(configFile));
        }
        int schemaVersion = yaml.getInt("config-version", 0);
        if (schemaVersion != ConfigSchema.VERSION) {
            throw new IllegalStateException("Unsupported config-version=" + schemaVersion
                    + "; expected " + ConfigSchema.VERSION + ". Restore the generated canonical config or a pre-migration backup.");
        }
        plugin.getLogger().info("BreedConsumeControl config loaded from: " + absolute(configFile)
                + " (schema v" + schemaVersion + ")");
    }


    private YamlConfiguration loadBundledDefaults() {
        File temp = null;
        try {
            temp = File.createTempFile("defaults-", ".yml", dataFolder);
            verifyOwnedFile(temp);
            copyOwnBundledResource(DEFAULT_CONFIG_RESOURCE, temp);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(temp);
            if (defaults == null) throw new IllegalStateException("Unable to load bundled canonical defaults");
            return defaults;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load bundled canonical defaults", ex);
        } finally {
            if (temp != null && temp.exists() && !temp.delete()) temp.deleteOnExit();
        }
    }

    public synchronized boolean getBoolean(String path, boolean def) {
        requireLoaded();
        verifyOwnership();
        return yaml.getBoolean(path, def);
    }

    public synchronized int getInt(String path, int def) {
        requireLoaded();
        verifyOwnership();
        return yaml.getInt(path, def);
    }

    public synchronized String getString(String path, String def) {
        requireLoaded();
        verifyOwnership();
        return yaml.getString(path, def);
    }

    public synchronized boolean contains(String path) {
        requireLoaded();
        verifyOwnership();
        return yaml.contains(path);
    }

    public String getConfigPath() {
        return absolute(configFile);
    }

    public synchronized int getSchemaVersion() {
        requireLoaded();
        return yaml.getInt("config-version", 0);
    }

    public synchronized int getDuplicateKeyCount() {
        verifyOwnership();
        return ConfigSchema.findDuplicatePaths(configFile).size();
    }

    private void requireLoaded() {
        if (yaml == null) throw new IllegalStateException("Configuration has not been loaded");
    }

    private void ensureDefaultConfig() {
        verifyOwnership();
        if (dataFolder.exists() && !dataFolder.isDirectory()) {
            throw new IllegalStateException("Plugin data path is not a directory: " + absolute(dataFolder));
        }
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Unable to create plugin data folder: " + absolute(dataFolder));
        }
        verifyOwnership();
        ensureOwnerMarker();
        verifyOwnership();

        if (configFile.exists()) {
            if (!configFile.isFile()) {
                throw new IllegalStateException("Config path is not a regular file: " + absolute(configFile));
            }
            return;
        }
        File temp = null;
        try {
            temp = File.createTempFile("config-", ".tmp", dataFolder);
            verifyOwnedFile(temp);
            copyOwnBundledResource(DEFAULT_CONFIG_RESOURCE, temp);
            verifyOwnership();
            moveWithoutOverwrite(temp, configFile);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create " + absolute(configFile), ex);
        } finally {
            if (temp != null && temp.exists() && !temp.delete()) temp.deleteOnExit();
        }
    }

    private File resolveOwnedFileInternal(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("relativePath");
        }
        Path raw = new File(relativePath).toPath();
        if (raw.isAbsolute()) throw new IllegalArgumentException("Absolute paths are forbidden: " + relativePath);
        for (Path part : raw) {
            String text = part.toString();
            if ("..".equals(text) || ".".equals(text)) {
                throw new IllegalArgumentException("Dot path segments are forbidden: " + relativePath);
            }
        }
        File candidate = canonical(new File(dataFolder, relativePath));
        requireInsideRoot(candidate, "Refusing path escape from plugin data folder");
        rejectSymlinkChain(candidate);
        return candidate;
    }

    private void verifyOwnedFile(File file) {
        verifyOwnershipBase();
        File candidate = canonical(file);
        requireInsideRoot(candidate, "Refusing path outside this plugin data folder");
        rejectSymlinkChain(file);
    }

    private void verifyOwnership() {
        verifyOwnershipBase();
        if (dataFolder.exists() && !dataFolder.isDirectory()) {
            throw new IllegalStateException("Plugin data path is not a directory: " + absolute(dataFolder));
        }
        rejectSymlinkChain(dataFolder);
        if (ownerFile.exists()) validateOwnerMarker();
        if (!configFile.getParentFile().equals(dataFolder)) {
            throw new IllegalStateException("Config file is not directly inside " + EXPECTED_DATA_FOLDER + ": " + absolute(configFile));
        }
        rejectSymlinkChain(configFile);
    }

    private void verifyOwnershipBase() {
        verifyRuntimeIdentity();
        File assigned = plugin.getDataFolder();
        if (assigned == null) throw new IllegalStateException("Bukkit returned a null plugin data folder");
        File currentAssigned = normalizedAbsolute(assigned);
        File currentPluginsRoot = resolvePluginsRoot(plugin, currentAssigned);
        if (!currentPluginsRoot.equals(pluginsRoot)) {
            throw new IllegalStateException("Bukkit plugins root changed at runtime; refusing all I/O. expected="
                    + pluginsRoot + ", actual=" + currentPluginsRoot);
        }

        File expected = normalizedAbsolute(new File(pluginsRoot, EXPECTED_DATA_FOLDER));
        validateDedicatedFolder(expected);
        File currentCanonical = canonical(expected);
        if (!currentCanonical.equals(dataFolder)) {
            throw new IllegalStateException("Dedicated plugin data folder target changed at runtime; refusing all I/O. expected="
                    + absolute(dataFolder) + ", actual=" + absolute(currentCanonical));
        }
    }

    private void validateDedicatedFolder(File folder) {
        if (!EXPECTED_DATA_FOLDER.equals(folder.getName())) {
            throw new IllegalStateException("Dedicated storage violation: expected folder named '"
                    + EXPECTED_DATA_FOLDER + "' but received " + folder);
        }
        File parent = folder.getParentFile();
        if (parent == null || !canonical(parent).equals(pluginsRoot)) {
            throw new IllegalStateException("Dedicated storage violation: folder is not directly inside plugins root: " + folder);
        }
        if (Files.isSymbolicLink(folder.toPath())) {
            throw new IllegalStateException("Dedicated storage violation: plugin data folder must not be a symbolic link: " + folder);
        }
    }

    private void ensureOwnerMarker() {
        verifyOwnershipBase();
        if (ownerFile.exists()) {
            validateOwnerMarker();
            return;
        }
        File temp = null;
        try {
            temp = File.createTempFile("owner-", ".tmp", dataFolder);
            verifyOwnedFile(temp);
            Files.write(temp.toPath(), OWNER_TOKEN.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            moveWithoutOverwrite(temp, ownerFile);
            validateOwnerMarker();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to establish exclusive plugin folder ownership: " + absolute(ownerFile), ex);
        } finally {
            if (temp != null && temp.exists() && !temp.delete()) temp.deleteOnExit();
        }
    }

    private void validateOwnerMarker() {
        verifyOwnershipBase();
        rejectSymlinkChain(ownerFile);
        if (!ownerFile.isFile()) {
            throw new IllegalStateException("Plugin owner marker is not a regular file: " + absolute(ownerFile));
        }
        try {
            String actual = new String(Files.readAllBytes(ownerFile.toPath()), StandardCharsets.UTF_8);
            if (!OWNER_TOKEN.equals(actual)) {
                throw new IllegalStateException("Plugin folder belongs to another plugin or installation: "
                        + absolute(dataFolder) + "; owner marker content does not match " + EXPECTED_DATA_FOLDER);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read plugin owner marker: " + absolute(ownerFile), ex);
        }
    }

    private void requireInsideRoot(File candidate, String message) {
        Path root = dataFolder.toPath();
        Path target = candidate.toPath();
        if (!target.equals(root) && !target.startsWith(root)) {
            throw new IllegalStateException(message + ": " + target);
        }
    }

    private void rejectSymlinkChain(File candidate) {
        Path root = dataFolder.toPath();
        Path target = normalizedAbsolute(candidate).toPath();
        if (!target.equals(root) && !target.startsWith(root)) {
            throw new IllegalStateException("Path is outside dedicated plugin folder: " + target);
        }
        Path cursor = root;
        if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
            throw new IllegalStateException("Dedicated plugin folder is a symbolic link: " + cursor);
        }
        Path relative = root.relativize(target);
        for (Path part : relative) {
            cursor = cursor.resolve(part);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
                throw new IllegalStateException("Symbolic links are forbidden inside dedicated plugin folder: " + cursor);
            }
        }
    }

    private void verifyRuntimeIdentity() {
        if (!EXPECTED_MAIN_CLASS.equals(plugin.getClass().getName())) {
            throw new IllegalStateException("Plugin identity mismatch: expected main "
                    + EXPECTED_MAIN_CLASS + " but got " + plugin.getClass().getName());
        }
        String runtimeName = plugin.getDescription() == null ? null : plugin.getDescription().getName();
        if (!EXPECTED_PLUGIN_NAME.equals(runtimeName)) {
            throw new IllegalStateException("Plugin identity mismatch: expected name "
                    + EXPECTED_PLUGIN_NAME + " but got " + runtimeName);
        }
    }

    /**
     * Copies a resource from this plugin's exact code source, never through a
     * parent/global ClassLoader resource lookup. This prevents a hot-loaded
     * plugin from receiving another plugin's same-named config resource.
     */
    private void copyOwnBundledResource(String resourceName, File target) throws IOException {
        if (resourceName == null || resourceName.isEmpty() || resourceName.startsWith("/")
                || resourceName.contains("..")) {
            throw new IOException("Unsafe embedded resource path: " + resourceName);
        }
        verifyRuntimeIdentity();
        verifyOwnedFile(target);

        URL location = plugin.getClass().getProtectionDomain().getCodeSource() == null
                ? null : plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
        if (location == null) throw new IOException("Plugin code source is unavailable");
        if (!"file".equalsIgnoreCase(location.getProtocol())) {
            throw new IOException("Unsupported plugin code-source protocol: " + location);
        }

        final File codeSource;
        try {
            URI uri = location.toURI();
            codeSource = canonical(new File(uri));
        } catch (URISyntaxException ex) {
            throw new IOException("Invalid plugin code-source URL: " + location, ex);
        }

        if (codeSource.isDirectory()) {
            File sourceRoot = canonical(codeSource);
            File resource = canonical(new File(sourceRoot, resourceName));
            Path rootPath = sourceRoot.toPath();
            Path resourcePath = resource.toPath();
            if (!resourcePath.startsWith(rootPath) || !resource.isFile()) {
                throw new IOException("Embedded resource is missing from plugin classes: " + resourceName);
            }
            try (InputStream in = Files.newInputStream(resourcePath)) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return;
        }

        if (!codeSource.isFile()) {
            throw new IOException("Plugin code source is not a file or directory: " + codeSource);
        }
        try (JarFile jar = new JarFile(codeSource)) {
            JarEntry entry = jar.getJarEntry(resourceName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Embedded resource is missing from own JAR: " + resourceName);
            }
            try (InputStream in = jar.getInputStream(entry)) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static File resolvePluginsRoot(JavaPlugin plugin, File assignedDataFolder) {
        // Prefer Bukkit's update-folder location because its parent is the actual plugins root,
        // even when a hybrid server returns a nested or foreign data-folder leaf.
        try {
            Object server = plugin.getServer();
            if (server != null) {
                java.lang.reflect.Method method = server.getClass().getMethod("getUpdateFolderFile");
                if (!method.isAccessible()) method.setAccessible(true);
                Object value = method.invoke(server);
                if (value instanceof File) {
                    File updateFolder = normalizedAbsolute((File) value);
                    File parent = updateFolder.getParentFile();
                    if (parent != null) return canonical(parent);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Older/custom Bukkit implementations may not expose this method; use the safe sibling fallback.
        } catch (SecurityException ignored) {
            // Reflection may be restricted; use the safe sibling fallback.
        }

        File parent = assignedDataFolder.getParentFile();
        if (parent == null) {
            throw new IllegalStateException("Bukkit plugin data folder has no parent: " + assignedDataFolder);
        }
        return canonical(parent);
    }

    private static File normalizedAbsolute(File file) {
        return file.toPath().toAbsolutePath().normalize().toFile();
    }

    private static File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to resolve canonical path: " + file, ex);
        }
    }

    private static String absolute(File file) {
        return normalizedAbsolute(file).getPath();
    }

    private static void moveWithoutOverwrite(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException exists) {
            return;
        } catch (AtomicMoveNotSupportedException unsupported) {
            try {
                Files.move(source.toPath(), target.toPath());
            } catch (FileAlreadyExistsException exists) {
                return;
            }
        }
    }
}
