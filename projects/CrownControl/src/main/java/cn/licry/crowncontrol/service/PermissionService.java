package cn.licry.crowncontrol.service;

import cn.licry.crowncontrol.config.PluginConfig;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Player command precedence: specific command > crowncontrol.command > crowncontrol.default.
 * Global crown use precedence: crowncontrol.use > crowncontrol.crown.default > crowncontrol.default.
 * There are intentionally no per-crown permission nodes in rc3.
 */
public final class PermissionService {
    public static final String PLAYER_DEFAULT = "crowncontrol.default";
    public static final String COMMAND_BUNDLE = "crowncontrol.command";
    public static final String MASTER_USE = "crowncontrol.use";
    public static final String CROWN_DEFAULT = "crowncontrol.crown.default";

    private final PluginConfig config;

    public PermissionService(PluginConfig config) {
        this.config = config;
    }

    public boolean hasPlayerCommand(CommandSender sender, String specificNode) {
        if (specificNode != null && sender.isPermissionSet(specificNode)) {
            return sender.hasPermission(specificNode);
        }
        if (sender.isPermissionSet(COMMAND_BUNDLE)) {
            return sender.hasPermission(COMMAND_BUNDLE);
        }
        return sender.hasPermission(PLAYER_DEFAULT);
    }

    public boolean canUseCrown(Player player) {
        if (player.isPermissionSet(MASTER_USE)) return player.hasPermission(MASTER_USE);
        if (player.isPermissionSet(CROWN_DEFAULT)) return player.hasPermission(CROWN_DEFAULT);
        return player.hasPermission(PLAYER_DEFAULT);
    }

    public boolean bypassPokemonBlacklist(Player player) {
        return (config.opBypassesPokemonBlacklist() && player.isOp())
                || player.hasPermission("crowncontrol.bypass.pokemon-blacklist")
                || player.hasPermission("crowncontrol.bypass.pokemon-lists");
    }

    public boolean bypassPokemonWhitelist(Player player) {
        return player.hasPermission("crowncontrol.bypass.pokemon-whitelist")
                || player.hasPermission("crowncontrol.bypass.pokemon-lists");
    }
}
