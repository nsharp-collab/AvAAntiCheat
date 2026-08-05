package com.nolan.ava.checks;

import com.nolan.ava.AvAAntiCheat;
import com.nolan.ava.data.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Basic inventory duplication protection.
 *
 * item vector dupe fix
 */
public class DupeCheck {

    private final AvAAntiCheat plugin;

    public DupeCheck(AvAAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void checkInventoryClick(InventoryClickEvent event, PlayerData data) {
        if (!plugin.isCheckDupeEnabled()) return;

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (player.getGameMode().toString().contains("CREATIVE") || player.getGameMode().toString().contains("SPECTATOR")) return;

        InventoryAction action = event.getAction();
        if (action != InventoryAction.DROP_ALL_CURSOR && action != InventoryAction.DROP_ONE_CURSOR) {
            return;
        }

        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) return;

        if (cursor.getType().name().contains("SHULKER_BOX")) {
            // shulker dupe vector
            return;
        }

        data.dupeViolations++;
        plugin.logToFile(player.getName(), "Suspicious dupe-related inventory drop: " + cursor.getType() + " x" + cursor.getAmount());
        player.sendMessage(plugin.getPrefix() + ChatColor.RED + "Item dupe attempts are monitored. Close your inventory cleanly.");
        plugin.punishPlayer(player, "Item Duplication", data.dupeViolations);
    }

    public void checkInventoryClose(InventoryCloseEvent event, PlayerData data) {
        if (!plugin.isCheckDupeEnabled()) return;

        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (player.getGameMode().toString().contains("CREATIVE") || player.getGameMode().toString().contains("SPECTATOR")) return;

        ItemStack cursor = event.getView().getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) return;
        if (cursor.getType().name().contains("SHULKER_BOX")) {
            return;
        }

        data.dupeViolations++;
        plugin.logToFile(player.getName(), "Inventory closed with cursor item: " + cursor.getType() + " x" + cursor.getAmount());

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.updateInventory();
            plugin.logToFile(player.getName(), "Forced inventory resynchronization after cursor close.");
            plugin.punishPlayer(player, "Item Duplication", data.dupeViolations);
        }, 2L);
    }
}
