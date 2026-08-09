package cn.licry.crowncontrol.service;

import cn.licry.crowncontrol.bridge.PlayerPointsBridge;
import cn.licry.crowncontrol.bridge.VaultBridge;
import cn.licry.crowncontrol.cost.CostCheck;
import cn.licry.crowncontrol.cost.CostReceipt;
import cn.licry.crowncontrol.model.GlobalRule;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Handles only Vault money and PlayerPoints. No item/NBT inventory costs exist. */
public final class CostService {
    private final JavaPlugin plugin;
    private final VaultBridge vault;
    private final PlayerPointsBridge points;

    public CostService(JavaPlugin plugin, VaultBridge vault, PlayerPointsBridge points) {
        this.plugin = plugin;
        this.vault = vault;
        this.points = points;
    }

    public CostCheck precheck(Player player, GlobalRule rule, boolean bypass) {
        if (bypass) return CostCheck.ok();
        try {
            if (rule.getMoney() > 0.0D && (!vault.isAvailable() || !vault.has(player, rule.getMoney()))) {
                return CostCheck.fail("money-missing", rule.getMoney());
            }
            if (rule.getPoints() > 0 && (!points.isAvailable() || points.balance(player) < rule.getPoints())) {
                return CostCheck.fail("points-missing", rule.getPoints());
            }
            return CostCheck.ok();
        } catch (Throwable ex) {
            plugin.getLogger().warning("Currency precheck failed for " + player.getName() + ": " + ex.getMessage());
            return CostCheck.fail("internal-error", 0.0D);
        }
    }

    public CostReceipt charge(Player player, GlobalRule rule, boolean bypass) throws Exception {
        CostReceipt receipt = new CostReceipt();
        if (bypass) return receipt;
        try {
            if (rule.getMoney() > 0.0D) {
                if (!vault.isAvailable() || !vault.withdraw(player, rule.getMoney())) {
                    throw new IllegalStateException("Vault withdrawal failed");
                }
                receipt.setMoney(rule.getMoney());
            }
            if (rule.getPoints() > 0) {
                if (!points.isAvailable() || !points.take(player, rule.getPoints())) {
                    throw new IllegalStateException("PlayerPoints withdrawal failed");
                }
                receipt.setPoints(rule.getPoints());
            }
            return receipt;
        } catch (Throwable ex) {
            refund(player, receipt);
            if (ex instanceof Exception) throw (Exception) ex;
            throw new RuntimeException(ex);
        }
    }

    public void refund(Player player, CostReceipt receipt) {
        if (receipt == null) return;
        try {
            if (receipt.getMoney() > 0.0D && vault.isAvailable()) vault.deposit(player, receipt.getMoney());
        } catch (Throwable ex) {
            plugin.getLogger().severe("Failed to refund money to " + player.getName() + ": " + ex.getMessage());
        }
        try {
            if (receipt.getPoints() > 0 && points.isAvailable()) points.give(player, receipt.getPoints());
        } catch (Throwable ex) {
            plugin.getLogger().severe("Failed to refund points to " + player.getName() + ": " + ex.getMessage());
        }
    }
}
