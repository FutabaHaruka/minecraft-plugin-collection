package cn.licry.mintcontrol.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import cn.licry.mintcontrol.util.SpeciesNames;

public final class PokemonView {
    private final int slot;
    private final Object storage;
    private final Object pokemon;
    private final String species;
    private final String displayName;
    private final String nature;
    private final int level;
    private final boolean egg;
    private final PokemonCategory category;
    private final Set<String> speciesKeys;

    public PokemonView(int slot, Object storage, Object pokemon, String species, String displayName,
                       String nature, int level, boolean egg, PokemonCategory category,
                       Set<String> speciesKeys) {
        this.slot = slot;
        this.storage = storage;
        this.pokemon = pokemon;
        this.species = species;
        this.displayName = displayName;
        this.nature = nature;
        this.level = level;
        this.egg = egg;
        this.category = category;
        LinkedHashSet<String> copy = new LinkedHashSet<String>();
        if (speciesKeys != null) copy.addAll(speciesKeys);
        copy.add(SpeciesNames.normalize(species));
        this.speciesKeys = Collections.unmodifiableSet(copy);
    }

    public int getSlot() { return slot; }
    public Object getStorage() { return storage; }
    public Object getPokemon() { return pokemon; }
    public String getSpecies() { return species; }
    public String getDisplayName() { return displayName; }
    public String getNature() { return nature; }
    public int getLevel() { return level; }
    public boolean isEgg() { return egg; }
    public PokemonCategory getCategory() { return category; }
    public Set<String> getSpeciesKeys() { return speciesKeys; }

    public boolean matchesSpecies(Set<String> speciesList) {
        if (speciesList == null || speciesList.isEmpty()) return false;
        for (String candidate : speciesList) {
            String normalized = SpeciesNames.normalize(candidate);
            if (!normalized.isEmpty() && speciesKeys.contains(normalized)) return true;
        }
        return false;
    }
}
