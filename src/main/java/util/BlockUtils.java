/*
 * Copyright 2025-2026 Nolan Sharp
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

package com.nolan.ava.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.ChatColor;

/**
 * Stateless helpers for classifying blocks/materials/items that the movement
 * checks care about (climbable, ice, soul blocks, partial-height, liquids, etc).
 */
public final class BlockUtils {

    private BlockUtils() {
    }

    public static boolean isNearSolidBlock(Player player) {
        Location loc = player.getLocation();
        Block block = loc.getBlock();
        return block.getRelative(BlockFace.NORTH).getType().isSolid() ||
                block.getRelative(BlockFace.SOUTH).getType().isSolid() ||
                block.getRelative(BlockFace.EAST).getType().isSolid() ||
                block.getRelative(BlockFace.WEST).getType().isSolid();
    }

    public static boolean isClimbable(Block block) {
        Material type = block.getType();
        if (type == Material.LADDER || type == Material.VINE || type == Material.SCAFFOLDING) return true;

        String name = type.name();
        return name.contains("VINES") || name.contains("VINE");
    }

    public static boolean isIce(Block block) {
        Material type = block.getType();
        return type == Material.ICE || type == Material.PACKED_ICE || type == Material.BLUE_ICE;
    }

    public static boolean isSoulBlock(Block block) {
        Material type = block.getType();
        return type == Material.SOUL_SAND || type == Material.SOUL_SOIL;
    }

    public static boolean isPartialHeightBlock(Block block) {
        if (block == null) return false;

        Material type = block.getType();

        if (type == Material.SOUL_SAND || type == Material.SOUL_SOIL)
            return true;

        if (type == Material.FARMLAND)
            return true;

        if (type == Material.SNOW)
            return true;

        if (type.name().endsWith("_CARPET"))
            return true;

        if (type.name().endsWith("_SLAB") || type.name().endsWith("_STAIRS"))
            return true;

        if (type == Material.HONEY_BLOCK || type == Material.MUD)
            return true;

        if (!type.isOccluding())
            return true;

        return false;
    }

    public static boolean isInLiquid(Player player) {
        Location loc = player.getLocation();
        Block block = loc.getBlock();
        Material mCenter = block.getType();
        Material mBelow = block.getRelative(BlockFace.DOWN).getType();
        Material mAbove = block.getRelative(BlockFace.UP).getType();
        return mCenter == Material.WATER || mBelow == Material.WATER || mAbove == Material.WATER
                || mCenter == Material.LAVA || mBelow == Material.LAVA || mAbove == Material.LAVA
                || mCenter == Material.BUBBLE_COLUMN || mBelow == Material.BUBBLE_COLUMN || mAbove == Material.BUBBLE_COLUMN;
    }

    public static boolean isHighMobilityItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return false;

        String matName = item.getType().name();
        if (matName.contains("MACE") || matName.contains("TRIDENT")) return true;

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
            if (displayName.contains("spear")) return true;
        }

        return false;
    }
}
