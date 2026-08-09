package cn.licry.mintcontrol.config;

import cn.licry.mintcontrol.model.ConsumeOn;
import cn.licry.mintcontrol.model.CooldownOn;
import cn.licry.mintcontrol.model.GlobalRule;
import cn.licry.mintcontrol.model.PokemonCategory;
import cn.licry.mintcontrol.model.PokemonView;
import cn.licry.mintcontrol.util.SpeciesNames;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
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
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dedicated MintControl configuration store.
 *
 * Every writable path is anchored to a fixed plugin-owned folder under the parent plugins directory.
 * The plugin ignores an incorrect Bukkit-assigned leaf folder, never scans sibling contents,
 * and refuses writes after a plugins-root change, symlink substitution or path escape.
 */
public final class PluginConfig {
    private static final String EXPECTED_PLUGIN_NAME = "MintControl";
    private static final String EXPECTED_MAIN_CLASS = "cn.licry.mintcontrol.MintControlPlugin";
    private static final String DEFAULT_CONFIG_RESOURCE = "META-INF/mintcontrol/defaults.rc17";
    private static final String POKEMON_LIST_ROOT = "pokemon-lists";
    private static final String LEGACY_LIST_ROOT = "global-lists";
    private static final String GLOBAL_RULE_ROOT = "rules";
    private static final String EXPECTED_DATA_FOLDER = "MintControl";
    private static final String OWNER_TOKEN = "MintControl\ncn.licry.mintcontrol.MintControlPlugin\n";
    private static final String OWNER_FILE_NAME = ".plugin-owner";

    private final JavaPlugin plugin;
    private final File assignedDataFolder;
    private final File pluginsRoot;
    private final File dataFolder;
    private final File ownerFile;
    private final File configFile;
    private YamlConfiguration yaml;

    private GlobalRule globalRule = invalidGlobalRule("rules section has not been loaded");
    private Set<String> globalBlacklist = Collections.emptySet();
    private Set<String> globalWhitelist = Collections.emptySet();
    private Set<String> mythical = Collections.emptySet();
    private Set<String> ultraBeast = Collections.emptySet();
    private Set<String> legendaryFallback = Collections.emptySet();

    public PluginConfig(JavaPlugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("plugin");
        this.plugin = plugin;
        verifyRuntimeIdentity();

        File assigned = plugin.getDataFolder();
        if (assigned == null) throw new IllegalStateException("Bukkit returned a null plugin data folder");
        this.assignedDataFolder = normalizedAbsolute(assigned);

        this.pluginsRoot = resolvePluginsRoot(plugin, assignedDataFolder);

        // CatServer may occasionally expose the wrong leaf folder to a hybrid plugin.
        // Only trust the parent plugins directory, then resolve our own immutable leaf name.
        File dedicated = normalizedAbsolute(new File(pluginsRoot, EXPECTED_DATA_FOLDER));
        validateDedicatedFolder(dedicated);
        this.dataFolder = canonical(dedicated);

        this.ownerFile = resolveOwnedFileInternal(OWNER_FILE_NAME);
        this.configFile = resolveOwnedFileInternal("config.yml");
        verifyOwnership();

        if (!assignedDataFolder.equals(dedicated)) {
            plugin.getLogger().warning("Bukkit assigned data folder " + assignedDataFolder
                    + "; MintControl will ignore that leaf and use dedicated folder " + dedicated);
        }
    }

    public synchronized void reload() {
        verifyOwnership();
        ensureDefaultConfig();
        verifyOwnership();
        yaml = YamlConfiguration.loadConfiguration(configFile);
        migrateLegacySettings();

        globalBlacklist = normalized(yaml.getStringList(POKEMON_LIST_ROOT + ".blacklist"));
        globalWhitelist = normalized(yaml.getStringList(POKEMON_LIST_ROOT + ".whitelist"));
        mythical = normalized(yaml.getStringList("categories.mythical"));
        ultraBeast = normalized(yaml.getStringList("categories.ultra-beast"));
        legendaryFallback = normalized(yaml.getStringList("categories.legendary-fallback"));
        globalRule = parseGlobalRule(yaml.getConfigurationSection(GLOBAL_RULE_ROOT));

        plugin.getLogger().info("MintControl config loaded from: " + absolute(configFile));
        plugin.getLogger().info("Native Pixelmon mints need no item definitions; blacklist="
                + globalBlacklist.size() + ", whitelist=" + globalWhitelist.size() + '.');
        if (!globalRule.isValid()) {
            plugin.getLogger().severe("Global mint rule is invalid: " + globalRule.getValidationError());
        }
    }

    private void ensureDefaultConfig() {
        verifyOwnership();
        if (dataFolder.exists() && !dataFolder.isDirectory()) {
            throw new IllegalStateException("MintControl data path is not a directory: " + absolute(dataFolder));
        }
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Unable to create MintControl data folder: " + absolute(dataFolder));
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

    private void migrateLegacySettings() {
        boolean changed = false;
        if (!yaml.isConfigurationSection(POKEMON_LIST_ROOT)
                && yaml.isConfigurationSection(LEGACY_LIST_ROOT)) {
            yaml.set(POKEMON_LIST_ROOT + ".blacklist", yaml.getStringList(LEGACY_LIST_ROOT + ".blacklist"));
            yaml.set(POKEMON_LIST_ROOT + ".whitelist", yaml.getStringList(LEGACY_LIST_ROOT + ".whitelist"));
            yaml.set(POKEMON_LIST_ROOT + ".op-bypasses-blacklist", true);
            changed = true;
        }

        // rc13 no longer recognises configured/custom mint items and no longer consumes NBT items.
        if (yaml.contains("mints")) { yaml.set("mints", null); changed = true; }
        if (yaml.contains(GLOBAL_RULE_ROOT + ".consume-mint")) {
            yaml.set(GLOBAL_RULE_ROOT + ".consume-mint", null); changed = true;
        }
        if (yaml.contains(GLOBAL_RULE_ROOT + ".costs.items")) {
            yaml.set(GLOBAL_RULE_ROOT + ".costs.items", null); changed = true;
        }
        if (yaml.contains("settings.warn-unmatched-mint-like-items")) {
            yaml.set("settings.warn-unmatched-mint-like-items", null); changed = true;
        }
        if (yaml.contains("settings.unmatched-warning-cooldown-seconds")) {
            yaml.set("settings.unmatched-warning-cooldown-seconds", null); changed = true;
        }

        if (changed) {
            backupConfigOnce();
            save();
            plugin.getLogger().warning("Migrated rc11 configuration: removed mints, consume-mint and costs.items. "
                    + "Only native Pixelmon mints plus money/points are supported.");
        }
    }

    private void backupConfigOnce() {
        File backup = resolveOwnedFile("config.rc11-before-rc13.yml");
        if (backup.exists() || !configFile.isFile()) return;
        try {
            verifyOwnership();
            Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException ex) {
            plugin.getLogger().warning("Unable to create rc11 config backup: " + ex.getMessage());
        }
    }

    private GlobalRule parseGlobalRule(ConfigurationSection sec) {
        if (sec == null) return invalidGlobalRule("missing rules section");
        List<String> errors = new ArrayList<String>();
        Set<PokemonCategory> categories = EnumSet.noneOf(PokemonCategory.class);
        for (String value : sec.getStringList("allowed-categories")) {
            PokemonCategory parsed = PokemonCategory.parseOrNull(value);
            if (parsed == null) errors.add("unknown category: " + value);
            else categories.add(parsed);
        }
        if (categories.isEmpty()) errors.add("rules.allowed-categories must contain at least one valid category");

        double success = sec.getDouble("chance.success-rate", 100.0D);
        double failure = sec.contains("chance.failure-rate")
                ? sec.getDouble("chance.failure-rate") : 100.0D - success;
        if (success < 0.0D || success > 100.0D || failure < 0.0D || failure > 100.0D
                || Math.abs(success + failure - 100.0D) > 0.0001D) {
            errors.add("success-rate/failure-rate must each be 0..100 and sum to 100");
        }
        if (success > 0.0D && success < 1.0D && warnFractionalProbability()) {
            plugin.getLogger().warning("success-rate=" + success + " means " + success + "%, not "
                    + (success * 100.0D) + "%.");
        }

        double money = sec.getDouble("costs.money", 0.0D);
        int points = sec.getInt("costs.points", 0);
        int cooldownSeconds = sec.getInt("cooldown-seconds", 0);
        if (money < 0.0D) errors.add("rules.costs.money must be >= 0");
        if (points < 0) errors.add("rules.costs.points must be >= 0");
        if (cooldownSeconds < 0) errors.add("rules.cooldown-seconds must be >= 0");

        CooldownOn cooldownOn = CooldownOn.parseOrNull(sec.getString("cooldown-on", "SUCCESS"));
        if (cooldownOn == null) { errors.add("unknown rules.cooldown-on"); cooldownOn = CooldownOn.SUCCESS; }
        ConsumeOn consumeOn = ConsumeOn.parseOrNull(sec.getString("chance.consume-on", "ATTEMPT"));
        if (consumeOn == null) { errors.add("unknown rules.chance.consume-on"); consumeOn = ConsumeOn.ATTEMPT; }

        return new GlobalRule(sec.getBoolean("enabled", true), cooldownSeconds, cooldownOn,
                categories, money, points, success, failure, consumeOn,
                errors.isEmpty() ? null : join(errors, "; "));
    }

    private static GlobalRule invalidGlobalRule(String error) {
        return new GlobalRule(false, 0, CooldownOn.SUCCESS,
                EnumSet.allOf(PokemonCategory.class), 0.0D, 0,
                0.0D, 100.0D, ConsumeOn.ATTEMPT, error);
    }

    public synchronized boolean addPokemonListEntry(boolean blacklist, String species) {
        String normalized = SpeciesNames.normalize(species);
        if (normalized.isEmpty()) return false;
        Set<String> current = blacklist ? globalBlacklist : globalWhitelist;
        if (current.contains(normalized)) return false;
        String path = POKEMON_LIST_ROOT + (blacklist ? ".blacklist" : ".whitelist");
        List<String> entries = new ArrayList<String>(yaml.getStringList(path));
        entries.add(species.trim());
        yaml.set(path, entries); save(); reload(); return true;
    }

    public synchronized boolean removePokemonListEntry(boolean blacklist, String species) {
        String normalized = SpeciesNames.normalize(species);
        if (normalized.isEmpty()) return false;
        String path = POKEMON_LIST_ROOT + (blacklist ? ".blacklist" : ".whitelist");
        List<String> entries = new ArrayList<String>(yaml.getStringList(path));
        boolean removed = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (SpeciesNames.normalize(entries.get(i)).equals(normalized)) { entries.remove(i); removed = true; }
        }
        if (!removed) return false;
        yaml.set(path, entries); save(); reload(); return true;
    }

    public synchronized boolean clearPokemonList(boolean blacklist) {
        String path = POKEMON_LIST_ROOT + (blacklist ? ".blacklist" : ".whitelist");
        if (yaml.getStringList(path).isEmpty()) return false;
        yaml.set(path, new ArrayList<String>()); save(); reload(); return true;
    }

    private void save() {
        verifyOwnership();
        File temp = null;
        try {
            temp = File.createTempFile("config-save-", ".tmp", dataFolder);
            verifyOwnedFile(temp);
            yaml.save(temp);
            verifyOwnership();
            moveReplacing(temp, configFile);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save " + absolute(configFile), ex);
        } finally {
            if (temp != null && temp.exists() && !temp.delete()) temp.deleteOnExit();
        }
    }

    public File resolveOwnedFile(String relativePath) {
        verifyOwnership();
        return resolveOwnedFileInternal(relativePath);
    }

    public File ensureOwnedDirectory(String relativePath) {
        verifyOwnership();
        File dir = resolveOwnedFileInternal(relativePath);
        if (dir.exists()) {
            if (!dir.isDirectory()) throw new IllegalStateException("Owned path is not a directory: " + absolute(dir));
            rejectSymlinkChain(dir);
            return dir;
        }
        try {
            Files.createDirectories(dir.toPath());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create owned directory: " + absolute(dir), ex);
        }
        verifyOwnedFile(dir);
        if (!dir.isDirectory()) throw new IllegalStateException("Owned directory was not created: " + absolute(dir));
        return dir;
    }

    public BufferedWriter openOwnedAppendWriter(String relativePath) throws IOException {
        verifyOwnership();
        File file = resolveOwnedFileInternal(relativePath);
        File parent = file.getParentFile();
        if (parent == null) throw new IllegalStateException("Owned file has no parent: " + absolute(file));
        String relativeParent = dataFolder.toPath().relativize(parent.toPath()).toString();
        if (!relativeParent.isEmpty()) ensureOwnedDirectory(relativeParent);
        verifyOwnedFile(file);
        OpenOption[] options = new OpenOption[] {
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND,
                LinkOption.NOFOLLOW_LINKS
        };
        return Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8, options);
    }

    public void verifyOwnedFile(File file) {
        verifyOwnershipBase();
        File candidate = canonical(file);
        requireInsideRoot(candidate, "Refusing path outside this plugin data folder");
        rejectSymlinkChain(file);
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
        if (ownerFile.exists()) { validateOwnerMarker(); return; }
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
        if (!ownerFile.isFile()) throw new IllegalStateException("Plugin owner marker is not a regular file: " + absolute(ownerFile));
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
            throw new IllegalStateException("Path is outside strict plugin folder: " + target);
        }
        Path cursor = root;
        if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
            throw new IllegalStateException("Strict plugin folder is a symbolic link: " + cursor);
        }
        Path relative = root.relativize(target);
        for (Path part : relative) {
            cursor = cursor.resolve(part);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
                throw new IllegalStateException("Symbolic links are forbidden inside strict plugin folder: " + cursor);
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

    private static void moveReplacing(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static File canonical(File file) {
        try { return file.getCanonicalFile(); }
        catch (IOException ex) { throw new IllegalStateException("Unable to resolve canonical path: " + file, ex); }
    }

    private static Set<String> normalized(Collection<String> values) {
        Set<String> out = new LinkedHashSet<String>();
        if (values != null) for (String value : values) {
            String normalized = SpeciesNames.normalize(value);
            if (!normalized.isEmpty()) out.add(normalized);
        }
        return Collections.unmodifiableSet(out);
    }

    private static String join(List<String> values, String delimiter) {
        StringBuilder out = new StringBuilder();
        for (String value : values) { if (out.length() > 0) out.append(delimiter); out.append(value); }
        return out.toString();
    }

    private static String absolute(File file) {
        try { return file.getCanonicalPath(); }
        catch (IOException ignored) { return file.getAbsolutePath(); }
    }

    public boolean isGlobalBlacklisted(PokemonView pokemon) { return pokemon != null && pokemon.matchesSpecies(globalBlacklist); }
    public boolean isGlobalWhitelisted(PokemonView pokemon) { return pokemon != null && pokemon.matchesSpecies(globalWhitelist); }
    public GlobalRule getGlobalRule() { return globalRule; }
    public Set<String> getGlobalBlacklist() { return globalBlacklist; }
    public Set<String> getGlobalWhitelist() { return globalWhitelist; }
    public Set<String> getMythical() { return mythical; }
    public Set<String> getUltraBeast() { return ultraBeast; }
    public Set<String> getLegendaryFallback() { return legendaryFallback; }
    public File getDataFolder() { return dataFolder; }
    public File getConfigFile() { return configFile; }
    public String getConfigPath() { return absolute(configFile); }

    public boolean opBypassesPokemonBlacklist() { return yaml.getBoolean(POKEMON_LIST_ROOT + ".op-bypasses-blacklist", true); }
    public boolean allowEggs() { return yaml.getBoolean("settings.allow-eggs", false); }
    public boolean auditLog() { return yaml.getBoolean("settings.audit-log", true); }
    public boolean pointsRequired() { return yaml.getBoolean("points.required", false); }
    public int nativeVerificationTicks() { return Math.max(1, yaml.getInt("settings.native-verification-ticks", 1)); }
    public boolean warnFractionalProbability() { return yaml.getBoolean("settings.warn-fractional-probability", true); }
    public boolean usesMoneyCosts() { return globalRule != null && globalRule.isEnabled() && globalRule.isValid() && globalRule.getMoney() > 0.0D; }
    public boolean usesPointCosts() { return globalRule != null && globalRule.isEnabled() && globalRule.isValid() && globalRule.getPoints() > 0; }

    public String message(String key) {
        String prefix = yaml.getString("messages.prefix", "");
        String value = yaml.getString("messages." + key, key);
        return color(prefix + value);
    }

    public static String color(String input) { return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input); }
}
