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

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.nolan.ava.AvAAntiCheat;
import com.nolan.ava.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * PacketEvents receive hooks used by {@link PacketChecks}.
 * Only loaded/registered when the PacketEvents plugin is installed,
 * packet checks are enabled, and the server is Minecraft 1.21.11.
 * Instantiated via reflection so AvAAntiCheat starts without PacketEvents.
 */
public class AvAPacketListener extends PacketListenerAbstract {

    private final AvAAntiCheat plugin;
    private final PacketChecks packetChecks;

    public AvAPacketListener(AvAAntiCheat plugin, PacketChecks packetChecks) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.packetChecks = packetChecks;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!packetChecks.arePacketHooksActive()) return;
        if (plugin.getCurrentAntiCheatMode() == 0) return;

        UUID uuid = event.getUser() != null ? event.getUser().getUUID() : null;
        if (uuid == null) return;

        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;
        if (plugin.shouldBypassChecks(player)) return;

        PlayerData data = plugin.getPlayerData(uuid);
        if (data == null) return;

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            handleFlying(player, data, event);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            handleInteract(player, data, event);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            data.lastSwingPacketTime = System.currentTimeMillis();
        }
    }

    private void handleFlying(Player player, PlayerData data, PacketReceiveEvent event) {
        WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
        long now = System.currentTimeMillis();

        data.packetFlyingCountWindow++;
        data.lastPacketMoveTime = now;

        if (now - data.packetWindowStartMs >= 1000L) {
            int count = data.packetFlyingCountWindow;
            data.packetFlyingCountWindow = 0;
            data.packetWindowStartMs = now;

            // Vanilla clients send ~20 flying packets/s; sustained high rates are timer-like.
            if (count > 45) {
                String msg = "EXPERIMENTAL PacketChecks (PE) timer: flying/s=" + count;
                plugin.logToFile(player.getName(), msg);
                if (!plugin.isExperimentalLogOnly()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.sendMessage(plugin.getPrefix() + ChatColor.GRAY
                                    + "[Experimental] Unusual flying packet rate logged.");
                        }
                    });
                }
            }
        }

        boolean clientOnGround = flying.isOnGround();
        // Defer block lookup to main thread — packet thread must not touch world state unsafely.
        final boolean clientGroundFlag = clientOnGround;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            PlayerData pd = plugin.getPlayerData(player.getUniqueId());
            if (pd == null) return;
            boolean serverGround = MovementChecks.isServerOnGround(player);
            if (clientGroundFlag != serverGround) {
                pd.onGroundMismatchStreak++;
                if (pd.onGroundMismatchStreak >= 20) {
                    plugin.logToFile(player.getName(),
                            "EXPERIMENTAL PacketChecks (PE) onGround mismatch streak="
                                    + pd.onGroundMismatchStreak
                                    + " client=" + clientGroundFlag + " server=" + serverGround);
                    pd.onGroundMismatchStreak = 0;
                }
            } else {
                pd.onGroundMismatchStreak = Math.max(0, pd.onGroundMismatchStreak - 1);
            }
        });

        if (flying.hasPositionChanged()) {
            com.github.retrooper.packetevents.util.Vector3d loc = flying.getLocation().getPosition();
            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();
            if (!Double.isNaN(data.lastPacketX)) {
                double dx = Math.abs(x - data.lastPacketX);
                double dy = Math.abs(y - data.lastPacketY);
                double dz = Math.abs(z - data.lastPacketZ);
                boolean duplicate = dx < 1.0E-6 && dy < 1.0E-6 && dz < 1.0E-6;
                boolean micro = !duplicate && (dx + dy + dz) < 1.0E-4;
                if (duplicate || micro) {
                    data.duplicateMoveStreak++;
                    if (data.duplicateMoveStreak >= 25) {
                        plugin.logToFile(player.getName(),
                                "EXPERIMENTAL PacketChecks (PE) duplicate/micro move streak="
                                        + data.duplicateMoveStreak);
                        data.duplicateMoveStreak = 0;
                    }
                } else {
                    data.duplicateMoveStreak = Math.max(0, data.duplicateMoveStreak - 2);
                }
            }
            data.lastPacketX = x;
            data.lastPacketY = y;
            data.lastPacketZ = z;
        }
    }

    private void handleInteract(Player player, PlayerData data, PacketReceiveEvent event) {
        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            return;
        }

        long now = System.currentTimeMillis();
        data.lastAttackPacketTime = now;
        long sinceSwing = now - data.lastSwingPacketTime;
        // Packet-level swing-before-hit; generous window for ping. Log-only while experimental.
        long maxDelay = 400L + Math.min(200L, com.nolan.ava.util.PingUtils.getPlayerPing(player));
        if (data.lastSwingPacketTime > 0 && sinceSwing > maxDelay) {
            plugin.logToFile(player.getName(),
                    "EXPERIMENTAL PacketChecks (PE) attack without recent swing packet SinceSwing="
                            + sinceSwing + "ms");
            if (!plugin.isExperimentalLogOnly()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(plugin.getPrefix() + ChatColor.GRAY
                                + "[Experimental] Attack/swing packet order logged.");
                    }
                });
            }
        }
    }
}
