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
            if (timeSinceDamage > MAX_SWING_DELAY_MS) {
                data.sequenceViolations++;
                plugin.logToFile(player.getName(), "CHECK:Sequence VIO=" + data.sequenceViolations + " Delay=" + timeSinceDamage + "ms");
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

        if (data.lastAttackTime > 0 && timeSinceLastAttack < MIN_ATTACK_DELAY_MS) {
            data.attackSpeedViolations++;
            plugin.logToFile(attacker.getName(), "CHECK:AttackSpeed VIO=" + data.attackSpeedViolations + " Delay=" + timeSinceLastAttack + "ms");
            if (data.attackSpeedViolations > plugin.getAttackSpeedViolationLimit()) {
                if (currentTime - data.lastAttackSpeedViolationTime > TimeUnit.SECONDS.toMillis(5)) {
                    plugin.punishPlayer(attacker, "Attack Speed (Autoclicker)", data.attackSpeedViolations);
                    data.lastAttackSpeedViolationTime = currentTime;
                }
            } else {
                attacker.sendMessage(plugin.getPrefix() + ChatColor.RED + "Warning! You're attacking too fast. (" + data.attackSpeedViolations + "/" + plugin.getAttackSpeedViolationLimit() + ")");
            }
        } else if (data.attackSpeedViolations > 0 && timeSinceLastAttack > MIN_ATTACK_DELAY_MS * 2) {
            data.attackSpeedViolations = Math.max(0, data.attackSpeedViolations - 1);
        }
        data.lastAttackTime = currentTime;
    }
}
