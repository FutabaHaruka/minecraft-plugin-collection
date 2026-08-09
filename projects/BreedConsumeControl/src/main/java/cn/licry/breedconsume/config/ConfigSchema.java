package cn.licry.breedconsume.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Canonical v3 configuration schema and one-time migration from legacy layouts. */
final class ConfigSchema {
    static final int VERSION = 3;

    private static final String[] MESSAGE_KEYS = {
            "strict-synthesis-zero-disabled",
            "strict-synthesis-minimum",
            "strict-synthesis-mismatch",
            "strict-synthesis-maximum",
            "strict-synthesis-failed",
            "strict-synthesis-success",
            "strict-item-lock-required",
            "strict-item-lock-role-mismatch",
            "strict-power-item-iv-not-perfect",
            "strict-power-item-locked",
            "strict-egg-ivs",
            "strict-nature-locked",
            "shiny-mismatch",
            "parents-consumed-on-egg-created",
            "parent-consume-failed",
            "parents-missing",
            "parent-range-synthesis-failed",
            "parent-range-synthesis-success",
            "parent-range-egg-ivs"
    };

    private ConfigSchema() {
    }

    static boolean normalizeIfNeeded(JavaPlugin plugin,
                                     File configFile,
                                     YamlConfiguration loaded,
                                     YamlConfiguration defaults) {
        List<String> duplicates = findDuplicatePaths(configFile);
        int version = loaded.getInt("config-version", 0);
        boolean legacy = version < VERSION;
        if (!legacy && duplicates.isEmpty()) return false;

        File backup = backup(configFile);
        String rendered = renderCanonical(loaded, defaults);
        replaceAtomically(configFile, rendered);

        if (legacy) {
            plugin.getLogger().warning("BreedConsumeControl migrated legacy configuration to canonical schema v"
                    + VERSION + "; backup=" + backup.getAbsolutePath());
        }
        if (!duplicates.isEmpty()) {
            plugin.getLogger().warning("BreedConsumeControl removed duplicate YAML keys: " + join(duplicates)
                    + "; backup=" + backup.getAbsolutePath());
        }
        return true;
    }

    static List<String> findDuplicatePaths(File file) {
        final List<String> duplicates = new ArrayList<String>();
        final Map<String, Integer> firstLine = new HashMap<String, Integer>();
        final Deque<Section> sections = new ArrayDeque<Section>();
        final List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to audit YAML keys: " + file.getAbsolutePath(), ex);
        }

        for (int index = 0; index < lines.size(); index++) {
            String raw = lines.get(index);
            if (index == 0 && raw.startsWith("\uFEFF")) raw = raw.substring(1);
            if (raw.indexOf('\t') >= 0) {
                throw new IllegalStateException("Tabs are forbidden in YAML indentation: line " + (index + 1));
            }
            int indent = countLeadingSpaces(raw);
            String trimmed = raw.substring(indent).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("- ")) continue;
            int colon = findYamlColon(trimmed);
            if (colon <= 0) continue;

            String key = unquote(trimmed.substring(0, colon).trim());
            if (key.isEmpty()) continue;
            while (!sections.isEmpty() && indent <= sections.peekLast().indent) sections.removeLast();

            StringBuilder path = new StringBuilder();
            for (Section section : sections) {
                if (path.length() > 0) path.append('.');
                path.append(section.key);
            }
            if (path.length() > 0) path.append('.');
            path.append(key);
            String fullPath = path.toString();

            Integer first = firstLine.put(fullPath, Integer.valueOf(index + 1));
            if (first != null) duplicates.add(fullPath + " (lines " + first + " and " + (index + 1) + ")");

            String value = stripInlineComment(trimmed.substring(colon + 1)).trim();
            if (value.isEmpty()) sections.addLast(new Section(indent, key));
        }
        return duplicates;
    }

    private static String renderCanonical(YamlConfiguration source, YamlConfiguration defaults) {
        boolean enabled = bool(source, defaults, "rules.enabled", "rules.synthesis-upgrade-mode");
        String ivMode = policy(source, defaults, "rules.iv-generation-mode", "rules.iv-generation-mode");
        boolean strict = bool(source, defaults, "rules.strict-equal-upgrade-mode", "rules.strict-equal-upgrade-mode");

        boolean zeroEnabled = bool(source, defaults, "rules.zero-v.enabled", "rules.allow-zero-v-breeding");
        int zeroResult = integer(source, defaults, "rules.zero-v.result-v", "rules.zero-v-result-v");

        int minParent = integer(source, defaults, "rules.upgrade.minimum-parent-v", "rules.minimum-upgrade-parent-v");
        int maxParent = integer(source, defaults, "rules.upgrade.maximum-parent-v", "rules.maximum-upgrade-parent-v");
        int maxResult = integer(source, defaults, "rules.upgrade.maximum-result-v", "rules.maximum-result-v");
        boolean allowMax = bool(source, defaults, "rules.upgrade.allow-maximum-v-breeding", "rules.allow-maximum-v-breeding");

        boolean allowEqual = bool(source, defaults, "rules.flexible-mode.allow-equal-parent-v", "rules.allow-equal-parent-v");
        boolean allowAdjacent = bool(source, defaults, "rules.flexible-mode.allow-adjacent-parent-v", "rules.allow-adjacent-parent-v");
        int maxGap = integer(source, defaults, "rules.flexible-mode.maximum-parent-v-difference", "rules.maximum-parent-v-difference");
        String basis = policy(source, defaults, "rules.flexible-mode.result-v-basis", "rules.result-v-basis");
        int offset = integer(source, defaults, "rules.flexible-mode.result-v-offset", "rules.result-v-offset", "rules.upgrade-step");

        boolean itemEnabled = bool(source, defaults, "rules.item-lock.enabled", "rules.item-lock.enabled");
        boolean itemZero = bool(source, defaults, "rules.item-lock.apply-to-zero-zero", "rules.item-lock.apply-to-zero-zero");
        boolean exactEverstone = bool(source, defaults, "rules.item-lock.require-exactly-one-everstone", "rules.item-lock.require-exactly-one-everstone");
        boolean requireAtLeastPower = bool(source, defaults, "rules.item-lock.require-at-least-one-power-item",
                "rules.item-lock.require-exactly-one-power-item");
        boolean allowTwoPower = bool(source, defaults, "rules.item-lock.allow-two-power-items",
                "rules.item-lock.allow-two-power-items");
        boolean different = bool(source, defaults, "rules.item-lock.require-different-parents", "rules.item-lock.require-different-parents");
        boolean perfectPower = bool(source, defaults, "rules.item-lock.require-power-item-perfect-iv", "rules.item-lock.require-power-item-perfect-iv");
        String everstoneRole = policy(source, defaults, "rules.item-lock.everstone-parent-role", "rules.item-lock.everstone-parent-role");
        String powerRole = policy(source, defaults, "rules.item-lock.power-item-parent-role", "rules.item-lock.power-item-parent-role");

        boolean displayCreate = bool(source, defaults, "rules.display.egg-ivs-on-create",
                "rules.show-egg-ivs-on-create", "rules.show-egg-ivs");
        boolean displayCollect = bool(source, defaults, "rules.display.egg-ivs-on-collect",
                "rules.show-egg-ivs-on-collect", "rules.show-egg-ivs");
        boolean displaySuccess = bool(source, defaults, "rules.display.synthesis-success",
                "rules.show-synthesis-success-message");

        boolean shiny = bool(source, defaults, "rules.shiny-only-with-shiny", "rules.shiny-only-with-shiny");
        boolean consume = bool(source, defaults, "rules.parent-consume.enabled",
                "rules.consume-parents-on-egg-created", "rules.consume-parents-after-hatch");
        boolean consumeZero = bool(source, defaults, "rules.parent-consume.consume-zero-v",
                "rules.consume-zero-v-parents");
        int delay = integer(source, defaults, "rules.parent-consume.delay-ticks", "rules.parent-consume-delay-ticks");
        boolean failClosed = bool(source, defaults, "rules.parent-consume.fail-closed",
                "rules.fail-closed-when-parent-consume-fails", "rules.fail-closed-when-tagging-fails");

        StringBuilder out = new StringBuilder(8192);
        line(out, "config-version: " + VERSION);
        line(out, "");
        line(out, "rules:");
        line(out, "  # 插件总开关。");
        line(out, "  enabled: " + enabled);
        line(out, "");
        line(out, "  # parent-range：每项IV在父母对应IV的最小值~最大值之间均匀随机（含边界）。");
        line(out, "  # exact-target-v：使用旧版目标V数量算法。");
        line(out, "  iv-generation-mode: " + quote(ivMode));
        line(out, "");
        line(out, "  # true：只允许同V父母升级，1V+1V=2V……5V+5V=6V。");
        line(out, "  strict-equal-upgrade-mode: " + strict);
        line(out, "");
        line(out, "  zero-v:");
        line(out, "    enabled: " + zeroEnabled);
        line(out, "    result-v: " + zeroResult);
        line(out, "");
        line(out, "  upgrade:");
        line(out, "    minimum-parent-v: " + minParent);
        line(out, "    maximum-parent-v: " + maxParent);
        line(out, "    maximum-result-v: " + maxResult);
        line(out, "    allow-maximum-v-breeding: " + allowMax);
        line(out, "");
        line(out, "  # 仅在 strict-equal-upgrade-mode=false 时生效。");
        line(out, "  flexible-mode:");
        line(out, "    allow-equal-parent-v: " + allowEqual);
        line(out, "    allow-adjacent-parent-v: " + allowAdjacent);
        line(out, "    maximum-parent-v-difference: " + maxGap);
        line(out, "    result-v-basis: " + quote(basis));
        line(out, "    result-v-offset: " + offset);
        line(out, "");
        line(out, "  item-lock:");
        line(out, "    # 六种力量道具（狗圈）由 Pixelmon 的 EVAdjusting 内部属性识别。");
        line(out, "    enabled: " + itemEnabled);
        line(out, "    apply-to-zero-zero: " + itemZero);
        line(out, "    # 单狗圈时是否要求另一只父母携带不变之石；双狗圈时自动不要求不变之石。");
        line(out, "    require-exactly-one-everstone: " + exactEverstone);
        line(out, "    require-at-least-one-power-item: " + requireAtLeastPower);
        line(out, "    # true：双方可各带一个狗圈，两个不同属性同时锁定。");
        line(out, "    allow-two-power-items: " + allowTwoPower);
        line(out, "    require-different-parents: " + different);
        line(out, "    require-power-item-perfect-iv: " + perfectPower);
        line(out, "    # 严格同V配对无高低V之分，实际携带道具的一侧就是锁定来源。");
        line(out, "    everstone-parent-role: " + quote(everstoneRole));
        line(out, "    power-item-parent-role: " + quote(powerRole));
        line(out, "");
        line(out, "  display:");
        line(out, "    egg-ivs-on-create: " + displayCreate);
        line(out, "    egg-ivs-on-collect: " + displayCollect);
        line(out, "    synthesis-success: " + displaySuccess);
        line(out, "");
        line(out, "  shiny-only-with-shiny: " + shiny);
        line(out, "");
        line(out, "  parent-consume:");
        line(out, "    enabled: " + consume);
        line(out, "    consume-zero-v: " + consumeZero);
        line(out, "    delay-ticks: " + delay);
        line(out, "    fail-closed: " + failClosed);
        line(out, "");
        line(out, "messages:");
        for (String key : MESSAGE_KEYS) {
            String value = string(source, defaults, "messages." + key, "messages." + key);
            line(out, "  " + key + ": " + quote(value));
        }
        return out.toString();
    }

    private static boolean bool(YamlConfiguration source, YamlConfiguration defaults,
                                String canonical, String... legacy) {
        if (source.contains(canonical)) return source.getBoolean(canonical, defaults.getBoolean(canonical, false));
        for (String path : legacy) if (source.contains(path)) return source.getBoolean(path, defaults.getBoolean(canonical, false));
        return defaults.getBoolean(canonical, false);
    }

    private static int integer(YamlConfiguration source, YamlConfiguration defaults,
                               String canonical, String... legacy) {
        if (source.contains(canonical)) return source.getInt(canonical, defaults.getInt(canonical, 0));
        for (String path : legacy) if (source.contains(path)) return source.getInt(path, defaults.getInt(canonical, 0));
        return defaults.getInt(canonical, 0);
    }

    private static String policy(YamlConfiguration source, YamlConfiguration defaults,
                                 String canonical, String... legacy) {
        return string(source, defaults, canonical, legacy).trim().toLowerCase(Locale.ROOT);
    }

    private static String string(YamlConfiguration source, YamlConfiguration defaults,
                                 String canonical, String... legacy) {
        if (source.contains(canonical)) return source.getString(canonical, defaults.getString(canonical, ""));
        for (String path : legacy) if (source.contains(path)) return source.getString(path, defaults.getString(canonical, ""));
        return defaults.getString(canonical, "");
    }

    private static File backup(File configFile) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        File parent = configFile.getParentFile();
        File target = new File(parent, "config.yml.pre-1.8.5-" + stamp + ".bak");
        int suffix = 1;
        while (target.exists()) {
            target = new File(parent, "config.yml.pre-1.8.5-" + stamp + "-" + suffix + ".bak");
            suffix++;
        }
        try {
            Files.copy(configFile.toPath(), target.toPath());
            return target;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to back up configuration before normalization", ex);
        }
    }

    private static void replaceAtomically(File configFile, String content) {
        File parent = configFile.getParentFile();
        File temp;
        try {
            temp = File.createTempFile("config-normalized-", ".tmp", parent);
            Files.write(temp.toPath(), content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temp.toPath(), configFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to replace normalized configuration", ex);
        }
    }

    private static int countLeadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') count++;
        return count;
    }

    private static int findYamlColon(String text) {
        boolean single = false;
        boolean dbl = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !dbl) single = !single;
            else if (c == '"' && !single && (i == 0 || text.charAt(i - 1) != '\\')) dbl = !dbl;
            else if (c == ':' && !single && !dbl) return i;
        }
        return -1;
    }

    private static String stripInlineComment(String text) {
        boolean single = false;
        boolean dbl = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !dbl) single = !single;
            else if (c == '"' && !single && (i == 0 || text.charAt(i - 1) != '\\')) dbl = !dbl;
            else if (c == '#' && !single && !dbl && (i == 0 || Character.isWhitespace(text.charAt(i - 1)))) {
                return text.substring(0, i);
            }
        }
        return text;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String quote(String value) {
        String safe = value == null ? "" : value.replace("'", "''").replace("\r", " ").replace("\n", "\\n");
        return "'" + safe + "'";
    }

    private static void line(StringBuilder out, String value) {
        out.append(value).append('\n');
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(values.get(i));
        }
        return out.toString();
    }

    private static final class Section {
        final int indent;
        final String key;

        Section(int indent, String key) {
            this.indent = indent;
            this.key = key;
        }
    }
}
