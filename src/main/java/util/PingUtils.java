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

package com.nolan.ava.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Bedrock/Geyser detection and cross-platform ping estimation.
 * Reflection is used so the plugin doesn't hard-depend on Geyser being installed.
 */
public final class PingUtils {

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
                    java.lang.reflect.Method pingMethod = connection.getClass().getMethod("ping");
                    int bedrockPing = (int) pingMethod.invoke(connection);
                    return bedrockPing + 75;
                }
            } catch (Exception ignored) {
            }
            return 125;
        }

        return player.getPing();
    }
}
