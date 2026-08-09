package cn.licry.mintcontrol.service;

import cn.licry.mintcontrol.config.PluginConfig;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Player command precedence: specific command > mintcontrol.command > mintcontrol.default.
 * Global mint use precedence: mintcontrol.use > mintcontrol.mint.default > mintcontrol.default.
 * There are intentionally no per-mint permission nodes in rc13.
 */
public final class PermissionService {
    public static final String PLAYER_DEFAULT = "mintcontrol.default";
    public static final String COMMAND_BUNDLE = "mintcontrol.command";
    public static final String MASTER_USE = "mintcontrol.use";
    public static final String MINT_DEFAULT = "mintcontrol.mint.default";

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

    public boolean canUseMint(Player player) {
        if (player.isPermissionSet(MASTER_USE)) return player.hasPermission(MASTER_USE);
        if (player.isPermissionSet(MINT_DEFAULT)) return player.hasPermission(MINT_DEFAULT);
        return player.hasPermission(PLAYER_DEFAULT);
    }

    public boolean bypassPokemonBlacklist(Player player) {
        return (config.opBypassesPokemonBlacklist() && player.isOp())
                || player.hasPermission("mintcontrol.bypass.pokemon-blacklist")
                || player.hasPermission("mintcontrol.bypass.pokemon-lists");
    }

    public boolean bypassPokemonWhitelist(Player player) {
        return player.hasPermission("mintcontrol.bypass.pokemon-whitelist")
                || player.hasPermission("mintcontrol.bypass.pokemon-lists");
    }
}
