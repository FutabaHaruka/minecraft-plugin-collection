package HarukaEdit.exchange;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

public final class LegacyImporter {
    public static final class Result {
        public int success;
        public int skipped;
    }

    private final HarukaExchange plugin;
    private final ExchangeRepository repository;

    public LegacyImporter(HarukaExchange plugin, ExchangeRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public Result importAll() throws IOException {
        Result result = new Result();
        File plugins = plugin.getDataFolder().getParentFile();
        String oldName = plugin.getConfig().getString("legacy-import.old-folder", "XinxinExchange");
        File oldFolder = new File(plugins, oldName);
        if (!oldFolder.isDirectory()) return null;

        List<File> files = new ArrayList<File>();
        if (plugin.getConfig().getBoolean("legacy-import.scan-many-data", true)) collect(new File(oldFolder, "ManyData"), files);
        if (plugin.getConfig().getBoolean("legacy-import.scan-fixed-data", true)) collect(new File(oldFolder, "FixedData"), files);

        File reportFile = new File(plugin.getDataFolder(), "migration-report.yml");
        YamlConfiguration report = new YamlConfiguration();
        int reportIndex = 1;

        for (File file : files) {
            YamlConfiguration yaml;
            try {
                yaml = YamlConfiguration.loadConfiguration(file);
            } catch (Throwable error) {
                result.skipped++;
                continue;
            }
            Set<String> roots = yaml.getKeys(false);
            for (String root : roots) {
                List<ItemStack> needs = itemList(yaml.getList(root + ".needItem"));
                List<ItemStack> outputs = itemList(yaml.getList(root + ".exItem"));
                if (needs.isEmpty() || outputs.isEmpty()) {
                    result.skipped++;
                    continue;
                }
                String source = relative(oldFolder, file);
                String id = stableId(source + "#" + root);
                ExchangeRecipe recipe = new ExchangeRecipe(id);
                recipe.setDisplayName(source + " # " + root);
                recipe.setInput1(needs.get(0));
                if (needs.size() > 1) recipe.setInput2(needs.get(1));
                recipe.setOutput(outputs.get(0));
                repository.save(recipe);

                String reportRoot = "entries." + reportIndex++;
                report.set(reportRoot + ".new-id", id);
                report.set(reportRoot + ".source", source);
                report.set(reportRoot + ".section", root);
                report.set(reportRoot + ".ignored-extra-inputs", Math.max(0, needs.size() - 2));
                report.set(reportRoot + ".ignored-extra-outputs", Math.max(0, outputs.size() - 1));
                result.success++;
            }
        }
        report.set("summary.success", result.success);
        report.set("summary.skipped", result.skipped);
        report.save(reportFile);
        return result;
    }

    private static String stableId(String source) {
        CRC32 crc = new CRC32();
        try { crc.update(source.getBytes("UTF-8")); }
        catch (java.io.UnsupportedEncodingException impossible) { crc.update(source.getBytes()); }
        return String.format("legacy_%08x", crc.getValue());
    }

    private static List<ItemStack> itemList(List<?> raw) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (raw == null) return out;
        for (Object value : raw) {
            if (value instanceof ItemStack && !ItemUtil.isEmpty((ItemStack) value)) out.add(((ItemStack) value).clone());
        }
        return out;
    }

    private static void collect(File directory, List<File> files) {
        if (!directory.isDirectory()) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collect(child, files);
            else if (child.getName().toLowerCase().endsWith(".yml")) files.add(child);
        }
    }

    private static String relative(File root, File file) {
        try { return root.toURI().relativize(file.toURI()).getPath(); }
        catch (Throwable ignored) { return file.getName(); }
    }
}
