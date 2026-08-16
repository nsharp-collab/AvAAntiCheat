/*
 * Copyright 2025-2026 AvA-Anti-Cheat (Nolan Sharp)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

        // Normal item dropping from the cursor is not a duplication exploit.
        // Only keep duping checks on inventory close with a remaining cursor item.
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

        long now = System.currentTimeMillis();
        boolean isRepeatSuspicion = data.dupeViolations > 0 && (now - data.lastDupeSuspicionTime) <= 20000;
        data.lastDupeSuspicionTime = now;

        if (!isRepeatSuspicion) {
            data.dupeViolations = 1;
            plugin.logToFile(player.getName(), "Inventory closed with cursor item (first suspicion): " + cursor.getType() + " x" + cursor.getAmount());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.updateInventory();
                plugin.logToFile(player.getName(), "Dupe detection warning only after cursor close.");
                player.sendMessage(plugin.getPrefix() + ChatColor.YELLOW + "Suspicious inventory behavior detected. Please close your inventory cleanly.");
            }, 2L);
            return;
        }

        data.dupeViolations++;
        plugin.logToFile(player.getName(), "Repeated inventory cursor close suspicion: " + cursor.getType() + " x" + cursor.getAmount());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.updateInventory();
            plugin.logToFile(player.getName(), "Forced inventory resynchronization after repeated cursor close.");
            plugin.punishPlayer(player, "Item Duplication", data.dupeViolations);
        }, 2L);
    }
}
