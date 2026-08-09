package cn.licry.crowncontrol.bridge;

import cn.licry.crowncontrol.config.PluginConfig;
import cn.licry.crowncontrol.model.PokemonCategory;
import cn.licry.crowncontrol.model.PokemonView;
import cn.licry.crowncontrol.util.HybridClassResolver;
import cn.licry.crowncontrol.util.Reflect;
import cn.licry.crowncontrol.util.SpeciesNames;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Read-only Pixelmon bridge used by the native BottleCapEvent interceptor.
 * CrownControl never writes IVs or hyper-training flags; Pixelmon remains the only
 * component that applies Gold/Silver Bottle Cap effects.
 */
public final class PixelmonBridge {
    private final JavaPlugin plugin;
    private final PluginConfig config;
    private Method getPlayerStorage;
    private boolean nativeEventAvailable;
    private boolean partyLookupAvailable;
    private String lastError = "";
    private String eventClassLoader = "";

    public PixelmonBridge(JavaPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean initialize() {
        nativeEventAvailable = false;
        partyLookupAvailable = false;
        getPlayerStorage = null;
        lastError = "";
        eventClassLoader = "";
        try {
            Class<?> eventClass = HybridClassResolver.load(plugin,
                    "com.pixelmonmod.pixelmon.api.events.pokemon.BottleCapEvent");
            HybridClassResolver.load(plugin, "com.pixelmonmod.pixelmon.api.pokemon.Pokemon");
            nativeEventAvailable = true;
            eventClassLoader = HybridClassResolver.describe(eventClass.getClassLoader());
            plugin.getLogger().info("Pixelmon BottleCapEvent resolved through " + eventClassLoader + ".");
        } catch (Throwable ex) {
            nativeEventAvailable = false;
            lastError = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
            plugin.getLogger().severe("Pixelmon BottleCapEvent is unavailable across plugin/server/Launch classloaders: "
                    + lastError);
        }

        try {
            Class<?> commandClass = HybridClassResolver.load(plugin,
                    "com.pixelmonmod.pixelmon.api.command.PixelmonCommand");
            getPlayerStorage = Reflect.findMethod(commandClass, "getPlayerStorage", 1);
            partyLookupAvailable = getPlayerStorage != null;
            if (!partyLookupAvailable) {
                plugin.getLogger().warning("PixelmonCommand#getPlayerStorage was not found; /crowncontrol check is unavailable; native crown interception still works.");
            }
        } catch (Throwable ex) {
            partyLookupAvailable = false;
            plugin.getLogger().warning("Party lookup bridge unavailable; native crown interception still works: " + ex.getMessage());
        }
        return nativeEventAvailable;
    }

    public boolean isNativeEventAvailable() { return nativeEventAvailable; }
    public boolean isPartyLookupAvailable() { return partyLookupAvailable; }
    public String getLastError() { return lastError; }
    public String getEventClassLoader() { return eventClassLoader; }

    public PokemonView fromNativePokemon(Object pokemon) throws Exception {
        return toView(-1, null, pokemon);
    }

    public PokemonView getPokemon(Player player, int slot) throws Exception {
        if (!partyLookupAvailable || slot < 0 || slot > 5) return null;
        Object storage = getStorage(player);
        if (storage == null) return null;
        Method get = Reflect.findCompatibleMethod(storage.getClass(), "get", slot);
        if (get == null) throw new NoSuchMethodException(storage.getClass().getName() + "#get(int)");
        Object pokemon = get.invoke(storage, slot);
        return pokemon == null ? null : toView(slot, storage, pokemon);
    }

    public String currentNature(Object pokemon) {
        Object value = invokeOptional(pokemon, "getNature");
        return enumName(value);
    }

    /**
     * Returns a defensive copy of the six Pixelmon hyper-training flags.
     *
     * Pixelmon 8.4.2 stores the flags in a private BitSet and exposes them only
     * through IVStore#isHyperTrained(StatsType). Earlier CrownControl builds
     * incorrectly looked for getHypertrainedArray(), which does not exist in
     * this runtime and caused every otherwise-valid crown event to be cancelled.
     */
    public boolean[] currentHyperTraining(Object pokemon) {
        Object ivs = invokeOptional(pokemon, "getIVs");
        if (ivs == null) return null;

        // Compatibility path for builds that expose a direct array accessor.
        Object value = invokeOptional(ivs, "getHypertrainedArray");
        if (value == null) value = invokeOptional(ivs, "getHyperTrainedArray");
        boolean[] direct = toBooleanArray(value);
        if (direct != null && direct.length > 0) return normalizeSix(direct);

        // Pixelmon 8.4.2 path: read HP/Attack/Defence/SpA/SpD/Speed one by one.
        Method isHyperTrained = Reflect.findMethod(ivs.getClass(), "isHyperTrained", 1);
        if (isHyperTrained == null) return null;
        Class<?> statsType = isHyperTrained.getParameterTypes()[0];
        Object[] stats = resolveSixStats(statsType);
        if (stats == null || stats.length != 6) return null;

        boolean[] out = new boolean[6];
        try {
            for (int i = 0; i < stats.length; i++) {
                Object result = isHyperTrained.invoke(ivs, stats[i]);
                if (!(result instanceof Boolean)) return null;
                out[i] = (Boolean) result;
            }
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object[] resolveSixStats(Class<?> statsType) {
        if (statsType == null) return null;
        try {
            Method getStatValues = Reflect.findMethod(statsType, "getStatValues", 0);
            if (getStatValues != null) {
                Object value = getStatValues.invoke(null);
                if (value != null && value.getClass().isArray() && Array.getLength(value) >= 6) {
                    Object[] out = new Object[6];
                    for (int i = 0; i < 6; i++) out[i] = Array.get(value, i);
                    return out;
                }
            }
        } catch (Throwable ignored) { }

        String[] names = {"HP", "Attack", "Defence", "SpecialAttack", "SpecialDefence", "Speed"};
        Object[] out = new Object[names.length];
        for (int i = 0; i < names.length; i++) {
            out[i] = Reflect.enumConstant(statsType, names[i]);
            if (out[i] == null) return null;
        }
        return out;
    }

    private static boolean[] toBooleanArray(Object value) {
        if (value instanceof boolean[]) return ((boolean[]) value).clone();
        if (value == null || !value.getClass().isArray()) return null;
        int length = Array.getLength(value);
        boolean[] out = new boolean[length];
        for (int i = 0; i < length; i++) {
            Object entry = Array.get(value, i);
            if (!(entry instanceof Boolean)) return null;
            out[i] = (Boolean) entry;
        }
        return out;
    }

    private static boolean[] normalizeSix(boolean[] source) {
        if (source == null || source.length < 6) return null;
        boolean[] out = new boolean[6];
        System.arraycopy(source, 0, out, 0, 6);
        return out;
    }

    public String currentHyperTrainingText(Object pokemon) {
        return formatHyperTraining(currentHyperTraining(pokemon));
    }

    public static String formatHyperTraining(boolean[] flags) {
        if (flags == null) return "unavailable";
        String[] names = {"HP", "ATK", "DEF", "SPA", "SPD", "SPE"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < flags.length; i++) {
            if (out.length() > 0) out.append('|');
            out.append(i < names.length ? names[i] : String.valueOf(i)).append('=').append(flags[i] ? '1' : '0');
        }
        return out.toString();
    }

    private Object getStorage(Player player) throws Exception {
        Method getHandle = player.getClass().getMethod("getHandle");
        Object handle = getHandle.invoke(player);
        return getPlayerStorage.invoke(null, handle);
    }

    private PokemonView toView(int slot, Object storage, Object pokemon) throws Exception {
        if (pokemon == null) return null;
        Object speciesObject = invokeNoArg(pokemon, "getSpecies");
        String species = speciesInternalName(speciesObject);
        String display = stringValue(invokeOptional(pokemon, "getDisplayName"), species);
        String nature = enumName(invokeOptional(pokemon, "getNature"));
        int level = intValue(invokeOptional(pokemon, "getLevel"), 0);
        boolean egg = boolValue(invokeOptional(pokemon, "isEgg"), false);
        PokemonCategory category = detectCategory(pokemon, speciesObject, species);
        return new PokemonView(slot, storage, pokemon, species, display, nature, level, egg,
                category, speciesKeys(speciesObject, species));
    }

    private PokemonCategory detectCategory(Object pokemon, Object speciesObject, String species) {
        String key = SpeciesNames.normalize(species);
        if (config.getMythical().contains(key)) return PokemonCategory.MYTHICAL;
        if (config.getUltraBeast().contains(key)) return PokemonCategory.ULTRA_BEAST;
        if (booleanMethod(speciesObject, "isUltraBeast")) return PokemonCategory.ULTRA_BEAST;
        if (booleanMethod(pokemon, "isLegendary")) return PokemonCategory.LEGENDARY;
        if (booleanMethod(speciesObject, "isLegendary")) return PokemonCategory.LEGENDARY;
        Object baseStats = invokeOptional(pokemon, "getBaseStats");
        if (booleanMethod(baseStats, "isLegendary")) return PokemonCategory.LEGENDARY;
        if (config.getLegendaryFallback().contains(key)) return PokemonCategory.LEGENDARY;
        return PokemonCategory.NORMAL;
    }

    private static String speciesInternalName(Object species) {
        if (species == null) return "Unknown";
        if (species instanceof Enum) return ((Enum<?>) species).name();
        Object value = invokeOptional(species, "getPokemonName");
        return value == null ? species.toString() : String.valueOf(value);
    }

    private static Set<String> speciesKeys(Object species, String canonical) {
        Set<String> keys = new LinkedHashSet<String>();
        addSpeciesKey(keys, canonical);
        if (species != null) {
            if (species instanceof Enum) addSpeciesKey(keys, ((Enum<?>) species).name());
            addSpeciesKey(keys, String.valueOf(species));
            Object pokemonName = invokeOptional(species, "getPokemonName");
            Object localizedName = invokeOptional(species, "getLocalizedName");
            if (pokemonName != null) addSpeciesKey(keys, String.valueOf(pokemonName));
            if (localizedName != null) addSpeciesKey(keys, String.valueOf(localizedName));
        }
        return Collections.unmodifiableSet(keys);
    }

    private static void addSpeciesKey(Set<String> keys, String value) {
        String normalized = SpeciesNames.normalize(value);
        if (!normalized.isEmpty()) keys.add(normalized);
    }

    private static String enumName(Object value) {
        if (value == null) return "Unknown";
        return value instanceof Enum ? ((Enum<?>) value).name() : value.toString();
    }

    private static Object invokeNoArg(Object target, String name) throws Exception {
        Method method = Reflect.findMethod(target.getClass(), name, 0);
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
        return method.invoke(target);
    }

    private static Object invokeOptional(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = Reflect.findMethod(target.getClass(), name, 0);
            return method == null ? null : method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean booleanMethod(Object target, String name) {
        Object value = invokeOptional(target, name);
        return value instanceof Boolean && (Boolean) value;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static boolean boolValue(Object value, boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }
}
