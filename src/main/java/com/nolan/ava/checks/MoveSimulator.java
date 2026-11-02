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
import com.nolan.ava.util.PredictionUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Experimental server-authoritative move simulator (default off).
 *
 * When enabled, compares observed movement to a simple predicted residual and
 * logs mismatches. Punishments stay off while experimental.log-only is true.
 */
public class MoveSimulator {

    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;

    private final AvAAntiCheat plugin;

    public MoveSimulator(AvAAntiCheat plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.isCheckMoveSimulatorEnabled();
    }

    public void onPlayerMove(PlayerMoveEvent event, PlayerData data) {
        if (!isEnabled()) return;
        Player player = event.getPlayer();
        if (plugin.shouldBypassChecks(player)) return;
        if (plugin.getCurrentAntiCheatMode() == 0) return;
        if (player.getAllowFlight() || player.isFlying() || player.isGliding() || player.isInsideVehicle()
                || BlockUtils.isInLiquid(player) || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            resetState(data);
            return;
        }
        if (System.currentTimeMillis() < data.velocityExemptUntil) {
            // Velocity compliance window: sync predicted velocity to observed after knockback
            Location to = event.getTo();
            if (to != null) {
                double obsDy = to.getY() - event.getFrom().getY();
                syncState(data, to, obsDy);
            }
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        double obsDx = to.getX() - from.getX();
        double obsDy = to.getY() - from.getY();
        double obsDz = to.getZ() - from.getZ();

        if (!data.simInitialized) {
            syncState(data, to, obsDy);
            return;
        }

        double gravity = GRAVITY;
        AttributeInstance gravityAttr = player.getAttribute(Attribute.GENERIC_GRAVITY);
        if (gravityAttr != null && gravityAttr.getValue() > 0) {
            // GENERIC_GRAVITY default is 0.08 — use it when present
            gravity = gravityAttr.getValue();
        }

        double expectedDy = (data.simVelY - gravity) * DRAG;
        boolean serverOnGround = MovementChecks.isServerOnGround(player);
        if (data.wasOnGround && !serverOnGround && obsDy > 0.0) {
            double jump = 0.42;
            AttributeInstance jumpAttr = player.getAttribute(Attribute.GENERIC_JUMP_STRENGTH);
            if (jumpAttr != null) {
                jump = Math.max(0.3, jumpAttr.getValue());
            }
            expectedDy = Math.max(expectedDy, jump);
        }

        double predictedHorizontal = PredictionUtils.predictGroundSpeed(
                data.lastHorizontalDistance, data.averageHorizontalSpeed, data.horizontalSpeedVariance);
        double attrCap = plugin.getBaseSpeedLimit();
        AttributeInstance moveAttr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (moveAttr != null) {
            attrCap *= Math.max(0.5, Math.min(3.5, moveAttr.getValue() / 0.1));
        }
        double obsHorizontal = Math.hypot(obsDx, obsDz);

        double verticalResidual = Math.abs(obsDy - expectedDy);
        double horizontalResidual = Math.max(0.0, obsHorizontal - Math.max(predictedHorizontal, attrCap));

        data.simResidualAverage = PredictionUtils.exponentialMovingAverage(
                data.simResidualAverage, verticalResidual + horizontalResidual, 0.25);

        if (verticalResidual > 0.35 || horizontalResidual > 0.45) {
            data.simMismatchStreak++;
            if (data.simMismatchStreak >= 8) {
                String msg = "EXPERIMENTAL MoveSimulator residual V=" + String.format("%.3f", verticalResidual)
                        + " H=" + String.format("%.3f", horizontalResidual)
                        + " avg=" + String.format("%.3f", data.simResidualAverage)
                        + " obsH=" + String.format("%.3f", obsHorizontal)
                        + " expectedDy=" + String.format("%.3f", expectedDy);
                plugin.logToFile(player.getName(), msg);
                if (!plugin.isExperimentalLogOnly()) {
                    player.sendMessage(plugin.getPrefix() + ChatColor.GRAY
                            + "[Experimental] Movement residual logged.");
                }
                data.simMismatchStreak = 0;
            }
        } else {
            data.simMismatchStreak = Math.max(0, data.simMismatchStreak - 1);
        }

        syncState(data, to, obsDy);
    }

    private void syncState(PlayerData data, Location to, double dy) {
        data.simX = to.getX();
        data.simY = to.getY();
        data.simZ = to.getZ();
        data.simVelY = dy;
        data.simInitialized = true;
    }

    private void resetState(PlayerData data) {
        data.simInitialized = false;
        data.simMismatchStreak = 0;
        data.simVelY = 0.0;
    }

    public void applyKnockbackHint(PlayerData data, Vector velocity) {
        if (!isEnabled() || data == null || velocity == null) return;
        data.simVelY = velocity.getY();
        data.simInitialized = true;
    }
}
