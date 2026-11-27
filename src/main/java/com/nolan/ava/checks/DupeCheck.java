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
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.block.data.type.SmallDripleaf;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

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
    public void checkDripleafFertilize(PlayerInteractEvent event, PlayerData data) {
    if (!plugin.isCheckDupeEnabled()) return;
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

    Block block = event.getClickedBlock();
    if (block == null || block.getType() != Material.SMALL_DRIPLEAF) return;
    if (!isBoneMeal(event.getItem())) return;
    if (!isOrphanedDripleafHalf(block)) return;

    Player player = event.getPlayer();
    if (player.getGameMode().toString().contains("CREATIVE") || player.getGameMode().toString().contains("SPECTATOR")) {
        return;
    }

    event.setCancelled(true);

    long now = System.currentTimeMillis();
    if (now - data.lastDupeSuspicionTime < 250L) return;
    data.lastDupeSuspicionTime = now;

    plugin.logToFile(player.getName(), "CHECK:Dupe blocked bone-meal on orphaned small dripleaf at "
            + block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ());

    if (plugin.getDupeSeverity() <= 2) return;

    data.dupeViolations++;
    plugin.punishPlayer(player, "item duplication", data.dupeViolations);
}

public void checkDripleafDispense(BlockDispenseEvent event) {
    if (!plugin.isCheckDupeEnabled()) return;
    if (!isBoneMeal(event.getItem())) return;

    Block dispenser = event.getBlock();
    if (!(dispenser.getBlockData() instanceof Dispenser)) return;

    Block target = dispenser.getRelative(((Dispenser) dispenser.getBlockData()).getFacing());
    if (!isOrphanedDripleafHalf(target)) return;

    event.setCancelled(true);
    plugin.logToFile("dispenser@" + dispenser.getWorld().getName() + ","
                    + dispenser.getX() + "," + dispenser.getY() + "," + dispenser.getZ(),
            "CHECK:Dupe blocked dispenser bone-meal on orphaned small dripleaf at "
                    + target.getX() + "," + target.getY() + "," + target.getZ());
}
private boolean isOrphanedDripleafHalf(Block block) {
    if (block.getType() != Material.SMALL_DRIPLEAF) return false;
    if (!(block.getBlockData() instanceof org.bukkit.block.data.type.SmallDripleaf)) return false;

    org.bukkit.block.data.type.SmallDripleaf half =
            (org.bukkit.block.data.type.SmallDripleaf) block.getBlockData();
    Block partner = half.getHalf() == org.bukkit.block.data.Bisected.Half.TOP
            ? block.getRelative(BlockFace.DOWN)
            : block.getRelative(BlockFace.UP);

    if (partner.getType() != Material.SMALL_DRIPLEAF) return true;
    if (!(partner.getBlockData() instanceof org.bukkit.block.data.type.SmallDripleaf)) return true;

    org.bukkit.block.data.type.SmallDripleaf partnerHalf =
            (org.bukkit.block.data.type.SmallDripleaf) partner.getBlockData();
    return partnerHalf.getHalf() == half.getHalf();
}

private boolean isBoneMeal(ItemStack item) {
    return item != null && item.getType() == Material.BONE_MEAL;
}
}
