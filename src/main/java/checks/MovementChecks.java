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

package com.nolan.ava.checks;

import com.nolan.ava.AvAAntiCheat;
import com.nolan.ava.data.PlayerData;
import com.nolan.ava.util.BlockUtils;
import com.nolan.ava.util.PingUtils;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Movement-related checks: flight/no-fall, horizontal speed, spider-climb
 * (wall climbing), and phase/no-clip.
 */
public class MovementChecks {

    private static final double MAX_FALL_DISTANCE = 0.5;

    private final AvAAntiCheat plugin;

    public MovementChecks(AvAAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void checkSpider(PlayerMoveEvent event, PlayerData data) {
        if (!plugin.isCheckSpiderEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 2) return;

        Player player = event.getPlayer();
        if (player.getAllowFlight() || player.isGliding() || player.isSwimming() || BlockUtils.isInLiquid(player)) {
            data.spiderTicks = 0;
            return;
        }

        if (data.isRiptiding || data.isWindBursting || (System.currentTimeMillis() - data.lastBreezeBoostTime < 4000)) {
            data.spiderTicks = 0;
            return;
        }

        if (player.hasPotionEffect(PotionEffectType.LEVITATION) || player.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
            data.spiderTicks = 0;
            return;
        }

        double deltaY = event.getTo().getY() - event.getFrom().getY();
        if (deltaY > 0 && !player.isOnGround() && BlockUtils.isNearSolidBlock(player)) {
            Block b = player.getLocation().getBlock();
            if (!BlockUtils.isClimbable(b) && !BlockUtils.isClimbable(b.getRelative(BlockFace.DOWN))) {
                data.spiderTicks++;
                if (data.spiderTicks > 10) {
                    data.spiderViolations++;
                    plugin.logToFile(player.getName(), "CHECK:Spider VIO=" + data.spiderViolations + " Ticks=" + data.spiderTicks);
                    data.spiderTicks = 5;
                    if (data.spiderViolations > plugin.getSpiderViolationLimit()) {
                        plugin.punishPlayer(player, "Spider (WallClimb)", data.spiderViolations);
                    }
                }
            } else {
                data.spiderTicks = 0;
            }
        } else {
            data.spiderTicks = 0;
        }
    }

    public void checkFlight(PlayerMoveEvent event, PlayerData data) {
        if (!plugin.isCheckFlightEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 2) return;

        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (BlockUtils.isClimbable(player.getLocation().getBlock()) || BlockUtils.isClimbable(player.getLocation().getBlock().getRelative(BlockFace.DOWN))) {
            data.flyViolations = 0;
            return;
        }

        if (player.isGliding()) {
            data.isGliding = true;
            data.lastGlideTime = System.currentTimeMillis();
            data.flyViolations = 0;
            return;
        }
        if (System.currentTimeMillis() - data.lastGlideTime < 3000) {
            data.flyViolations = 0;
            return;
        }
        if (data.isGliding && player.isOnGround()) {
            data.isGliding = false;
        }

        if (player.isRiptiding() || data.isWindBursting || (System.currentTimeMillis() - data.lastBreezeBoostTime < 14000)) {
            data.flyViolations = 0;
            return;
        }
        if (System.currentTimeMillis() - data.lastVelocityTime < 10000) {
            return;
        }

        if (player.getAllowFlight() || player.isSwimming() || player.hasPotionEffect(PotionEffectType.LEVITATION) || BlockUtils.isInLiquid(player) || player.isInsideVehicle()) {
            data.flyViolations = 0;
            return;
        }

        double deltaY = to.getY() - from.getY();
        boolean isHighPower = plugin.getHardwareManager().isHighPerformance();
        boolean wasOnGround = data.wasOnGround;
        data.wasOnGround = player.isOnGround();

        if (isHighPower) {
            if (!player.isOnGround() && !wasOnGround && deltaY > 0) {
                double expectedY = (data.lastDeltaY - 0.08) * 0.98;
                int ping = PingUtils.getPlayerPing(player);
                double pingMargin = (ping > 300) ? 0.05 : 0.0;

                if (deltaY > (expectedY + 0.1 + pingMargin) && player.getFallDistance() < MAX_FALL_DISTANCE) {
                    if (data.spiderTicks > 0) return;
                    data.flyViolations++;
                    plugin.logToFile(player.getName(), "CHECK:Flight (Physics) VIO=" + data.flyViolations + " Y=" + String.format("%.3f", deltaY) + " ExpectedY=" + String.format("%.3f", expectedY) + " Ping=" + ping + "ms");
                    if (data.flyViolations > plugin.getFlyViolationLimit()) {
                        plugin.punishPlayer(player, "Flight", data.flyViolations);
                    }
                }
            } else if (player.isOnGround()) {
                if (data.flyViolations > 0) data.flyViolations--;
            }
        } else {
            if (!player.isOnGround() && deltaY > 0.05 && player.getFallDistance() < MAX_FALL_DISTANCE) {
                if (deltaY > MAX_FALL_DISTANCE) {
                    if (data.spiderTicks > 0) return;
                    data.flyViolations++;
                    plugin.logToFile(player.getName(), "CHECK:Flight VIO=" + data.flyViolations + " Y=" + String.format("%.3f", deltaY));
                    if (data.flyViolations > plugin.getFlyViolationLimit()) {
                        plugin.punishPlayer(player, "Flight", data.flyViolations);
                    }
                }
            } else if (player.isOnGround()) {
                data.flyViolations = 0;
            }
        }

        data.lastDeltaY = deltaY;
    }

    public void checkSpeed(PlayerMoveEvent event, PlayerData data) {
        if (!plugin.isCheckSpeedEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 2) return;

        Player player = event.getPlayer();

        if (player.isGliding()) return;
        if (player.isRiptiding()) return;
        if (player.isSwimming()) return;
        if (BlockUtils.isInLiquid(player)) return;

        if (player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) return;

        if (player.getAllowFlight() || player.isFlying() || player.isInsideVehicle()) return;

        if (data.isWindBursting || (System.currentTimeMillis() - data.lastBreezeBoostTime < 4000)) return;

        if (System.currentTimeMillis() - data.lastVelocityTime < 4000) return;

        if (data.glideEndTime > 0 && (System.currentTimeMillis() - data.glideEndTime < plugin.getGlideGracePeriodMs())) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        double deltaX = to.getX() - from.getX();
        double deltaZ = to.getZ() - from.getZ();
        double horizontalDistance = Math.hypot(deltaX, deltaZ);

        double speedLimit = plugin.getBaseSpeedLimit();

        Block blockBelow = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        if (BlockUtils.isIce(blockBelow)) {
            speedLimit = plugin.getIceSpeedLimit();
        }

        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            speedLimit += (amplifier * 0.15);
        }

        if (BlockUtils.isSoulBlock(blockBelow)) {
            ItemStack boots = player.getInventory().getBoots();
            if (boots != null && boots.containsEnchantment(Enchantment.SOUL_SPEED)) {
                int level = boots.getEnchantmentLevel(Enchantment.SOUL_SPEED);
                speedLimit += (level * 0.2);
            }
        }

        if (BlockUtils.isHighMobilityItem(player)) {
            speedLimit += 0.6;
        }

        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - data.lastMoveTime;
        data.lastMoveTime = currentTime;

        if (timeDiff < 5) timeDiff = 5;
        double ticksElapsed = timeDiff / 50.0;

        int ping = PingUtils.getPlayerPing(player);

        double maxLagTicks = Math.min(20.0, Math.max(5.0, (ping + 100) / 50.0));
        ticksElapsed = Math.min(ticksElapsed, maxLagTicks);

        boolean isHighPower = plugin.getHardwareManager().isHighPerformance();

        if (isHighPower) {
            speedLimit -= 0.05;
        }

        // BUG FIX: tick-elapsed scaling used to only apply "if (isHighPower)". That meant
        // OPTIMIZED_LIGHT mode compared raw per-move distance against an un-scaled limit even
        // when several ticks had elapsed between move events (e.g. during server lag), causing
        // false speed-hack flags. The scaling now applies to both hardware modes.
        speedLimit = speedLimit * Math.max(1.0, ticksElapsed);

        if (PingUtils.isBedrock(player)) {
            speedLimit *= 1.15;
        }

        if (!isHighPower && horizontalDistance < (speedLimit * 0.8)) {
            return;
        }

        if (horizontalDistance > speedLimit) {
            data.speedViolations++;
            plugin.logToFile(player.getName(),
                    "CHECK:Speed VIO=" + data.speedViolations +
                            " Dist=" + String.format("%.3f", horizontalDistance) +
                            " Limit=" + String.format("%.3f", speedLimit) +
                            " TicksDelta=" + String.format("%.2f", ticksElapsed) +
                            " Ping=" + ping + "ms");

            if (data.speedViolations > plugin.getSpeedViolationLimit()) {
                plugin.punishPlayer(player, "Speed", data.speedViolations);
            }
        } else {
            if (data.speedViolations > 0) data.speedViolations--;
        }
    }

    public void checkPhase(PlayerMoveEvent event, PlayerData data) {
        if (!plugin.isCheckPhaseEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 2) return;

        Player player = event.getPlayer();

        if (player.getAllowFlight() || player.isFlying()) return;
        if (player.isSwimming()) return;
        if (player.isRiptiding()) return;
        if (BlockUtils.isInLiquid(player)) return;
        if (player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) return;
        if (System.currentTimeMillis() - data.lastVelocityTime < 2000) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        boolean isHighPower = plugin.getHardwareManager().isHighPerformance();

        if (isHighPower) {
            double deltaY = to.getY() - from.getY();

            if (deltaY < -4.0) {
                event.setTo(from);
                player.sendMessage(plugin.getPrefix() + ChatColor.RED + "Hey! You can't clip through the floor!");
                plugin.logToFile(player.getName(), "CHECK:Phase - V-Clip Prevented! DeltaY=" + String.format("%.2f", deltaY));
                return;
            }

            Vector dir = to.toVector().subtract(from.toVector());
            double dist = dir.length();

            int ping = PingUtils.getPlayerPing(player);
            double rayTraceThreshold = (ping > 400) ? 0.6 : 0.4;

            if (dist > rayTraceThreshold && dist < 10.0) {
                Location traceStart = from.clone().add(0, 1.0, 0);

                RayTraceResult trace = player.getWorld().rayTraceBlocks(traceStart, dir, dist, FluidCollisionMode.NEVER, true);

                if (trace != null && trace.getHitBlock() != null) {
                    Block hit = trace.getHitBlock();
                    if (hit.getType().isOccluding() && !BlockUtils.isPartialHeightBlock(hit)) {
                        event.setTo(from);
                        player.sendMessage(plugin.getPrefix() + ChatColor.RED + "Hey! You can't phase through blocks like that!");
                        plugin.logToFile(player.getName(), "CHECK:Phase - RayTrace Intersected " + hit.getType().name() + " Ping=" + ping + "ms");
                        return;
                    }
                }
            }
        }

        Block toBlockFeet = to.getBlock();
        Block fromBlockFeet = from.getBlock();
        Block blockBelow = player.getLocation().getBlock().getRelative(BlockFace.DOWN);

        if (BlockUtils.isPartialHeightBlock(blockBelow) || BlockUtils.isSoulBlock(blockBelow)) return;
        if (BlockUtils.isPartialHeightBlock(fromBlockFeet) || BlockUtils.isPartialHeightBlock(toBlockFeet)) return;

        if (toBlockFeet.getType().isOccluding() &&
                !fromBlockFeet.getType().isOccluding()) {

            Block eyeBlock = player.getEyeLocation().getBlock();
            if (!eyeBlock.getType().isOccluding()) return;

            event.setTo(from);
            player.sendMessage(plugin.getPrefix() + ChatColor.RED + "Hey! You can't phase through blocks like that!");
            plugin.logToFile(player.getName(),
                    "CHECK:Phase - Prevented phasing into " + toBlockFeet.getType().name());
        }
    }
}
