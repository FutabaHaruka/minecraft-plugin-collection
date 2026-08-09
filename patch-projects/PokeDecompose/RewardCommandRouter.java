package com.zero3.pokedecompose.reward;

import com.zero3.pokedecompose.PokeDecomposePlugin;
import com.zero3.pokedecompose.model.PokemonInfo;
import com.zero3.pokedecompose.model.PartySlot;
import com.zero3.pokedecompose.util.MessageUtil;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 最小化奖励分流：只负责分解完成后的命令与成功提示。
 *
 * 六个执行区：
 * normal、shiny、legendary、shiny_legendary、mythical、shiny_mythical。
 * 异兽沿用旧逻辑，归入 legendary 分类。
 */
public final class RewardCommandRouter {
    private static final String NORMAL = "normal";
    private static final String SHINY = "shiny";
    private static final String LEGENDARY = "legendary";
    private static final String SHINY_LEGENDARY = "shiny_legendary";
    private static final String MYTHICAL = "mythical";
    private static final String SHINY_MYTHICAL = "shiny_mythical";

    private RewardCommandRouter() {
    }

    /** 执行当前分类对应的控制台命令。 */
    public static void execute(PokeDecomposePlugin plugin, Player player,
                               String priceText, PokemonInfo info) {
        if (plugin == null || player == null) {
            return;
        }

        String zone = resolveZone(plugin, info);
        List<String> commands = selectCommands(plugin, zone);

        // 新配置缺失或为空时，最终回退原插件 Command 执行逻辑。
        if (!hasText(commands)) {
            plugin.executeRewardCommands(player, safe(priceText));
            return;
        }

        Map<String, String> placeholders = buildPlaceholders(plugin, player, priceText, info, zone);
        for (String command : commands) {
            if (command == null || command.trim().isEmpty()) {
                continue;
            }
            String parsed = replace(command, placeholders);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    /** 发送当前分类对应的分解成功提示，替代原来的统一成功提示。 */
    public static void sendSuccess(PokeDecomposePlugin plugin, Player player,
                                   String priceText, PokemonInfo info) {
        if (plugin == null || player == null) {
            return;
        }

        String zone = resolveZone(plugin, info);
        String message = selectMessage(plugin, zone);
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        String prefix = plugin.getConfig().getString("messages.prefix", "");
        Map<String, String> placeholders = buildPlaceholders(plugin, player, priceText, info, zone);
        String parsed = MessageUtil.applyPlaceholders(
                plugin, player, safe(prefix) + message, placeholders);
        if (parsed != null && !parsed.isEmpty()) {
            player.sendMessage(parsed);
        }
    }

    /**
     * 将当前分类的 GUI 奖励说明写入原 GUI 占位符 Map。
     * 只新增占位符，不修改原价格计算与确认上下文。
     */
    public static void appendGuiPlaceholders(PokeDecomposePlugin plugin,
                                             PokemonInfo info,
                                             String priceText,
                                             Map<String, String> values) {
        if (plugin == null || values == null) {
            return;
        }
        String zone = resolveZone(plugin, info);
        String reward = resolveRewardDisplay(plugin, zone, priceText, info);

        // GUI 原有 Lore 使用价格占位符。仅在 GUI Map 中将其替换成分类奖励说明，
        // 命令执行中的 %price% 仍保持原始数值，不改变旧奖励逻辑。
        values.put("%calculated_price%", safe(priceText));
        values.put("%计算价格%", safe(priceText));
        values.put("%price%", reward);
        values.put("%价格%", reward);
        values.put("{money}", reward);
        values.put("{price}", reward);
        values.put("%reward%", reward);
        values.put("%奖励%", reward);
        values.put("{reward}", reward);
        values.put("%reward_zone%", zone);
        values.put("%执行区%", zone);
    }

    private static String resolveZone(PokeDecomposePlugin plugin, PokemonInfo info) {
        boolean shiny = info != null && info.isShiny();
        boolean mythical = isMythical(plugin, info);
        if (mythical) {
            return shiny ? SHINY_MYTHICAL : MYTHICAL;
        }

        boolean legendary = info != null && (info.isLegendary() || info.isUltra());
        if (legendary) {
            return shiny ? SHINY_LEGENDARY : LEGENDARY;
        }
        return shiny ? SHINY : NORMAL;
    }

    /**
     * Pixelmon 8.4.2 的 isLegendary() 会把神兽与幻兽合并。
     * 这里仅反射读取现有 Pokemon 对象的 EnumSpecies.name()，再匹配配置名单；
     * 不修改 PokemonInfo、PixelmonCompat 或 GUI 数据结构。
     */
    private static boolean isMythical(PokeDecomposePlugin plugin, PokemonInfo info) {
        if (plugin == null || info == null) {
            return false;
        }

        String speciesId = readSpeciesId(info);
        if (speciesId.isEmpty()) {
            return false;
        }

        List<String> mythicalSpecies = plugin.getConfig()
                .getStringList("classification.mythical-species");
        for (String configured : mythicalSpecies) {
            if (configured != null
                    && speciesId.equalsIgnoreCase(configured.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String readSpeciesId(PokemonInfo info) {
        try {
            PartySlot partySlot = info.getPartySlot();
            if (partySlot == null || partySlot.getPokemon() == null) {
                return "";
            }
            Object pokemon = partySlot.getPokemon();
            Method getSpecies = pokemon.getClass().getMethod("getSpecies");
            Object species = getSpecies.invoke(pokemon);
            if (species == null) {
                return "";
            }
            if (species instanceof Enum) {
                return ((Enum<?>) species).name();
            }
            return species.toString();
        } catch (Throwable ignored) {
            // 读取失败时保持旧行为：由 isLegendary/isUltra 判断，不影响分解。
            return "";
        }
    }

    private static List<String> selectCommands(PokeDecomposePlugin plugin, String zone) {
        if (SHINY_MYTHICAL.equals(zone)) {
            List<String> value = configCommands(plugin, "ShinyMythicalCommand");
            if (hasText(value)) return value;
            value = configCommands(plugin, "MythicalCommand");
            if (hasText(value)) return value;
            value = configCommands(plugin, "ShinyLegendaryCommand");
            if (hasText(value)) return value;
            value = configCommands(plugin, "ShinyCommand");
            if (hasText(value)) return value;
            value = configCommands(plugin, "LegendaryCommand");
            return hasText(value) ? value : plugin.getRewardCommands();
        }
        if (MYTHICAL.equals(zone)) {
            List<String> value = configCommands(plugin, "MythicalCommand");
            if (hasText(value)) return value;
            value = configCommands(plugin, "LegendaryCommand");
            return hasText(value) ? value : plugin.getRewardCommands();
        }
        if (SHINY_LEGENDARY.equals(zone)) {
            List<String> value = configCommands(plugin, "ShinyLegendaryCommand");
            if (hasText(value)) return value;
            value = configCommands(plugin, "LegendaryCommand");
            if (hasText(value)) return value;
            value = configCommands(plugin, "ShinyCommand");
            return hasText(value) ? value : plugin.getRewardCommands();
        }
        if (LEGENDARY.equals(zone)) {
            List<String> value = configCommands(plugin, "LegendaryCommand");
            return hasText(value) ? value : plugin.getRewardCommands();
        }
        if (SHINY.equals(zone)) {
            List<String> value = configCommands(plugin, "ShinyCommand");
            return hasText(value) ? value : plugin.getRewardCommands();
        }
        return plugin.getRewardCommands();
    }

    private static String selectMessage(PokeDecomposePlugin plugin, String zone) {
        String key;
        if (SHINY_MYTHICAL.equals(zone)) {
            key = "messages.decompose-success-shiny-mythical";
        } else if (MYTHICAL.equals(zone)) {
            key = "messages.decompose-success-mythical";
        } else if (SHINY_LEGENDARY.equals(zone)) {
            key = "messages.decompose-success-shiny-legendary";
        } else if (LEGENDARY.equals(zone)) {
            key = "messages.decompose-success-legendary";
        } else if (SHINY.equals(zone)) {
            key = "messages.decompose-success-shiny";
        } else {
            key = "messages.decompose-success-normal";
        }

        String value = plugin.getConfig().getString(key, "");
        if (value == null || value.trim().isEmpty()) {
            value = plugin.getConfig().getString("messages.decompose-success", "");
        }
        return value;
    }

    private static List<String> configCommands(PokeDecomposePlugin plugin, String path) {
        return plugin.getConfig().getStringList(path);
    }

    private static Map<String, String> buildPlaceholders(PokeDecomposePlugin plugin,
                                                          Player player,
                                                          String priceText,
                                                          PokemonInfo info,
                                                          String zone) {
        Map<String, String> values = new HashMap<String, String>();
        String playerName = player == null ? "" : safe(player.getName());
        String price = safe(priceText);
        String pokemon = info == null ? "" : safe(info.getName());
        String type;
        if (MYTHICAL.equals(zone) || SHINY_MYTHICAL.equals(zone)) {
            type = "mythical";
        } else if (LEGENDARY.equals(zone) || SHINY_LEGENDARY.equals(zone)) {
            type = "legendary";
        } else {
            type = "normal";
        }
        String shiny = (SHINY.equals(zone)
                || SHINY_LEGENDARY.equals(zone)
                || SHINY_MYTHICAL.equals(zone)) ? "true" : "false";

        values.put("%player%", playerName);
        values.put("%price%", price);
        values.put("%pokemon%", pokemon);
        values.put("%type%", type);
        values.put("%shiny%", shiny);
        values.put("%reward_zone%", zone);

        values.put("%玩家%", playerName);
        values.put("%价格%", price);
        values.put("%宝可梦%", pokemon);
        values.put("%分类%", type);
        values.put("%闪光%", shiny);
        values.put("%执行区%", zone);

        String reward = resolveRewardDisplay(plugin, zone, price, info);
        values.put("%reward%", reward);
        values.put("%奖励%", reward);
        values.put("{reward}", reward);
        return values;
    }

    private static String resolveRewardDisplay(PokeDecomposePlugin plugin,
                                               String zone,
                                               String priceText,
                                               PokemonInfo info) {
        String value = "";
        if (plugin != null) {
            value = plugin.getConfig().getString("GuiReward." + zone, "");
        }
        if (value == null || value.trim().isEmpty()) {
            value = safe(priceText) + " 金币";
        }

        Map<String, String> base = new HashMap<String, String>();
        base.put("%price%", safe(priceText));
        base.put("%价格%", safe(priceText));
        base.put("%pokemon%", info == null ? "" : safe(info.getName()));
        base.put("%宝可梦%", info == null ? "" : safe(info.getName()));
        base.put("%reward_zone%", safe(zone));
        base.put("%执行区%", safe(zone));
        return replace(value, base);
    }

    private static String replace(String input, Map<String, String> values) {
        String result = safe(input);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static boolean hasText(List<String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
