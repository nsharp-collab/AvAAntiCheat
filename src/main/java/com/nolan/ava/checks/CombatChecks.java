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
import com.nolan.ava.util.PingUtils;
import com.nolan.ava.util.PredictionUtils;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Combat-related checks: attack sequence, attack speed, reach/LOS, multi-hit.
 */
public class CombatChecks {

    private static final long MAX_SWING_DELAY_MS = 350;
    private static final long MIN_ATTACK_DELAY_MS = 100;
    private static final long MULTI_HIT_WINDOW_MS = 350;

    private final AvAAntiCheat plugin;

    public CombatChecks(AvAAntiCheat plugin) {
        this.plugin = plugin;
    }

    /**
     * Called at damage time. Flags if there was no arm swing within the allowed window.
     */
    public void checkAttackSequence(Player player, PlayerData data) {
        if (!plugin.isCheckCombatEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 3) return;
        if (plugin.shouldBypassChecks(player)) return;

        long now = System.currentTimeMillis();
        long timeSinceSwing = now - data.lastSwingTime;
        long maxSwingDelay = Math.max(150, MAX_SWING_DELAY_MS + (5 - plugin.getCombatSeverity()) * 20L);
        int ping = PingUtils.getPlayerPing(player);
        maxSwingDelay += Math.min(150, ping / 2L);
        if (PingUtils.isGeyserPlayer(player)) {
            maxSwingDelay += 50;
        }

        if (timeSinceSwing > maxSwingDelay) {
            data.sequenceViolations++;
            plugin.logToFile(player.getName(), "CHECK:Sequence VIO=" + data.sequenceViolations
                    + " SinceSwing=" + timeSinceSwing + "ms Threshold=" + maxSwingDelay);
            if (data.sequenceViolations > plugin.getSequenceViolationLimit()) {
                plugin.punishPlayer(player, "Illegal Attack Sequence", data.sequenceViolations);
            } else {
                player.sendMessage(plugin.getPrefix() + ChatColor.YELLOW
                        + "Warning: Suspicious attack sequence detected. (Swing check failed)");
            }
        }
    }

    public void checkAttackSpeed(Player attacker, PlayerData data) {
        if (!plugin.isCheckCombatEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 3) return;
        if (plugin.shouldBypassChecks(attacker)) return;

        long currentTime = System.currentTimeMillis();
        long timeSinceLastAttack = currentTime - data.lastAttackTime;
        int ping = PingUtils.getPlayerPing(attacker);
        double serverTps = PingUtils.getServerTps();

        if (data.lastAttackTime > 0) {
            double intervalSeconds = timeSinceLastAttack / 1000.0;
            data.attackIntervalAverage = PredictionUtils.exponentialMovingAverage(data.attackIntervalAverage, intervalSeconds, 0.2);
            data.attackIntervalVariance = PredictionUtils.updateVariance(data.attackIntervalVariance, data.attackIntervalAverage, intervalSeconds, 0.2);

            double predictedInterval = PredictionUtils.predictAttackInterval(data.attackIntervalAverage, data.attackIntervalVariance);
            double severityFactor = Math.max(0.75, Math.min(1.25, 1.0 - (plugin.getCombatSeverity() - 5) * 0.05));
            double adaptiveDelay = Math.max(MIN_ATTACK_DELAY_MS / 1000.0, predictedInterval * severityFactor);
            adaptiveDelay += (ping > 250 ? 0.02 : 0.0);
            adaptiveDelay += (serverTps < 18.0 ? (18.0 - serverTps) * 0.005 : 0.0);
            if (PingUtils.isGeyserPlayer(attacker)) {
                adaptiveDelay += 0.03;
            }

            // Soft cooldown floor from attack cooldown attribute when available
            float cooldown = attacker.getAttackCooldown();
            boolean cooldownViolated = cooldown < 0.85f && intervalSeconds < 0.45;

            boolean isTooFast = intervalSeconds < adaptiveDelay || cooldownViolated;
            double consistencyThreshold = 0.04 + (5 - plugin.getCombatSeverity()) * 0.01;
            boolean isConsistent = data.attackIntervalVariance < consistencyThreshold;
            int requiredSuspicion = Math.max(1, 3 - plugin.getCombatSeverity() / 3);

            // Low variance strengthens suspicion; humanized clickers still accumulate more slowly
            if (isTooFast && isConsistent) {
                data.attackSpeedSuspicion += 2;
            } else if (isTooFast) {
                data.attackSpeedSuspicion++;
            } else {
                data.attackSpeedSuspicion = Math.max(0, data.attackSpeedSuspicion - 1);
            }

            if (isTooFast && data.attackSpeedSuspicion > requiredSuspicion) {
                data.attackSpeedViolations++;
                plugin.logToFile(attacker.getName(), "CHECK:AttackSpeed (Predictive) VIO=" + data.attackSpeedViolations
                        + " Delay=" + timeSinceLastAttack + "ms Pred=" + String.format("%.3f", predictedInterval * 1000.0)
                        + "ms Var=" + String.format("%.4f", data.attackIntervalVariance)
                        + " CD=" + String.format("%.2f", cooldown)
                        + " Ping=" + ping + "ms TPS=" + String.format("%.2f", serverTps));
                if (data.attackSpeedViolations > plugin.getAttackSpeedViolationLimit()) {
                    if (currentTime - data.lastAttackSpeedViolationTime > TimeUnit.SECONDS.toMillis(5)) {
                        plugin.punishPlayer(attacker, "Attack Speed (Autoclicker)", data.attackSpeedViolations);
                        data.lastAttackSpeedViolationTime = currentTime;
                    }
                } else {
                    attacker.sendMessage(plugin.getPrefix() + ChatColor.RED + "Warning! Suspicious attack rhythm detected. ("
                            + data.attackSpeedViolations + "/" + plugin.getAttackSpeedViolationLimit() + ")");
                }
            } else if (data.attackSpeedViolations > 0 && timeSinceLastAttack > MIN_ATTACK_DELAY_MS * 2) {
                data.attackSpeedViolations = Math.max(0, data.attackSpeedViolations - 1);
            }
        }

        data.lastAttackTime = currentTime;
    }

    /**
     * Eye-to-hitbox reach with ping margin, plus occluding-block LOS ray.
     */
    public void checkReachAndLos(Player attacker, Entity victim, PlayerData data) {
        if (!plugin.isCheckCombatEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 3) return;
        if (plugin.shouldBypassChecks(attacker)) return;
        if (!(victim instanceof LivingEntity)) return;

        Location eye = attacker.getEyeLocation();
        Location target = ((LivingEntity) victim).getEyeLocation();
        // Closest point approximation: use body midpoint for large entities
        Location body = victim.getLocation().add(0, Math.min(1.0, victim.getHeight() * 0.5), 0);

        double distEye = eye.distance(body);
        double maxReach = 3.1;
        AttributeInstance reachAttr = attacker.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE);
        if (reachAttr != null) {
            maxReach = Math.max(3.0, reachAttr.getValue());
        }

        int ping = PingUtils.getPlayerPing(attacker);
        // Ping rewind margin: ~ player can be slightly farther due to latency
        maxReach += Math.min(1.2, ping / 250.0);
        if (PingUtils.isGeyserPlayer(attacker)) {
            maxReach += 0.35;
        }
        maxReach += (5 - plugin.getCombatSeverity()) * 0.08;

        if (distEye > maxReach + 0.15) {
            data.reachViolations++;
            plugin.logToFile(attacker.getName(), "CHECK:Reach VIO=" + data.reachViolations
                    + " Dist=" + String.format("%.2f", distEye) + " Max=" + String.format("%.2f", maxReach)
                    + " Ping=" + ping + "ms");
            if (data.reachViolations > plugin.getReachViolationLimit()) {
                plugin.punishPlayer(attacker, "Reach", data.reachViolations);
            } else if (data.reachViolations >= 2) {
                attacker.sendMessage(plugin.getPrefix() + ChatColor.RED + "Warning! Suspicious reach. ("
                        + data.reachViolations + "/" + plugin.getReachViolationLimit() + ")");
            }
        } else if (data.reachViolations > 0) {
            data.reachViolations = Math.max(0, data.reachViolations - 1);
        }

        // LOS: ray from eye toward target; flag if solid occluding block is hit before the entity
        Vector dir = target.toVector().subtract(eye.toVector());
        double losDist = dir.length();
        if (losDist < 0.2) return;
        dir.normalize();

        RayTraceResult blockHit = attacker.getWorld().rayTraceBlocks(eye, dir, losDist - 0.15,
                FluidCollisionMode.NEVER, true);
        if (blockHit != null && blockHit.getHitBlock() != null
                && blockHit.getHitBlock().getType().isOccluding()) {
            // Allow thin/partial blocks via name heuristics already used elsewhere
            String name = blockHit.getHitBlock().getType().name();
            if (name.contains("SLAB") || name.contains("STAIR") || name.contains("FENCE")
                    || name.contains("WALL") || name.contains("PANE") || name.contains("DOOR")
                    || name.contains("TRAPDOOR") || name.contains("GATE")) {
                return;
            }
            data.reachViolations++;
            plugin.logToFile(attacker.getName(), "CHECK:LOS VIO=" + data.reachViolations
                    + " Block=" + blockHit.getHitBlock().getType().name()
                    + " Dist=" + String.format("%.2f", losDist));
            if (data.reachViolations > plugin.getLosViolationLimit()) {
                plugin.punishPlayer(attacker, "Hit Through Blocks (LOS)", data.reachViolations);
            }
        }
    }

    /**
     * Multiple distinct living targets hit within a short window (multi-aura signal).
     */
    public void checkMultiHit(Player attacker, Entity victim, PlayerData data) {
        if (!plugin.isCheckCombatEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 3) return;
        if (plugin.shouldBypassChecks(attacker)) return;
        if (!(victim instanceof LivingEntity)) return;

        long now = System.currentTimeMillis();
        UUID targetId = victim.getUniqueId();

        if (now - data.multiHitWindowStart > MULTI_HIT_WINDOW_MS) {
            data.multiHitWindowStart = now;
            data.multiHitCount = 1;
            data.multiHitLastTarget = targetId;
            return;
        }

        if (data.multiHitLastTarget != null && !data.multiHitLastTarget.equals(targetId)) {
            data.multiHitCount++;
            data.multiHitLastTarget = targetId;
            if (data.multiHitCount >= 3) {
                data.multiHitViolations++;
                plugin.logToFile(attacker.getName(), "CHECK:MultiHit VIO=" + data.multiHitViolations
                        + " Targets=" + data.multiHitCount + " WindowMs=" + MULTI_HIT_WINDOW_MS);
                data.multiHitCount = 0;
                data.multiHitWindowStart = now;
                if (data.multiHitViolations > plugin.getMultiAuraViolationLimit()) {
                    plugin.punishPlayer(attacker, "MultiAura", data.multiHitViolations);
                } else {
                    attacker.sendMessage(plugin.getPrefix() + ChatColor.RED
                            + "Warning! Suspicious multi-target attacks. ("
                            + data.multiHitViolations + "/" + plugin.getMultiAuraViolationLimit() + ")");
                }
            }
        } else {
            data.multiHitLastTarget = targetId;
        }
    }
}
