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

package com.nolan.ava.data;

import org.bukkit.boss.BossBar;

import java.util.UUID;

/**
 * Holds all per-player runtime tracking state used by the various checks.
 * One instance lives per online player, keyed by UUID in AvAAntiCheat#playerDataMap.
 */
public class PlayerData {

    public String clientBrand = "Unknown";

    public int flyViolations = 0;
    public int spiderViolations = 0;
    public int spiderTicks = 0;
    public int speedViolations = 0;
    public int spamViolations = 0;
    public int dupeViolations = 0;
    public long lastDupeSuspicionTime = 0;

    public long lastChatTime = 0;
    public String lastMessage = "";

    public long combatEndTime = 0;
    public long lastDamageTime = 0;
    public long lastSwingTime = System.currentTimeMillis();
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
    /** Absolute timestamp until which movement checks skip velocity-sensitive logic. */
    public long velocityExemptUntil = 0;
    public long lastCombatActivityTime = 0;
    /** Absolute timestamp until which movement checks ignore post-teleport deltas. */
    public long teleportExemptUntil = 0;

    public int inventoryWalkStreak = 0;

    public long lastMoveTime = System.currentTimeMillis();
    public double lastDeltaY = 0.0;
    public double lastHorizontalDistance = 0.0;
    public double averageHorizontalSpeed = 0.0;
    public double horizontalSpeedVariance = 0.0;
    public double averageVerticalDelta = 0.0;
    public int airborneTicks = 0;
    public int hoverTicks = 0;

    /** Server-accumulated fall distance while airborne (blocks). */
    public double serverFallDistance = 0.0;
    /** True after legitimate FALL damage until next takeoff. */
    public boolean tookFallDamage = false;
    public long lastFallDamageTime = 0;
    public int noFallViolations = 0;

    public double attackIntervalAverage = 0.0;
    public double attackIntervalVariance = 0.0;
    public int attackSpeedSuspicion = 0;
    public boolean wasOnGround = true;

    public int reachViolations = 0;
    public int multiHitViolations = 0;
    public long multiHitWindowStart = 0;
    public int multiHitCount = 0;
    public UUID multiHitLastTarget = null;

    public int scaffoldPlaceCount = 0;
    public long scaffoldWindowStart = 0;
    public int scaffoldViolations = 0;

    // Experimental: last observed move for duplicate-packet placeholder
    public double lastPacketX = Double.NaN;
    public double lastPacketY = Double.NaN;
    public double lastPacketZ = Double.NaN;
    public int duplicateMoveStreak = 0;

    // Experimental PacketChecks state
    public long lastPacketMoveTime = System.currentTimeMillis();
    public long packetWindowStartMs = System.currentTimeMillis();
    public int packetMoveCountWindow = 0;
    public int packetFlyingCountWindow = 0;
    public int onGroundMismatchStreak = 0;
    public long lastSwingPacketTime = 0;
    public long lastAttackPacketTime = 0;

    // Experimental MoveSimulator state
    public boolean simInitialized = false;
    public double simX = 0.0;
    public double simY = 0.0;
    public double simZ = 0.0;
    public double simVelY = 0.0;
    public double simResidualAverage = 0.0;
    public int simMismatchStreak = 0;

    public boolean isInCombat() {
        return System.currentTimeMillis() < combatEndTime;
    }

    /**
     * Extend velocity exemption to at least {@code now + durationMs}, never shortening an existing window.
     */
    public void extendVelocityExempt(long durationMs) {
        long until = System.currentTimeMillis() + Math.max(0L, durationMs);
        if (until > velocityExemptUntil) {
            velocityExemptUntil = until;
        }
        lastVelocityTime = System.currentTimeMillis();
    }

    /**
     * Short grace after plugin/vanilla teleports so speed/phase/flight don't false-flag.
     */
    public void extendTeleportExempt(long durationMs) {
        long until = System.currentTimeMillis() + Math.max(0L, durationMs);
        if (until > teleportExemptUntil) {
            teleportExemptUntil = until;
        }
    }

    public boolean isTeleportExempt() {
        return System.currentTimeMillis() < teleportExemptUntil;
    }
}
