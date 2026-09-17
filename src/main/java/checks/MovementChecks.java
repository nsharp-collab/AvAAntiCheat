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
import com.nolan.ava.util.BlockUtils;
import com.nolan.ava.util.PingUtils;
import com.nolan.ava.util.PredictionUtils;
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
    private static final long COMBAT_MOVE_GRACE_MS = 600;

    private final AvAAntiCheat plugin;

    public MovementChecks(AvAAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void checkSpider(PlayerMoveEvent event, PlayerData data) {
        if (!plugin.isCheckSpiderEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 2) return;
        if (plugin.shouldBypassChecks(data)) return;
        if (System.currentTimeMillis() - data.lastCombatActivityTime < COMBAT_MOVE_GRACE_MS) return;

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
                int spiderTickThreshold = Math.max(5, 10 + (5 - plugin.getSpiderSeverity()));
                if (data.spiderTicks > spiderTickThreshold) {
                    data.spiderViolations++;
                    plugin.logToFile(player.getName(), "CHECK:Spider VIO=" + data.spiderViolations + " Ticks=" + data.spiderTicks + " Threshold=" + spiderTickThreshold);
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
        if (plugin.shouldBypassChecks(data)) return;
        if (System.currentTimeMillis() - data.lastCombatActivityTime < COMBAT_MOVE_GRACE_MS) return;

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

        if (!player.isOnGround()) {
            data.airborneTicks++;
        } else {
            data.airborneTicks = 0;
            data.averageVerticalDelta = 0.0;
        }

        double serverTps = PingUtils.getServerTps();

        if (isHighPower) {
            if (!player.isOnGround() && !wasOnGround && deltaY > 0 && data.airborneTicks > 1) {
                double expectedY = PredictionUtils.predictAirborneVertical(data.lastDeltaY, data.averageVerticalDelta);
                int ping = PingUtils.getPlayerPing(player);
                double severityScale = 1.0 + (5 - plugin.getFlightSeverity()) * 0.06;
                double pingMargin = (ping > 300) ? 0.05 : 0.0;
                double tpsMargin = serverTps < 18.0 ? (18.0 - serverTps) * 0.015 : 0.0;
                if (PingUtils.isGeyserPlayer(player)) {
                    tpsMargin += 0.03;
                }
                double adaptiveThreshold = Math.max(expectedY + 0.11 * severityScale + pingMargin + tpsMargin,
                        data.averageVerticalDelta + 0.12 * severityScale + tpsMargin);

                if (deltaY > adaptiveThreshold && player.getFallDistance() < MAX_FALL_DISTANCE) {
                    if (data.spiderTicks > 0) return;
                    data.flyViolations++;
                    plugin.logToFile(player.getName(), "CHECK:Flight (Predictive) VIO=" + data.flyViolations + " Y=" + String.format("%.3f", deltaY) + " ExpectedY=" + String.format("%.3f", expectedY) + " Ping=" + ping + "ms TPS=" + String.format("%.2f", serverTps));
                    if (data.flyViolations > plugin.getFlyViolationLimit()) {
                        plugin.punishPlayer(player, "Flight", data.flyViolations);
                    }
                }
            } else if (player.isOnGround()) {
                if (data.flyViolations > 0) data.flyViolations--;
            }
        } else {
            if (!player.isOnGround() && deltaY > 0.05 && player.getFallDistance() < MAX_FALL_DISTANCE) {
                double tpsMargin = serverTps < 18.0 ? (18.0 - serverTps) * 0.02 : 0.0;
                if (PingUtils.isGeyserPlayer(player)) {
                    tpsMargin += 0.04;
                }
                double severityScale = 1.0 + (5 - plugin.getFlightSeverity()) * 0.06;
                double adaptiveThreshold = Math.max(MAX_FALL_DISTANCE, data.averageVerticalDelta + 0.12 * severityScale + tpsMargin);
                if (deltaY > adaptiveThreshold) {
                    if (data.spiderTicks > 0) return;
                    data.flyViolations++;
                    plugin.logToFile(player.getName(), "CHECK:Flight (Predictive) VIO=" + data.flyViolations + " Y=" + String.format("%.3f", deltaY) + " TPS=" + String.format("%.2f", serverTps));
                    if (data.flyViolations > plugin.getFlyViolationLimit()) {
                        plugin.punishPlayer(player, "Flight", data.flyViolations);
                    }
                }
            } else if (player.isOnGround()) {
                data.flyViolations = 0;
            }
        }

        data.averageVerticalDelta = PredictionUtils.exponentialMovingAverage(data.averageVerticalDelta, deltaY, 0.22);
        data.lastDeltaY = deltaY;
    }

    public void checkSpeed(PlayerMoveEvent event, PlayerData data) {
        if (!plugin.isCheckSpeedEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 2) return;
        if (plugin.shouldBypassChecks(data)) return;
        if (System.currentTimeMillis() - data.lastCombatActivityTime < COMBAT_MOVE_GRACE_MS) return;

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
        double serverTps = PingUtils.getServerTps();

        double maxLagTicks = Math.max(5.0, (ping + 100) / 50.0 + (20.0 - serverTps) * 0.5);
        if (PingUtils.isGeyserPlayer(player)) {
            maxLagTicks += 3.0;
        }
        maxLagTicks = Math.min(30.0, maxLagTicks);
        ticksElapsed = Math.min(ticksElapsed, maxLagTicks);

        boolean isHighPower = plugin.getHardwareManager().isHighPerformance();
        double speedSeverityOffset = (5 - plugin.getSpeedSeverity()) * 0.04;

        if (isHighPower) {
            speedLimit -= 0.05;
        }
        speedLimit += speedSeverityOffset;

        // BUG FIX: tick-elapsed scaling used to only apply "if (isHighPower)". That meant
        // OPTIMIZED_LIGHT mode compared raw per-move distance against an un-scaled limit even
        // when several ticks had elapsed between move events (e.g. during server lag), causing
        // false speed-hack flags. The scaling now applies to both hardware modes.
        speedLimit = speedLimit * Math.max(1.0, ticksElapsed);

        if (PingUtils.isBedrock(player)) {
            speedLimit *= 1.15;
        }

        double lagLimit = Math.max(0.0, (20.0 - serverTps) * 0.04);
        lagLimit += (ping > 250) ? Math.min(0.16, (ping - 250) * 0.0007) : 0.0;
        if (PingUtils.isGeyserPlayer(player)) {
            lagLimit += 0.08;
        }
        speedLimit += lagLimit;
        speedLimit += 0.05;

        double predictedHorizontal = PredictionUtils.predictGroundSpeed(data.lastHorizontalDistance, data.averageHorizontalSpeed, data.horizontalSpeedVariance);
        double adaptiveBoost = 0.08 * (1.0 + (5 - plugin.getSpeedSeverity()) * 0.05);
        double adaptiveLimit = Math.max(speedLimit, predictedHorizontal + adaptiveBoost);

        if (!isHighPower && horizontalDistance < (speedLimit * 0.8)) {
            data.averageHorizontalSpeed = PredictionUtils.exponentialMovingAverage(data.averageHorizontalSpeed, horizontalDistance, 0.18);
            data.horizontalSpeedVariance = PredictionUtils.updateVariance(data.horizontalSpeedVariance, data.averageHorizontalSpeed, horizontalDistance, 0.18);
            data.lastHorizontalDistance = horizontalDistance;
            return;
        }

        if (horizontalDistance > adaptiveLimit) {
            data.speedViolations++;
            plugin.logToFile(player.getName(),
                    "CHECK:Speed (Predictive) VIO=" + data.speedViolations +
                            " Dist=" + String.format("%.3f", horizontalDistance) +
                            " Limit=" + String.format("%.3f", speedLimit) +
                            " Pred=" + String.format("%.3f", predictedHorizontal) +
                            " TicksDelta=" + String.format("%.2f", ticksElapsed) +
                            " Ping=" + ping + "ms");

            if (data.speedViolations > plugin.getSpeedViolationLimit()) {
                plugin.punishPlayer(player, "Speed", data.speedViolations);
            }
        } else {
            if (data.speedViolations > 0) data.speedViolations--;
        }

        data.averageHorizontalSpeed = PredictionUtils.exponentialMovingAverage(data.averageHorizontalSpeed, horizontalDistance, 0.18);
        data.horizontalSpeedVariance = PredictionUtils.updateVariance(data.horizontalSpeedVariance, data.averageHorizontalSpeed, horizontalDistance, 0.18);
        data.lastHorizontalDistance = horizontalDistance;
    }

    public void checkPhase(PlayerMoveEvent event, PlayerData data) {
        if (!plugin.isCheckPhaseEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 2) return;
        if (plugin.shouldBypassChecks(data)) return;
        if (System.currentTimeMillis() - data.lastCombatActivityTime < COMBAT_MOVE_GRACE_MS) return;

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
            double serverTps = PingUtils.getServerTps();
            double rayTraceThreshold = 0.4 + (5 - plugin.getPhaseSeverity()) * 0.04;
            if (ping > 400) {
                rayTraceThreshold += 0.2;
            }
            if (serverTps < 18.0) {
                rayTraceThreshold += 0.12;
            }
            if (PingUtils.isGeyserPlayer(player)) {
                rayTraceThreshold += 0.18;
            }

            if (dist > rayTraceThreshold && dist < 10.0 && serverTps >= 13.0) {
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
