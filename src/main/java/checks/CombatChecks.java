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
import com.nolan.ava.util.PingUtils;
import com.nolan.ava.util.PredictionUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

/**
 * Combat-related checks: illegal attack sequence (hit without a swing
 * animation) and attack speed (autoclicker) detection.
 */
public class CombatChecks {

    private static final long MAX_SWING_DELAY_MS = 200;
    private static final long MIN_ATTACK_DELAY_MS = 100;

    private final AvAAntiCheat plugin;

    public CombatChecks(AvAAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void checkAttackSequence(Player player, PlayerData data) {
        if (!plugin.isCheckCombatEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 3) return;
        if (plugin.shouldBypassChecks(data)) return;

        if (data.lastDamageTime > 0) {
            long timeSinceDamage = System.currentTimeMillis() - data.lastDamageTime;
            long maxSwingDelay = Math.max(100, MAX_SWING_DELAY_MS + (5 - plugin.getCombatSeverity()) * 10);
            if (timeSinceDamage > maxSwingDelay) {
                data.sequenceViolations++;
                plugin.logToFile(player.getName(), "CHECK:Sequence VIO=" + data.sequenceViolations + " Delay=" + timeSinceDamage + "ms Threshold=" + maxSwingDelay);
                if (data.sequenceViolations > plugin.getSequenceViolationLimit()) {
                    plugin.punishPlayer(player, "Illegal Attack Sequence", data.sequenceViolations);
                } else {
                    player.sendMessage(plugin.getPrefix() + ChatColor.YELLOW + "Warning: Suspicious attack sequence detected. (Swing check failed)");
                }
            }
            data.lastDamageTime = 0;
        }
    }

    public void checkAttackSpeed(Player attacker, PlayerData data) {
        if (!plugin.isCheckCombatEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 3) return;
        if (plugin.shouldBypassChecks(data)) return;

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

            boolean isTooFast = intervalSeconds < adaptiveDelay;
            double consistencyThreshold = 0.02 + (5 - plugin.getCombatSeverity()) * 0.01;
            boolean isConsistent = data.attackIntervalVariance < consistencyThreshold;
            int requiredSuspicion = Math.max(1, 3 - plugin.getCombatSeverity() / 3);

            if (isTooFast && isConsistent) {
                data.attackSpeedSuspicion++;
            } else {
                data.attackSpeedSuspicion = Math.max(0, data.attackSpeedSuspicion - 1);
            }

            if (isTooFast && data.attackSpeedSuspicion > requiredSuspicion) {
                data.attackSpeedViolations++;
                plugin.logToFile(attacker.getName(), "CHECK:AttackSpeed (Predictive) VIO=" + data.attackSpeedViolations + " Delay=" + timeSinceLastAttack + "ms Pred=" + String.format("%.3f", predictedInterval * 1000.0) + "ms Var=" + String.format("%.4f", data.attackIntervalVariance) + " Ping=" + ping + "ms TPS=" + String.format("%.2f", serverTps));
                if (data.attackSpeedViolations > plugin.getAttackSpeedViolationLimit()) {
                    if (currentTime - data.lastAttackSpeedViolationTime > TimeUnit.SECONDS.toMillis(5)) {
                        plugin.punishPlayer(attacker, "Attack Speed (Autoclicker)", data.attackSpeedViolations);
                        data.lastAttackSpeedViolationTime = currentTime;
                    }
                } else {
                    attacker.sendMessage(plugin.getPrefix() + ChatColor.RED + "Warning! Suspicious attack rhythm detected. (" + data.attackSpeedViolations + "/" + plugin.getAttackSpeedViolationLimit() + ")");
                }
            } else if (data.attackSpeedViolations > 0 && timeSinceLastAttack > MIN_ATTACK_DELAY_MS * 2) {
                data.attackSpeedViolations = Math.max(0, data.attackSpeedViolations - 1);
            }
        }

        data.lastAttackTime = currentTime;
    }
}
