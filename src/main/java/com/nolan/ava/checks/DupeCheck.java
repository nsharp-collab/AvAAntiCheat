/*
 * Copyright 2025-2026 AvA-Anti-Cheat Contributers 
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
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Inventory integrity helpers.
 *
 * Cursor-on-close is normal vanilla behavior and is no longer treated as a dupe.
 * This check only forces a client inventory resync when enabled; real window-click
 * integrity belongs in a future inventory-packet check.
 */
public class DupeCheck {

    private final AvAAntiCheat plugin;

    public DupeCheck(AvAAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void checkInventoryClick(InventoryClickEvent event, PlayerData data) {
        if (!plugin.isCheckDupeEnabled()) return;
        // Reserved for future window-click sequence validation.
    }

    public void checkInventoryClose(InventoryCloseEvent event, PlayerData data) {
        if (!plugin.isCheckDupeEnabled()) return;

        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (player.getGameMode().toString().contains("CREATIVE") || player.getGameMode().toString().contains("SPECTATOR")) {
            return;
        }

        // Soft resync only — do not kick for cursor items (legitimate vanilla state).
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.updateInventory();
        }, 1L);
    }
}
