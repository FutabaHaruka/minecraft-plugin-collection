package HarukaEdit.exchange;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExchangeRepository {
    private final HarukaExchange plugin;
    private final File directory;
    private final Map<String, ExchangeRecipe> recipes = new LinkedHashMap<String, ExchangeRecipe>();

    public ExchangeRepository(HarukaExchange plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), "exchanges");
    }

    public void loadAll() {
        recipes.clear();
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("无法创建 exchanges 目录：" + directory.getAbsolutePath());
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase().endsWith(".yml")) continue;
            String id = file.getName().substring(0, file.getName().length() - 4);
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ExchangeRecipe recipe = new ExchangeRecipe(id);
                recipe.setDisplayName(yaml.getString("display-name", id));
                recipe.setInput1(yaml.getItemStack("input-1"));
                recipe.setInput2(yaml.getItemStack("input-2"));
                recipe.setOutput(yaml.getItemStack("output"));
                recipes.put(id.toLowerCase(), recipe);
            } catch (Throwable error) {
                plugin.getLogger().warning("读取兑换失败 " + file.getName() + ": " + error.getMessage());
            }
        }
    }

    public ExchangeRecipe get(String id) {
        return id == null ? null : recipes.get(id.toLowerCase());
    }

    public ExchangeRecipe getOrCreate(String id) {
        ExchangeRecipe recipe = get(id);
        if (recipe == null) {
            recipe = new ExchangeRecipe(id);
            recipes.put(id.toLowerCase(), recipe);
        }
        return recipe;
    }

    public boolean delete(String id) {
        ExchangeRecipe removed = recipes.remove(id.toLowerCase());
        File file = fileFor(id);
        return removed != null && (!file.exists() || file.delete());
    }

    public void save(ExchangeRecipe recipe) throws IOException {
        if (!directory.exists()) directory.mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("display-name", recipe.getDisplayName());
        yaml.set("input-1", recipe.getInput1());
        yaml.set("input-2", recipe.getInput2());
        yaml.set("output", recipe.getOutput());
        yaml.save(fileFor(recipe.getId()));
        recipes.put(recipe.getId().toLowerCase(), recipe);
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<String>();
        for (ExchangeRecipe recipe : recipes.values()) ids.add(recipe.getId());
        Collections.sort(ids, String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    private File fileFor(String id) {
        return new File(directory, id + ".yml");
    }
}
