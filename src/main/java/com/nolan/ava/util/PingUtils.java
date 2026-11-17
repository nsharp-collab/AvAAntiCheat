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

package com.nolan.ava.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bedrock/Geyser detection and cross-platform ping estimation.
 * Reflection is used so the plugin doesn't hard-depend on Geyser being installed.
 */
public final class PingUtils {

    /** TPS barely changes per move event — refresh at most once per second. */
    private static final long TPS_CACHE_MS = 1000L;

    private static volatile double cachedTps = 20.0;
    private static volatile long cachedTpsAtMs = 0L;

    private enum TpsAccess {
        NONE,
        SERVER_GET_TPS,
        SERVER_RECENT_TPS,
        NMS_RECENT_TPS
    }

    private static volatile boolean tpsResolved = false;
    private static TpsAccess tpsAccess = TpsAccess.NONE;
    private static Method cachedGetTpsMethod;
    private static Field cachedRecentTpsField;
    private static Method cachedGetHandleMethod;
    private static Field cachedNmsRecentTpsField;

    private static Method cachedGeyserPingMethod;
    private static boolean geyserPingResolved = false;

    private PingUtils() {
    }

    public static boolean isBedrock(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
            try {
                return org.geysermc.geyser.api.GeyserApi.api().isBedrockPlayer(player.getUniqueId());
            } catch (NoClassDefFoundError | Exception ignored) {
            }
        }
        return false;
    }

    public static int getPlayerPing(Player player) {
        if (isBedrock(player)) {
            try {
                org.geysermc.geyser.api.connection.GeyserConnection connection =
                        org.geysermc.geyser.api.GeyserApi.api().connectionByUuid(player.getUniqueId());

                if (connection != null) {
                    Method pingMethod = resolveGeyserPingMethod(connection.getClass());
                    if (pingMethod != null) {
                        int bedrockPing = (int) pingMethod.invoke(connection);
                        return bedrockPing + 75;
                    }
                }
            } catch (Exception ignored) {
            }
            return 125;
        }

        return player.getPing();
    }

    public static double getServerTps() {
        long now = System.currentTimeMillis();
        if (now - cachedTpsAtMs < TPS_CACHE_MS) {
            return cachedTps;
        }
        double tps = sampleServerTps();
        cachedTps = tps;
        cachedTpsAtMs = now;
        return tps;
    }

    private static double sampleServerTps() {
        resolveTpsAccess();
        try {
            Object server = Bukkit.getServer();
            switch (tpsAccess) {
                case SERVER_GET_TPS -> {
                    double[] tpsValues = (double[]) cachedGetTpsMethod.invoke(server);
                    return clampTps(tpsValues);
                }
                case SERVER_RECENT_TPS -> {
                    double[] tpsValues = (double[]) cachedRecentTpsField.get(server);
                    return clampTps(tpsValues);
                }
                case NMS_RECENT_TPS -> {
                    Object nms = cachedGetHandleMethod.invoke(server);
                    double[] tpsValues = (double[]) cachedNmsRecentTpsField.get(nms);
                    return clampTps(tpsValues);
                }
                default -> {
                    return 20.0;
                }
            }
        } catch (Exception ignored) {
            return cachedTps > 0.0 ? cachedTps : 20.0;
        }
    }

    private static synchronized void resolveTpsAccess() {
        if (tpsResolved) return;
        Object server = Bukkit.getServer();
        try {
            cachedGetTpsMethod = server.getClass().getMethod("getTPS");
            cachedGetTpsMethod.setAccessible(true);
            tpsAccess = TpsAccess.SERVER_GET_TPS;
            tpsResolved = true;
            return;
        } catch (Exception ignored) {
            cachedGetTpsMethod = null;
        }

        try {
            cachedRecentTpsField = server.getClass().getField("recentTps");
            cachedRecentTpsField.setAccessible(true);
            tpsAccess = TpsAccess.SERVER_RECENT_TPS;
            tpsResolved = true;
            return;
        } catch (Exception ignored) {
            cachedRecentTpsField = null;
        }

        try {
            cachedGetHandleMethod = server.getClass().getMethod("getHandle");
            cachedGetHandleMethod.setAccessible(true);
            Object nms = cachedGetHandleMethod.invoke(server);
            cachedNmsRecentTpsField = nms.getClass().getField("recentTps");
            cachedNmsRecentTpsField.setAccessible(true);
            tpsAccess = TpsAccess.NMS_RECENT_TPS;
            tpsResolved = true;
            return;
        } catch (Exception ignored) {
            cachedGetHandleMethod = null;
            cachedNmsRecentTpsField = null;
        }

        tpsAccess = TpsAccess.NONE;
        tpsResolved = true;
    }

    private static double clampTps(double[] tpsValues) {
        if (tpsValues != null && tpsValues.length > 0) {
            return Math.max(0.0, Math.min(20.0, tpsValues[0]));
        }
        return 20.0;
    }

    private static synchronized Method resolveGeyserPingMethod(Class<?> connectionClass) {
        if (geyserPingResolved) return cachedGeyserPingMethod;
        try {
            cachedGeyserPingMethod = connectionClass.getMethod("ping");
            cachedGeyserPingMethod.setAccessible(true);
        } catch (Exception ignored) {
            cachedGeyserPingMethod = null;
        }
        geyserPingResolved = true;
        return cachedGeyserPingMethod;
    }

    public static boolean isGeyserPlayer(Player player) {
        return isBedrock(player);
    }
}
