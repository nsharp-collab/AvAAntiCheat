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

package com.nolan.ava.data;

import org.bukkit.boss.BossBar;

import java.util.UUID;

/**
 * Holds all per-player runtime tracking state used by the various checks.
 * One instance lives per online player, keyed by UUID in AvAAntiCheat#playerDataMap.
 */
public class PlayerData {

    public String clientBrand = "Unknown"; // 1.21 Brand tracking

    public int flyViolations = 0;
    public int spiderViolations = 0;
    public int spiderTicks = 0;
    public int speedViolations = 0;
    public int spamViolations = 0;
    public int dupeViolations = 0;

    public long lastChatTime = 0;
    public String lastMessage = "";

    public long combatEndTime = 0;
    public long lastDamageTime = 0;
    public UUID lastAttacker = null;
    public BossBar combatBossBar = null;

    public int sequenceViolations = 0;
    public long lastAttackTime = 0;
    public int attackSpeedViolations = 0;
    public long lastAttackSpeedViolationTime = 0;

    public boolean isGliding = false;
    public long lastGlideTime = 0;
    public long glideEndTime = 0;

    public boolean isRiptiding = false;
    public boolean isWindBursting = false;

    public long lastBreezeBoostTime = 0;
    public long lastVelocityTime = 0;
    public long lastCombatActivityTime = 0;
    public boolean bypassAllChecks = false;

    public long lastMoveTime = System.currentTimeMillis();
    public double lastDeltaY = 0.0;
    public boolean wasOnGround = true;

    public boolean isInCombat() {
        return System.currentTimeMillis() < combatEndTime;
    }
}
