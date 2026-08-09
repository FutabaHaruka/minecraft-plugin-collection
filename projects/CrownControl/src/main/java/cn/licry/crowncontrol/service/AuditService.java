package cn.licry.crowncontrol.service;

import cn.licry.crowncontrol.config.PluginConfig;
import cn.licry.crowncontrol.model.GlobalRule;
import cn.licry.crowncontrol.model.PokemonView;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.BufferedWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AuditService {
    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final SimpleDateFormat timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
    private final SimpleDateFormat month = new SimpleDateFormat("yyyy-MM", Locale.ROOT);

    public AuditService(JavaPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public synchronized void log(Player player, String crownName, GlobalRule rule,
                                 PokemonView pokemon, String outcome, double roll,
                                 String oldTraining, String newTraining, String detail) {
        if (!config.auditLog()) return;
        config.ensureOwnedDirectory("logs");
        String relativeFile = "logs/attempts-" + month.format(new Date()) + ".csv";
        File file = config.resolveOwnedFile(relativeFile);
        boolean header = !file.exists();
        try (BufferedWriter writer = config.openOwnedAppendWriter(relativeFile)) {
            if (header) {
                writer.write("time,player_uuid,player_name,crown,slot,species,category,old_hyper_training,new_hyper_training,outcome,roll,success_rate,failure_rate,money,points,detail\n");
            }
            writer.write(csv(timestamp.format(new Date())) + ','
                    + csv(player.getUniqueId().toString()) + ','
                    + csv(player.getName()) + ','
                    + csv(crownName) + ','
                    + pokemon.getSlot() + ','
                    + csv(pokemon.getSpecies()) + ','
                    + csv(pokemon.getCategory().name()) + ','
                    + csv(oldTraining) + ','
                    + csv(newTraining) + ','
                    + csv(outcome) + ','
                    + roll + ','
                    + rule.getSuccessRate() + ','
                    + rule.getFailureRate() + ','
                    + rule.getMoney() + ','
                    + rule.getPoints() + ','
                    + csv(detail) + "\n");
        } catch (IOException ex) {
            plugin.getLogger().warning("Unable to write audit log: " + ex.getMessage());
        }
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return '"' + safe + '"';
    }
}
