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
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Arrays;
import java.util.List;

/**
 * Chat spam detection: rate-limiting and exact-repeat detection.
 * Event cancel stays on the async thread (Bukkit-safe); PlayerData mutations
 * and punishments are deferred to the main thread.
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
        Player player = event.getPlayer();
        if (plugin.shouldBypassChecks(player)) return;

        String message = event.getMessage().trim();
        long currentTime = System.currentTimeMillis();

        String potentialCommand = message.toLowerCase();
        for (String prefix : COMMAND_PREFIXES) {
            if (potentialCommand.startsWith(prefix.toLowerCase() + "ac")) {
                return;
            }
        }

        long spamMultiplier = Math.max(1, Math.min(8, plugin.getSpamSeverity()));
        long minDelay = (long) (MIN_CHAT_DELAY_MS * (1.0 + (5 - spamMultiplier) * 0.08));
        long repeatWindow = 5000 + (5 - spamMultiplier) * 500L;

        final long lastChatTime;
        final String lastMessage;
        synchronized (data) {
            lastChatTime = data.lastChatTime;
            lastMessage = data.lastMessage;
        }

        long timeElapsed = currentTime - lastChatTime;
        boolean rateLimited = timeElapsed < minDelay;
        boolean repetitive = !rateLimited
                && message.equalsIgnoreCase(lastMessage)
                && timeElapsed < repeatWindow;

        if (!rateLimited && !repetitive) {
            // Defer "allowed" bookkeeping to main thread as well
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                synchronized (data) {
                    data.lastChatTime = currentTime;
                    data.lastMessage = message;
                }
            });
            return;
        }

        event.setCancelled(true);

        final boolean wasRateLimited = rateLimited;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            int violations;
            synchronized (data) {
                data.spamViolations++;
                violations = data.spamViolations;
            }

            plugin.logToFile(player.getName(), "CHECK:Spam VIO=" + violations
                    + (wasRateLimited ? " (Rate Limit) Delay=" + minDelay : " (Repetitive) Window=" + repeatWindow));

            if (violations >= 2) {
                String rateLimitMsg = "Hold up! Wait " + String.format("%.1f",
                        Math.max(0, (minDelay - timeElapsed) / 1000.0)) + "s before chatting again!";
                String repeatMsg = "Please try to avoid repeating the exact same message quickly!";
                String warningMessage = wasRateLimited ? rateLimitMsg : repeatMsg;
                player.sendMessage(plugin.getPrefix() + ChatColor.RED + "Warning! ("
                        + violations + "/" + plugin.getSpamViolationLimit() + ") " + warningMessage);
            }

            if (violations > plugin.getSpamViolationLimit()) {
                plugin.punishPlayer(player, "Chat Spam", violations);
                synchronized (data) {
                    data.spamViolations = 0;
                }
            }
        });
    }
}
