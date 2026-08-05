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
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Arrays;
import java.util.List;

/**
 * Chat spam detection: rate-limiting and exact-repeat detection.
 */
public class ChatCheck {

    private static final long MIN_CHAT_DELAY_MS = 1500;
    private final List<String> COMMAND_PREFIXES = Arrays.asList("#", "%");

    private final AvAAntiCheat plugin;

    public ChatCheck(AvAAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void checkSpam(AsyncPlayerChatEvent event, PlayerData data) {
        if (!plugin.isCheckSpamEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 4) return;
        if (plugin.shouldBypassChecks(data)) return;

        Player player = event.getPlayer();
        String message = event.getMessage().trim();
        long currentTime = System.currentTimeMillis();
        boolean violated = false;

        String potentialCommand = message.toLowerCase();
        for (String prefix : COMMAND_PREFIXES) {
            if (potentialCommand.startsWith(prefix.toLowerCase() + "ac")) {
                return;
            }
        }

        long timeElapsed = currentTime - data.lastChatTime;
        if (timeElapsed < MIN_CHAT_DELAY_MS) {
            data.spamViolations++;
            violated = true;
            plugin.logToFile(player.getName(), "CHECK:Spam VIO=" + data.spamViolations + " (Rate Limit)");
            event.setCancelled(true);
        }

        if (!violated && message.equalsIgnoreCase(data.lastMessage) && timeElapsed < 5000) {
            data.spamViolations++;
            violated = true;
            plugin.logToFile(player.getName(), "CHECK:Spam VIO=" + data.spamViolations + " (Repetitive)");
            event.setCancelled(true);
        }

        if (violated) {
            if (data.spamViolations >= 2) {
                String rateLimitMsg = "Hold up! Wait " + String.format("%.1f", (MIN_CHAT_DELAY_MS - timeElapsed) / 1000.0) + "s before chatting again!";
                String repeatMsg = "Please try to avoid repeating the exact same message quickly!";
                String warningMessage = data.spamViolations > 2 ? repeatMsg : rateLimitMsg;
                player.sendMessage(plugin.getPrefix() + ChatColor.RED + "Warning! (" + data.spamViolations + "/" + plugin.getSpamViolationLimit() + ") " + warningMessage);
            }
        }

        if (data.spamViolations > plugin.getSpamViolationLimit()) {
            plugin.punishPlayer(player, "Chat Spam", data.spamViolations);
            data.spamViolations = 0;
        } else if (!violated) {
            data.lastChatTime = currentTime;
            data.lastMessage = message;
        }
    }
}
