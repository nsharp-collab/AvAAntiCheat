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

package com.nolan.ava.commands;

import com.nolan.ava.AvAAntiCheat;
import com.nolan.ava.data.PlayerData;
import com.nolan.ava.util.HardwareManager;
import com.nolan.ava.util.PingUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;


// Handles /ac (status/start/stop/perf/mods/info/debug/kick/checkop/reload)

public class ACCommandExecutor implements CommandExecutor {

    private final AvAAntiCheat plugin;

    public ACCommandExecutor(AvAAntiCheat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String senderName = sender instanceof Player ? sender.getName() : "CONSOLE";
        String fullCommand = "/" + command.getName() + " " + String.join(" ", args);
        String prefix = plugin.getPrefix();

        if (command.getName().equalsIgnoreCase("ac")) {
            plugin.logToFile(senderName, "Attempted AC command: " + fullCommand);

            if (!sender.hasPermission("ava.admin")) {
                sender.sendMessage(prefix + ChatColor.RED + "You don't have permission to do that.");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(prefix + ChatColor.AQUA + "Usage: /ac <status|start <1-4>|stop|kick|mods|info|checkop|reload|perf|debug>");
                return true;
            }

            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("start")) {
                if (args.length != 2) {
                    sender.sendMessage(prefix + ChatColor.RED + "Usage: /ac start <1|2|3|4>");
                    sender.sendMessage(prefix + ChatColor.YELLOW + "1: ALL, 2: Flight/Move, 3: PvP, 4: Chat Spam");
                    return true;
                }
                try {
                    int mode = Integer.parseInt(args[1]);
                    if (mode < 1 || mode > 4) {
                        sender.sendMessage(prefix + ChatColor.RED + "Invalid mode. Stick to 1, 2, 3, or 4.");
                        return true;
                    }
                    plugin.setCurrentAntiCheatMode(mode);
                    String modeDesc = plugin.getModeDescription(mode);
                    plugin.getServer().broadcastMessage(prefix + ChatColor.GREEN + ChatColor.BOLD + "AvA anti-cheat ACTIVE (Mode " + mode + ": " + modeDesc + ")");
                    sender.sendMessage(prefix + ChatColor.GREEN + "Anti-Cheat Mode " + mode + " activated.");
                    plugin.logToFile(senderName, "EXECUTED AC Mode " + mode + " (" + modeDesc + ")");
                } catch (NumberFormatException e) {
                    sender.sendMessage(prefix + ChatColor.RED + "Make sure you enter a number (1-4).");
                }
                return true;
            }

            if (subCommand.equals("stop")) {
                plugin.setCurrentAntiCheatMode(0);
                plugin.getServer().broadcastMessage(prefix + ChatColor.YELLOW + "Anti-Cheat has been temporarily DISABLED.");
                plugin.logToFile(senderName, "EXECUTED AC Mode 0 (Disabled)");
                return true;
            }

            if (subCommand.equals("perf")) {
                if (args.length != 2) {
                    sender.sendMessage(prefix + ChatColor.RED + "Usage: /ac perf <high|light|auto>");
                    return true;
                }
                String modeReq = args[1].toLowerCase();
                HardwareManager hw = plugin.getHardwareManager();
                if (modeReq.equals("high")) {
                    hw.forceMode(HardwareManager.HIGH_PERFORMANCE);
                    sender.sendMessage(prefix + ChatColor.GREEN + "Hardware mode forced to HIGH_PERFORMANCE (Strict Math enabled).");
                } else if (modeReq.equals("light")) {
                    hw.forceMode(HardwareManager.OPTIMIZED_LIGHT);
                    sender.sendMessage(prefix + ChatColor.GREEN + "Hardware mode forced to OPTIMIZED_LIGHT (Pi-Mode fallback enabled).");
                } else if (modeReq.equals("auto")) {
                    hw.resetToAuto();
                    sender.sendMessage(prefix + ChatColor.GREEN + "Hardware mode reset to AUTO. Detected: " + hw.getCurrentHardwareMode());
                } else {
                    sender.sendMessage(prefix + ChatColor.RED + "Invalid option. Use high, light, or auto.");
                }
                plugin.logToFile(senderName, "Changed hardware mode to " + hw.getCurrentHardwareMode());
                return true;
            }

            if (subCommand.equals("mods")) {
                if (args.length < 2) {
                    sender.sendMessage(prefix + ChatColor.RED + "Usage: /ac mods <player>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(prefix + ChatColor.RED + "Player " + args[1] + " isn't online.");
                    return true;
                }
                Set<String> channels = target.getListeningPluginChannels();
                sender.sendMessage(prefix + ChatColor.YELLOW + "Registered Handshake Channels for " + target.getName() + ":");
                if (channels == null || channels.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + " - None (Either Vanilla, Bedrock, or a lying hacked client)");
                } else {
                    for (String channel : channels) {
                        sender.sendMessage(ChatColor.AQUA + " - " + channel);
                    }
                }
                return true;
            }

            if (subCommand.equals("info")) {
                if (args.length < 3) {
                    sender.sendMessage(prefix + ChatColor.RED + "Usage: /ac info <player> <true|false (show perms)>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(prefix + ChatColor.RED + "Player " + args[1] + " isn't online.");
                    return true;
                }

                boolean showPerms = false;
                if (args[2].equalsIgnoreCase("true")) {
                    showPerms = true;
                } else if (!args[2].equalsIgnoreCase("false")) {
                    sender.sendMessage(prefix + ChatColor.RED + "Please specify true or false for the <perms> flag.");
                    return true;
                }

                sender.sendMessage(ChatColor.DARK_GRAY + "---[ " + ChatColor.GOLD + "AvA Player Info: " + target.getName() + ChatColor.DARK_GRAY + " ]---");

                sender.sendMessage(ChatColor.YELLOW + "UUID: " + ChatColor.WHITE + target.getUniqueId().toString());

                boolean isBedrockPlayer = PingUtils.isBedrock(target);
                sender.sendMessage(ChatColor.YELLOW + "Platform: " + ChatColor.WHITE + (isBedrockPlayer ? "Bedrock (Geyser)" : "Java"));

                PlayerData targetData = plugin.getPlayerData(target.getUniqueId());
                String clientBrand = (targetData != null && targetData.clientBrand != null) ? targetData.clientBrand : "Unknown";

                sender.sendMessage(ChatColor.YELLOW + "Client Brand: " + ChatColor.WHITE + clientBrand);

                sender.sendMessage(ChatColor.YELLOW + "Ping: " + ChatColor.WHITE + PingUtils.getPlayerPing(target) + "ms");

                Set<String> channels = target.getListeningPluginChannels();
                if (channels == null || channels.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "Channels/Mods: " + ChatColor.GRAY + "None");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Channels/Mods (" + channels.size() + "): " + ChatColor.GRAY + String.join(", ", channels));
                }

                if (showPerms) {
                    List<String> perms = new ArrayList<>();
                    for (PermissionAttachmentInfo pInfo : target.getEffectivePermissions()) {
                        perms.add(pInfo.getPermission() + "(" + pInfo.getValue() + ")");
                    }
                    sender.sendMessage(ChatColor.YELLOW + "Permissions: " + ChatColor.GRAY + String.join(", ", perms));
                }

                sender.sendMessage(ChatColor.DARK_GRAY + "--------------------------------");
                return true;
            }

            if (subCommand.equals("debug")) {
                plugin.getLogManager().toggleDebugConsole();
                sender.sendMessage(prefix + ChatColor.GREEN + "Console debugging is now " + (plugin.getLogManager().isDebugModeConsole() ? "ON" : "OFF") + ".");
                plugin.logToFile(senderName, "Toggled debug mode to " + plugin.getLogManager().isDebugModeConsole());
                return true;
            }

            if (subCommand.equals("status")) {
                String activeStatus = plugin.getCurrentAntiCheatMode() > 0 ? ChatColor.GREEN + "ACTIVE" : ChatColor.RED + "DISABLED";
                String modeDesc = plugin.getModeDescription(plugin.getCurrentAntiCheatMode());
                sender.sendMessage(prefix + ChatColor.YELLOW + "Status: " + activeStatus + " | Version: " + plugin.getVersion());
                sender.sendMessage(prefix + ChatColor.YELLOW + "Current Mode: " + plugin.getCurrentAntiCheatMode() + " (" + modeDesc + ")");
                sender.sendMessage(prefix + ChatColor.YELLOW + "Hardware Profile: " + plugin.getHardwareManager().getCurrentHardwareMode() + (plugin.getHardwareManager().isForced() ? " (Forced)" : " (Auto)"));
                sender.sendMessage(prefix + ChatColor.YELLOW + "Active Checks: " + plugin.getEnabledChecksString());

                if (plugin.getUpdateManager().isUpdateAvailable()) {
                    sender.sendMessage(prefix + ChatColor.RED + ChatColor.BOLD + "UPDATE AVAILABLE: " + plugin.getUpdateManager().getLatestVersion());
                    sender.sendMessage(prefix + ChatColor.GRAY + "Changelog:\n" + plugin.getUpdateManager().getChangelog());
                    if (plugin.getUpdateManager().isAutoUpdateEnabled()) {
                        sender.sendMessage(prefix + ChatColor.GREEN + "Auto-downloaded. Restart to apply.");
                    } else {
                        sender.sendMessage(prefix + ChatColor.RED + "Auto-update disabled. Go grab it manually.");
                    }
                } else {
                    sender.sendMessage(prefix + ChatColor.GRAY + "Everything is fully up to date.");
                }
                return true;
            }

            if (subCommand.equals("kick")) {
                if (args.length < 2) {
                    sender.sendMessage(prefix + ChatColor.RED + "Usage: /ac kick <player> [reason]");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(prefix + ChatColor.RED + "Can't find player " + args[1] + ". Are they offline?");
                    return true;
                }
                String reason = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Kicked by Admin.";
                target.kickPlayer(ChatColor.RED + "You were kicked: " + ChatColor.WHITE + reason);
                plugin.getServer().broadcastMessage(prefix + ChatColor.RED + target.getName() + " was kicked by " + sender.getName() + ".");
                plugin.logToFile(senderName, "EXECUTED AC kick: " + target.getName() + " for: " + reason);
                return true;
            }

            if (subCommand.equals("checkop")) {
                if (args.length < 2) {
                    sender.sendMessage(prefix + ChatColor.RED + "Usage: /ac checkop <player>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(prefix + ChatColor.RED + "Player " + args[1] + " isn't online.");
                    return true;
                }
                String status = target.isOp() ? ChatColor.GREEN + " [OP] " : ChatColor.RED + " [NOT OP] ";
                sender.sendMessage(prefix + ChatColor.YELLOW + target.getName() + "'s status: " + status);
                return true;
            }

            if (subCommand.equals("reload")) {
                plugin.reloadConfig();
                plugin.loadConfigValues();

                sender.sendMessage(prefix + ChatColor.GREEN + "Configurations reloaded.");
                plugin.logToFile(senderName, "EXECUTED AC reload.");
                return true;
            }

            sender.sendMessage(prefix + ChatColor.RED + "Unknown command. Try /ac for help.");
            return true;
        } else if (command.getName().equalsIgnoreCase("secretdisable")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (player.isOp()) {
                plugin.setCurrentAntiCheatMode(0);
                player.sendMessage(prefix + ChatColor.YELLOW + "Anti-Cheat is now secretly DISABLED (Mode 0).");
                plugin.logToFile(player.getName(), "EXECUTED secretdisable (Mode 0)");
                return true;
            }
        }
        return false;
    }
}
