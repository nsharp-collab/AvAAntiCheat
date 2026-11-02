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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
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

    private boolean isVelocityExempt(PlayerData data) {
        return System.currentTimeMillis() < data.velocityExemptUntil;
    }

    private boolean isMovementExempt(PlayerData data) {
        return isVelocityExempt(data) || data.isTeleportExempt();
    }

    public void checkSpider(PlayerMoveEvent event, PlayerData data) {
        if (!plugin.isCheckSpiderEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 2) return;
        if (plugin.shouldBypassChecks(event.getPlayer())) return;
        if (isMovementExempt(data)) return;

        Player player = event.getPlayer();
        if (player.getAllowFlight() || player.isGliding() || player.isSwimming() || BlockUtils.isInLiquid(player)) {
            data.spiderTicks = 0;
            return;
        }

        if (data.isRiptiding || data.isWindBursting || (System.currentTimeMillis() - data.lastBreezeBoostTime < 4000)) {
            data.spiderTicks = 0;
            return;
        }

        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) {
            data.spiderTicks = 0;
            return;
        }

        Location to = event.getTo();
        if (to == null) return;
        double deltaY = to.getY() - event.getFrom().getY();
        double maxStep = 0.42;
        PotionEffect jump = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jump != null) {
            // Raise expected climb instead of fully disabling
            maxStep += (jump.getAmplifier() + 1) * 0.1;
        }

        boolean serverAirborne = !isServerOnGround(player);
        if (deltaY > maxStep && serverAirborne && BlockUtils.isNearSolidBlock(player)) {
            Block b = player.getLocation().getBlock();
            if (!BlockUtils.isClimbable(b) && !BlockUtils.isClimbable(b.getRelative(BlockFace.DOWN))) {
                data.spiderTicks++;
                int spiderTickThreshold = Math.max(5, 10 + (5 - plugin.getSpiderSeverity()));
                if (data.spiderTicks > spiderTickThreshold) {
                    data.spiderViolations++;
                    plugin.logToFile(player.getName(), "CHECK:Spider VIO=" + data.spiderViolations
                            + " Ticks=" + data.spiderTicks + " Threshold=" + spiderTickThreshold
                            + " dY=" + String.format("%.3f", deltaY));
                    data.spiderTicks = 5;
                    if (data.spiderViolations > plugin.getSpiderViolationLimit()) {
                        plugin.punishPlayer(player, "Spider (WallClimb)", data.spiderViolations);
                    }
                }
            } else {
                data.spiderTicks = Math.max(0, data.spiderTicks - 1);
            }
        } else if (deltaY <= 0) {
            data.spiderTicks = Math.max(0, data.spiderTicks - 1);
        }
    }

    public void checkFlight(PlayerMoveEvent event, PlayerData data) {
        if (!plugin.isCheckFlightEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 2) return;
        if (plugin.shouldBypassChecks(event.getPlayer())) return;

        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (BlockUtils.isClimbable(player.getLocation().getBlock())
                || BlockUtils.isClimbable(player.getLocation().getBlock().getRelative(BlockFace.DOWN))) {
            data.flyViolations = 0;
            data.hoverTicks = 0;
            return;
        }

        if (player.isGliding()) {
            data.isGliding = true;
            data.lastGlideTime = System.currentTimeMillis();
            data.flyViolations = 0;
            data.hoverTicks = 0;
            return;
        }
        if (System.currentTimeMillis() - data.lastGlideTime < 3000) {
            data.flyViolations = 0;
            data.hoverTicks = 0;
            return;
        }
        if (data.isGliding && isServerOnGround(player)) {
            data.isGliding = false;
        }

        if (player.isRiptiding() || data.isWindBursting || (System.currentTimeMillis() - data.lastBreezeBoostTime < 14000)) {
            data.flyViolations = 0;
            data.hoverTicks = 0;
            return;
        }
        if (isMovementExempt(data)) {
            return;
        }

        if (player.getAllowFlight() || player.isSwimming() || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || BlockUtils.isInLiquid(player) || player.isInsideVehicle()) {
            data.flyViolations = 0;
            data.hoverTicks = 0;
            return;
        }

        double deltaY = to.getY() - from.getY();
        boolean isHighPower = plugin.getHardwareManager().isHighPerformance();
        boolean wasOnGround = data.wasOnGround;
        boolean serverOnGround = isServerOnGround(player);
        data.wasOnGround = serverOnGround;

        if (!serverOnGround) {
            data.airborneTicks++;
        } else {
            data.airborneTicks = 0;
            data.hoverTicks = 0;
            data.averageVerticalDelta = 0.0;
        }

        double serverTps = PingUtils.getServerTps();

        // Hover / controlled float: near-zero vertical while airborne for sustained ticks
        if (!serverOnGround && data.airborneTicks > 8 && Math.abs(deltaY) < 0.03) {
            data.hoverTicks++;
            int hoverThreshold = Math.max(12, 18 + (5 - plugin.getFlightSeverity()));
            if (data.hoverTicks > hoverThreshold) {
                data.flyViolations++;
                data.hoverTicks = hoverThreshold / 2;
                plugin.logToFile(player.getName(), "CHECK:Flight (Hover) VIO=" + data.flyViolations
                        + " AirTicks=" + data.airborneTicks + " dY=" + String.format("%.3f", deltaY));
                if (data.flyViolations > plugin.getFlyViolationLimit()) {
                    plugin.punishPlayer(player, "Flight", data.flyViolations);
                }
            }
        } else if (deltaY < -0.08) {
            data.hoverTicks = Math.max(0, data.hoverTicks - 2);
        }

        if (isHighPower) {
            if (!serverOnGround && !wasOnGround && deltaY > 0 && data.airborneTicks > 1) {
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

                if (deltaY > adaptiveThreshold) {
                    data.flyViolations++;
                    plugin.logToFile(player.getName(), "CHECK:Flight (Predictive) VIO=" + data.flyViolations
                            + " Y=" + String.format("%.3f", deltaY)
                            + " ExpectedY=" + String.format("%.3f", expectedY)
                            + " Ping=" + ping + "ms TPS=" + String.format("%.2f", serverTps));
                    if (data.flyViolations > plugin.getFlyViolationLimit()) {
                        plugin.punishPlayer(player, "Flight", data.flyViolations);
                    }
                }
            } else if (serverOnGround) {
                if (data.flyViolations > 0) data.flyViolations--;
            }
        } else {
            if (!serverOnGround && deltaY > 0.05) {
                double tpsMargin = serverTps < 18.0 ? (18.0 - serverTps) * 0.02 : 0.0;
                if (PingUtils.isGeyserPlayer(player)) {
                    tpsMargin += 0.04;
                }
                double severityScale = 1.0 + (5 - plugin.getFlightSeverity()) * 0.06;
                double adaptiveThreshold = Math.max(MAX_FALL_DISTANCE, data.averageVerticalDelta + 0.12 * severityScale + tpsMargin);
                if (deltaY > adaptiveThreshold) {
                    data.flyViolations++;
                    plugin.logToFile(player.getName(), "CHECK:Flight (Predictive) VIO=" + data.flyViolations
                            + " Y=" + String.format("%.3f", deltaY) + " TPS=" + String.format("%.2f", serverTps));
                    if (data.flyViolations > plugin.getFlyViolationLimit()) {
                        plugin.punishPlayer(player, "Flight", data.flyViolations);
                    }
                }
            } else if (serverOnGround) {
                data.flyViolations = 0;
            }
        }

        data.averageVerticalDelta = PredictionUtils.exponentialMovingAverage(data.averageVerticalDelta, deltaY, 0.22);
        data.lastDeltaY = deltaY;

        checkNoFall(player, data, deltaY, serverOnGround, wasOnGround);
    }

    /**
     * Compare accumulated server-side fall distance against whether FALL damage occurred.
     */
    private void checkNoFall(Player player, PlayerData data, double deltaY, boolean serverOnGround, boolean wasOnGround) {
        if (!plugin.isCheckFlightEnabled()) return;
        if (plugin.shouldBypassChecks(player)) return;
        if (isMovementExempt(data)) {
            data.serverFallDistance = 0.0;
            return;
        }
        if (player.getAllowFlight() || player.isGliding() || player.isInsideVehicle()
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || BlockUtils.isInLiquid(player) || BlockUtils.isClimbable(player.getLocation().getBlock())) {
            data.serverFallDistance = 0.0;
            data.tookFallDamage = false;
            return;
        }

        if (!serverOnGround && deltaY < -0.05) {
            data.serverFallDistance += -deltaY;
        }

        if (!wasOnGround && serverOnGround) {
            double safeFall = 3.0;
            AttributeInstance safeAttr = player.getAttribute(Attribute.GENERIC_SAFE_FALL_DISTANCE);
            if (safeAttr != null) {
                safeFall = Math.max(2.0, safeAttr.getValue());
            }
            // Feather Falling / boots reduce damage; require a larger drop before flagging
            ItemStack boots = player.getInventory().getBoots();
            if (boots != null && boots.containsEnchantment(Enchantment.FEATHER_FALLING)) {
                safeFall += boots.getEnchantmentLevel(Enchantment.FEATHER_FALLING) * 1.5;
            }

            double expectedDamageFall = data.serverFallDistance - safeFall;
            boolean recentFallDamage = data.tookFallDamage
                    || (System.currentTimeMillis() - data.lastFallDamageTime < 750);

            if (expectedDamageFall > 1.5 && !recentFallDamage) {
                data.noFallViolations++;
                plugin.logToFile(player.getName(), "CHECK:NoFall VIO=" + data.noFallViolations
                        + " FallDist=" + String.format("%.2f", data.serverFallDistance)
                        + " Safe=" + String.format("%.2f", safeFall));
                if (data.noFallViolations > plugin.getNoFallViolationLimit()) {
                    plugin.punishPlayer(player, "NoFall", data.noFallViolations);
                } else {
                    player.sendMessage(plugin.getPrefix() + ChatColor.RED
                            + "Warning! Suspicious fall (no damage). ("
                            + data.noFallViolations + "/" + plugin.getNoFallViolationLimit() + ")");
                }
            } else if (recentFallDamage && data.noFallViolations > 0) {
                data.noFallViolations = Math.max(0, data.noFallViolations - 1);
            }

            data.serverFallDistance = 0.0;
            data.tookFallDamage = false;
        } else if (serverOnGround) {
            data.serverFallDistance = 0.0;
        }
    }

    public void checkSpeed(PlayerMoveEvent event, PlayerData data) {
        if (!plugin.isCheckSpeedEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 2) return;
        if (plugin.shouldBypassChecks(event.getPlayer())) return;
        if (isMovementExempt(data)) return;

        Player player = event.getPlayer();

        if (player.isGliding()) return;
        if (player.isRiptiding()) return;
        if (player.isSwimming()) return;
        if (BlockUtils.isInLiquid(player)) return;

        if (player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) return;

        if (player.getAllowFlight() || player.isFlying() || player.isInsideVehicle()) return;

        if (data.isWindBursting || (System.currentTimeMillis() - data.lastBreezeBoostTime < 4000)) return;

        if (data.glideEndTime > 0 && (System.currentTimeMillis() - data.glideEndTime < plugin.getGlideGracePeriodMs())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        double deltaX = to.getX() - from.getX();
        double deltaZ = to.getZ() - from.getZ();
        double horizontalDistance = Math.hypot(deltaX, deltaZ);

        boolean serverOnGround = isServerOnGround(player);
        double speedLimit = serverOnGround ? plugin.getBaseSpeedLimit() : plugin.getBaseSpeedLimit() * 0.92;

        Block blockBelow = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        if (BlockUtils.isIce(blockBelow)) {
            speedLimit = plugin.getIceSpeedLimit();
        } else if (BlockUtils.isHoney(blockBelow) || BlockUtils.isHoney(player.getLocation().getBlock())) {
            speedLimit = Math.min(speedLimit, 0.18);
        }

        // Scale with movement_speed attribute (accounts for Speed potion, gear, etc.)
        AttributeInstance moveAttr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (moveAttr != null) {
            double vanillaWalk = 0.1;
            double attrScale = Math.max(0.5, Math.min(3.5, moveAttr.getValue() / vanillaWalk));
            speedLimit *= attrScale;
        } else if (player.hasPotionEffect(PotionEffectType.SPEED)) {
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

        // Cap lag inflation so one-shot stalls cannot balloon the speed ceiling
        double maxLagTicks = Math.max(3.0, Math.min(8.0, (ping + 100) / 50.0 + (20.0 - serverTps) * 0.4));
        if (PingUtils.isGeyserPlayer(player)) {
            maxLagTicks = Math.min(10.0, maxLagTicks + 2.0);
        }
        ticksElapsed = Math.min(ticksElapsed, maxLagTicks);

        boolean isHighPower = plugin.getHardwareManager().isHighPerformance();
        double speedSeverityOffset = (5 - plugin.getSpeedSeverity()) * 0.04;

        if (isHighPower) {
            speedLimit -= 0.05;
        }
        speedLimit += speedSeverityOffset;

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

        double predictedHorizontal = PredictionUtils.predictGroundSpeed(
                data.lastHorizontalDistance, data.averageHorizontalSpeed, data.horizontalSpeedVariance);
        // Hard-cap: EMA prediction must not raise the allowed ceiling above the attribute-aware limit
        double adaptiveLimit = speedLimit;

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
        if (plugin.shouldBypassChecks(event.getPlayer())) return;
        if (isMovementExempt(data)) return;

        Player player = event.getPlayer();

        if (player.getAllowFlight() || player.isFlying()) return;
        if (player.isSwimming()) return;
        if (player.isRiptiding()) return;
        if (BlockUtils.isInLiquid(player)) return;
        if (player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ()) {
            return;
        }

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
            // Multi-ray: feet, mid, eyes
            double[] yOffsets = {0.1, 0.9, 1.6};
            for (double yOff : yOffsets) {
                Location traceStart = from.clone().add(0, yOff, 0);
                RayTraceResult trace = player.getWorld().rayTraceBlocks(traceStart, dir, dist, FluidCollisionMode.NEVER, true);
                if (trace != null && trace.getHitBlock() != null) {
                    Block hit = trace.getHitBlock();
                    if (hit.getType().isOccluding() && !BlockUtils.isPartialHeightBlock(hit)) {
                        event.setTo(from);
                        player.sendMessage(plugin.getPrefix() + ChatColor.RED + "Hey! You can't phase through blocks like that!");
                        plugin.logToFile(player.getName(), "CHECK:Phase - RayTrace Intersected " + hit.getType().name()
                                + " at yOff=" + yOff + " Ping=" + ping + "ms");
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

    /**
     * Server-side ground estimate from collision under feet (not client isOnGround).
     */
    public static boolean isServerOnGround(Player player) {
        Location loc = player.getLocation();
        Block below = loc.clone().subtract(0, 0.2, 0).getBlock();
        if (!below.isPassable() && below.getType().isSolid()) {
            return true;
        }
        Block feet = loc.getBlock();
        return !feet.isPassable() && feet.getType().isSolid();
    }
}
