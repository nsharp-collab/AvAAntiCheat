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
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Experimental packet-oriented checks (default off).
 *
 * PacketEvents is an optional softdepend (same pattern as Geyser). The core
 * anti-cheat stays compatible with all Minecraft 1.21.x without PacketEvents.
 * When PacketEvents is installed and {@code enabled-checks.packet} is true on
 * Minecraft 1.21.11, real packet hooks activate; otherwise Bukkit move
 * placeholders remain as a fallback probe only.
 *
 * PE classes are loaded via reflection so missing PacketEvents never breaks startup.
 */
public class PacketChecks {

    /** Only fully tested / intended for this Minecraft patch for now. */
    public static final String SUPPORTED_MC_VERSION = "1.21.11";

    private static final String PACKETEVENTS_PLUGIN = "packetevents";

    private final AvAAntiCheat plugin;
    /** Holds AvAPacketListener when PE hooks are active; typed as Object to avoid hard PE linkage. */
    private Object packetListener;
    private boolean packetHooksActive = false;
    private boolean unavailableLogged = false;

    public PacketChecks(AvAAntiCheat plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.isCheckPacketEnabled();
    }

    public boolean arePacketHooksActive() {
        return packetHooksActive;
    }

    /**
     * True when the PacketEvents plugin is present (softdepend).
     */
    public static boolean isPacketEventsPresent() {
        Plugin pe = Bukkit.getPluginManager().getPlugin(PACKETEVENTS_PLUGIN);
        return pe != null && pe.isEnabled();
    }

    /**
     * True when the running server reports Minecraft 1.21.11.
     */
    public static boolean isSupportedServerVersion() {
        String bukkit = Bukkit.getBukkitVersion(); // e.g. 1.21.11-R0.1-SNAPSHOT
        if (bukkit != null && bukkit.startsWith(SUPPORTED_MC_VERSION)) {
            return true;
        }
        String version = Bukkit.getVersion();
        return version != null && version.contains("MC: " + SUPPORTED_MC_VERSION);
    }

    /**
     * Re-evaluate registration after enable or {@code /ac reload}.
     * Unregisters first so toggling the config on/off is safe.
     */
    public void tryRegisterPacketListeners() {
        unregisterPacketListeners();

        if (!isEnabled()) {
            plugin.getLogger().info("PacketChecks disabled in config (enabled-checks.packet=false).");
            return;
        }

        if (!isSupportedServerVersion()) {
            plugin.getLogger().warning("enabled-checks.packet is true, but this server is not "
                    + SUPPORTED_MC_VERSION + " (BukkitVersion=" + Bukkit.getBukkitVersion()
                    + "). Packet hooks will stay inactive. Only enable packet checks on "
                    + SUPPORTED_MC_VERSION + " for now. Bukkit checks still run on all 1.21.x.");
            return;
        }

        if (!isPacketEventsPresent()) {
            plugin.getLogger().warning("enabled-checks.packet is true, but PacketEvents is not installed. "
                    + "Install the PacketEvents plugin (softdepend) to use experimental packet hooks. "
                    + "Core AvAAntiCheat continues without it.");
            return;
        }

        try {
            Class<?> listenerClass = Class.forName("com.nolan.ava.checks.AvAPacketListener");
            Constructor<?> ctor = listenerClass.getConstructor(AvAAntiCheat.class, PacketChecks.class);
            Object listener = ctor.newInstance(plugin, this);

            Class<?> peClass = Class.forName("com.github.retrooper.packetevents.PacketEvents");
            Object api = peClass.getMethod("getAPI").invoke(null);
            Object eventManager = api.getClass().getMethod("getEventManager").invoke(api);

            // PacketListenerAbstract extends PacketListenerCommon
            Class<?> listenerCommon = Class.forName(
                    "com.github.retrooper.packetevents.event.PacketListenerCommon");
            Method register = eventManager.getClass().getMethod("registerListener", listenerCommon);
            Object registered = register.invoke(eventManager, listener);

            // Store the PE-registered handle for clean unregister on reload/disable.
            packetListener = registered != null ? registered : listener;
            packetHooksActive = true;
            unavailableLogged = false;
            plugin.getLogger().info("PacketChecks: PacketEvents hooks active on "
                    + SUPPORTED_MC_VERSION + " (experimental, log-only="
                    + plugin.isExperimentalLogOnly() + ").");
        } catch (Throwable t) {
            packetListener = null;
            packetHooksActive = false;
            plugin.getLogger().severe("PacketChecks: failed to register PacketEvents listeners: "
                    + t.getMessage());
        }
    }

    /**
     * Unregister PE listeners if previously registered.
     */
    public void unregisterPacketListeners() {
        if (packetListener == null) {
            packetHooksActive = false;
            return;
        }

        try {
            if (isPacketEventsPresent()) {
                Class<?> peClass = Class.forName("com.github.retrooper.packetevents.PacketEvents");
                Object api = peClass.getMethod("getAPI").invoke(null);
                Object eventManager = api.getClass().getMethod("getEventManager").invoke(api);
                Class<?> listenerCommon = Class.forName(
                        "com.github.retrooper.packetevents.event.PacketListenerCommon");
                Method unregister = eventManager.getClass()
                        .getMethod("unregisterListener", listenerCommon);
                unregister.invoke(eventManager, packetListener);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("PacketChecks: failed to unregister PacketEvents listeners: "
                    + t.getMessage());
        } finally {
            packetListener = null;
            packetHooksActive = false;
        }
    }

    /**
     * Bukkit-side stand-in used only when PE hooks are not active.
     */
    public void onPlayerMove(PlayerMoveEvent event, PlayerData data) {
        if (!isEnabled()) return;
        if (packetHooksActive) return; // PE path owns timer/onGround/duplicate probes
        Player player = event.getPlayer();
        if (plugin.shouldBypassChecks(player)) return;
        if (plugin.getCurrentAntiCheatMode() == 0) return;

        Location to = event.getTo();
        if (to == null) return;

        long now = System.currentTimeMillis();
        long delta = now - data.lastPacketMoveTime;
        data.lastPacketMoveTime = now;
        data.packetMoveCountWindow++;

        if (now - data.packetWindowStartMs >= 1000L) {
            int count = data.packetMoveCountWindow;
            data.packetMoveCountWindow = 0;
            data.packetWindowStartMs = now;

            if (count > 45) {
                String msg = "EXPERIMENTAL PacketChecks (Bukkit fallback) timer: moves/s=" + count
                        + " lastDeltaMs=" + delta;
                plugin.logToFile(player.getName(), msg);
                if (!plugin.isExperimentalLogOnly()) {
                    player.sendMessage(plugin.getPrefix() + ChatColor.GRAY
                            + "[Experimental] Unusual move rate logged.");
                }
            }
        }

        probeOnGroundConsistency(player, data);
        probeDuplicateOrMicroMove(player, data, to);
    }

    private void probeOnGroundConsistency(Player player, PlayerData data) {
        boolean clientOnGround = player.isOnGround();
        boolean serverLikelyGround = MovementChecks.isServerOnGround(player);
        if (clientOnGround != serverLikelyGround) {
            data.onGroundMismatchStreak++;
            if (data.onGroundMismatchStreak >= 20) {
                plugin.logToFile(player.getName(),
                        "EXPERIMENTAL PacketChecks (Bukkit fallback) onGround mismatch streak="
                                + data.onGroundMismatchStreak
                                + " client=" + clientOnGround + " server=" + serverLikelyGround);
                data.onGroundMismatchStreak = 0;
            }
        } else {
            data.onGroundMismatchStreak = Math.max(0, data.onGroundMismatchStreak - 1);
        }
    }

    private void probeDuplicateOrMicroMove(Player player, PlayerData data, Location to) {
        if (!Double.isNaN(data.lastPacketX)) {
            double dx = Math.abs(to.getX() - data.lastPacketX);
            double dy = Math.abs(to.getY() - data.lastPacketY);
            double dz = Math.abs(to.getZ() - data.lastPacketZ);
            boolean duplicate = dx < 1.0E-6 && dy < 1.0E-6 && dz < 1.0E-6;
            boolean micro = !duplicate && (dx + dy + dz) < 1.0E-4;
            if (duplicate || micro) {
                data.duplicateMoveStreak++;
                if (data.duplicateMoveStreak >= 25) {
                    plugin.logToFile(player.getName(),
                            "EXPERIMENTAL PacketChecks (Bukkit fallback) duplicate/micro move streak="
                                    + data.duplicateMoveStreak);
                    data.duplicateMoveStreak = 0;
                }
            } else {
                data.duplicateMoveStreak = Math.max(0, data.duplicateMoveStreak - 2);
            }
        }
        data.lastPacketX = to.getX();
        data.lastPacketY = to.getY();
        data.lastPacketZ = to.getZ();
    }

    public void logUnavailableOnce() {
        if (unavailableLogged) return;
        if (!isEnabled()) return;
        if (packetHooksActive) return;
        unavailableLogged = true;
        if (!isSupportedServerVersion()) {
            plugin.getLogger().warning("PacketChecks enabled but inactive: server is not "
                    + SUPPORTED_MC_VERSION + ".");
        } else if (!isPacketEventsPresent()) {
            plugin.getLogger().warning("PacketChecks enabled but PacketEvents plugin is not installed.");
        } else {
            plugin.getLogger().warning("PacketChecks enabled but PacketEvents hooks are not active.");
        }
    }
}
