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

package com.nolan.ava;

import com.nolan.ava.checks.ChatCheck;
import com.nolan.ava.checks.CombatChecks;
import com.nolan.ava.checks.MovementChecks;
import com.nolan.ava.commands.ACCommandExecutor;
import com.nolan.ava.data.PlayerData;
import com.nolan.ava.listeners.AvAListener;
import com.nolan.ava.util.HardwareManager;
import com.nolan.ava.util.LogManager;
import com.nolan.ava.util.UpdateManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AvAAntiCheat extends JavaPlugin {

    // Basic plugin identity details
    private static final String AC_PREFIX = ChatColor.translateAlternateColorCodes('&', "&6&l[AvA-AC] &r");
    private static final String AC_VERSION = "DEV-1.9.5-SPLIT";
    private static final String AC_AUTHOR = "Nolan";
    public static final String PVP_LOG_REASON = "PvP Logging: Disconnected during combat";

    private int currentAntiCheatMode = 0;

    // Toggles for our various cheat checks
    private boolean checkFlightEnabled = true;
    private boolean checkSpeedEnabled = true;
    private boolean checkSpiderEnabled = true;
    private boolean checkSpamEnabled = true;
    private boolean checkCombatEnabled = true;
    private boolean checkPhaseEnabled = true;
    private boolean checkModsEnabled = true;

    private List<String> bannedMods = new ArrayList<>();

    private double baseSpeedLimit = 0.65;
    private double iceSpeedLimit = 1.3;
    private int speedViolationLimit = 5;
    private long glideGracePeriodMs = 7000;

    private int flyViolationLimit = 5;
    private int spiderViolationLimit = 5;
    private int spamViolationLimit = 5;
    private int sequenceViolationLimit = 5;
    private int attackSpeedViolationLimit = 5;

    private long combatTimeoutSeconds = 30;
    private String combatTimerPosition = "ACTION_BAR";

    private boolean enableFileLogging = true;
    private int maxLogFiles = 20;

    private final HashMap<UUID, PlayerData> playerDataMap = new HashMap<>();
    private final Set<UUID> combatLoggedPlayers = new HashSet<>();

    private HardwareManager hardwareManager;
    private LogManager logManager;
    private UpdateManager updateManager;

    @Override
    public void onEnable() {
        hardwareManager = new HardwareManager(getLogger());
        hardwareManager.detect();

        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        File logFolder = new File(getDataFolder(), "logs");
        if (!logFolder.exists()) logFolder.mkdirs();

        saveDefaultConfig();

        int currentConfigVersion = 3;
        if (getConfig().getInt("config-version", 0) < currentConfigVersion) {
            getLogger().warning("Your config.yml is outdated! Renaming to config-old.yml and generating a fresh one...");

            File configFile = new File(getDataFolder(), "config.yml");
            File oldConfigFile = new File(getDataFolder(), "config-old.yml");

            if (oldConfigFile.exists()) oldConfigFile.delete();

            if (configFile.renameTo(oldConfigFile)) {
                saveDefaultConfig();
                reloadConfig();
            } else {
                getLogger().severe("Failed to backup outdated config.yml!");
            }
        }

        logManager = new LogManager(this, logFolder, enableFileLogging, maxLogFiles);
        updateManager = new UpdateManager(this, AC_VERSION, AC_PREFIX, true);

        loadConfigValues();

        if (enableFileLogging) {
            logManager.setupLoggingSession();
        }

        int pluginId = 28550;
        Metrics metrics = new Metrics(this, pluginId);
        try {
            metrics.addCustomChart(new SimplePie("anti_cheat_mode", () -> getModeDescription(currentAntiCheatMode)));
            metrics.addCustomChart(new SimplePie("hardware_profile", hardwareManager::getCurrentHardwareMode));
        } catch (Exception e) {
            getLogger().warning("Couldn't set up bStats chart: " + e.getMessage());
        }

        MovementChecks movementChecks = new MovementChecks(this);
        CombatChecks combatChecks = new CombatChecks(this);
        ChatCheck chatCheck = new ChatCheck(this);
        AvAListener listener = new AvAListener(this, movementChecks, combatChecks, chatCheck);

        // Register incoming plugin channels for Brand reading (Strictly 1.13+ Format)
        getServer().getMessenger().registerIncomingPluginChannel(this, "minecraft:brand", listener);

        // This allows the server to send plugin messages properly (e.g. BungeeCord hooks)
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        getServer().getPluginManager().registerEvents(listener, this);

        ACCommandExecutor commandExecutor = new ACCommandExecutor(this);
        getCommand("ac").setExecutor(commandExecutor);
        getCommand("secretdisable").setExecutor(commandExecutor);

        Bukkit.getScheduler().runTaskTimer(this, this::tickCombatTimers, 20L, 20L);

        Bukkit.getScheduler().runTaskLater(this, this::sendStartupBanner, 60L);

        logToFile("SYSTEM", "Plugin Enabled - Session Started (Version " + AC_VERSION + ")");

        updateManager.checkVersionAndDownload();
    }

    @Override
    public void onDisable() {
        for (PlayerData data : playerDataMap.values()) {
            if (data.combatBossBar != null) {
                data.combatBossBar.removeAll();
            }
        }

        getServer().getConsoleSender().sendMessage(AC_PREFIX + ChatColor.RED + "AvA anti-cheat shutting down.");
        logToFile("SYSTEM", "Plugin Disabled");
    }

    private void tickCombatTimers() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData data = playerDataMap.get(p.getUniqueId());
            if (data == null) continue;

            if (!combatTimerPosition.equalsIgnoreCase("BOSS_BAR") && data.combatBossBar != null) {
                data.combatBossBar.removeAll();
                data.combatBossBar = null;
            }

            if (data.isInCombat()) {
                long secondsLeft = (data.combatEndTime - now) / 1000;
                if (secondsLeft > 0) {
                    String message = ChatColor.RED + "⚔ In Combat: " + secondsLeft + "s ⚔";

                    if (combatTimerPosition.equalsIgnoreCase("BOSS_BAR")) {
                        if (data.combatBossBar == null) {
                            data.combatBossBar = Bukkit.createBossBar(message, BarColor.RED, BarStyle.SOLID);
                            data.combatBossBar.addPlayer(p);
                        }
                        data.combatBossBar.setTitle(message);
                        double progress = (double) secondsLeft / combatTimeoutSeconds;
                        data.combatBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
                    } else if (combatTimerPosition.equalsIgnoreCase("SUBTITLE")) {
                        p.sendTitle("", message, 0, 30, 0);
                    } else {
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                    }
                }
            } else if (data.combatEndTime > 0) {
                data.combatEndTime = 0;
                data.lastAttacker = null;
                String clearMsg = ChatColor.GREEN + "You are no longer in combat.";

                if (data.combatBossBar != null) {
                    data.combatBossBar.removeAll();
                    data.combatBossBar = null;
                }

                if (combatTimerPosition.equalsIgnoreCase("SUBTITLE")) {
                    p.sendTitle("", clearMsg, 0, 40, 10);
                } else if (combatTimerPosition.equalsIgnoreCase("ACTION_BAR")) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(clearMsg));
                }
            }
        }
    }

    public void loadConfigValues() {
        currentAntiCheatMode = getConfig().getInt("default-mode", 1);

        enableFileLogging = getConfig().getBoolean("enable-logging", true);
        maxLogFiles = getConfig().getInt("max-logs", 20);
        boolean autoUpdateEnabled = getConfig().getBoolean("auto-update", true);
        if (updateManager != null) updateManager.setAutoUpdateEnabled(autoUpdateEnabled);
        if (logManager != null) logManager.updateSettings(enableFileLogging, maxLogFiles);

        checkFlightEnabled = getConfig().getBoolean("enabled-checks.flight", true);
        checkSpeedEnabled = getConfig().getBoolean("enabled-checks.speed", true);
        checkSpiderEnabled = getConfig().getBoolean("enabled-checks.spider", true);
        checkSpamEnabled = getConfig().getBoolean("enabled-checks.chat-spam", true);
        checkCombatEnabled = getConfig().getBoolean("enabled-checks.combat", true);
        checkPhaseEnabled = getConfig().getBoolean("enabled-checks.phase", true);
        checkModsEnabled = getConfig().getBoolean("enabled-checks.mod-detector", true);

        bannedMods = getConfig().getStringList("banned-mods");
        if (bannedMods == null) bannedMods = new ArrayList<>();

        flyViolationLimit = getConfig().getInt("kick-limits.flight", 5);
        speedViolationLimit = getConfig().getInt("kick-limits.speed", 10);
        spiderViolationLimit = getConfig().getInt("kick-limits.spider", 5);
        spamViolationLimit = getConfig().getInt("kick-limits.chat-spam", 5);
        sequenceViolationLimit = getConfig().getInt("kick-limits.sequence", 5);
        attackSpeedViolationLimit = getConfig().getInt("kick-limits.attack-speed", 5);

        baseSpeedLimit = getConfig().getDouble("speed-check.base-limit", 0.65);
        iceSpeedLimit = getConfig().getDouble("speed-check.ice-limit", 1.3);
        glideGracePeriodMs = getConfig().getLong("speed-check.grace-period-ms", 7000);

        combatTimeoutSeconds = getConfig().getLong("combat-timeout-seconds", 30);
        combatTimerPosition = getConfig().getString("combat-timer-position", "ACTION_BAR");
    }

    private void sendStartupBanner() {
        String dash = ChatColor.GRAY + "──────────────────────────────────────────────────";

        String[] art = {
                dash,
                ChatColor.GOLD + "         _                      _   ",
                ChatColor.GOLD + "        / \\      __   __      / \\  ",
                ChatColor.GOLD + "       / _ \\     \\ \\ / /     / _ \\ ",
                ChatColor.GOLD + "      / ___ \\     \\ V /     / ___ \\",
                ChatColor.GOLD + "     /_/   \\_\\     \\_/     /_/   \\_\\",
                " ",
                ChatColor.YELLOW + "  AvA AntiCheat v" + AC_VERSION,
                ChatColor.YELLOW + "  Running on " + Bukkit.getBukkitVersion(),
                ChatColor.YELLOW + "  Author: " + AC_AUTHOR,
                ChatColor.GRAY + "  Active Mode: " + currentAntiCheatMode + " (" + getModeDescription(currentAntiCheatMode) + ")",
                ChatColor.GRAY + "  Hardware Profile: " + hardwareManager.getCurrentHardwareMode(),
                ChatColor.GRAY + "  Auto-Update: " + (updateManager.isAutoUpdateEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"),
                ChatColor.AQUA + "  Discord: https://discord.gg/CNb3Qwezpa",
                dash
        };
        for (String line : art) {
            getServer().getConsoleSender().sendMessage(line);
        }
    }

    public String getModeDescription(int mode) {
        switch (mode) {
            case 1: return "All Checks (Config Filtered)";
            case 2: return "Flight/Movement/Speed/Phase";
            case 3: return "PvP Checks";
            case 4: return "Chat Spam";
            case 0: return "Disabled";
            default: return "Unknown";
        }
    }

    public String getEnabledChecksString() {
        StringBuilder sb = new StringBuilder();
        if (checkFlightEnabled) sb.append("Fly, ");
        if (checkSpeedEnabled) sb.append("Speed, ");
        if (checkSpiderEnabled) sb.append("Spider, ");
        if (checkPhaseEnabled) sb.append("Phase, ");
        if (checkCombatEnabled) sb.append("PvP, ");
        if (checkSpamEnabled) sb.append("Spam, ");
        if (checkModsEnabled) sb.append("Mods");
        if (sb.length() == 0) return "None";
        return sb.toString();
    }

    public void kickPlayer(Player player, String reason) {
        String kickMessage = AC_PREFIX + ChatColor.RED + "You were kicked for " + reason + "!";
        player.kickPlayer(kickMessage);
        logToFile(player.getName(), "KICKED for: " + reason);
    }

    public void killPlayer(Player player, String reason) {
        getServer().getScheduler().runTask(this, () -> {
            if (player.isOnline()) {
                player.setHealth(0.0);
                getServer().broadcastMessage(AC_PREFIX + ChatColor.DARK_RED + player.getName() +
                        " combat logged and died: " + ChatColor.WHITE + reason);
                logToFile(player.getName(), "KILLED for: " + reason);
            }
        });
    }

    public void punishPlayer(Player player, String cheatType, int violations) {
        getServer().getScheduler().runTask(this, () -> {
            int limit = 0;
            if (cheatType.equalsIgnoreCase("Flight")) {
                limit = flyViolationLimit;
            } else if (cheatType.equalsIgnoreCase("Speed")) {
                limit = speedViolationLimit;
            } else if (cheatType.equalsIgnoreCase("Spider (WallClimb)")) {
                limit = spiderViolationLimit;
            } else if (cheatType.equalsIgnoreCase("Chat Spam")) {
                limit = spamViolationLimit;
            } else if (cheatType.equalsIgnoreCase("Illegal Attack Sequence")) {
                limit = sequenceViolationLimit;
            } else if (cheatType.equalsIgnoreCase("Attack Speed (Autoclicker)")) {
                limit = attackSpeedViolationLimit;
            }

            if (violations > limit && limit > 0) {
                String logMessage = "AUTOMATICALLY KICKED " + player.getName() +
                        " for " + cheatType + " (" + violations + "/" + limit + ")";
                getServer().broadcastMessage(AC_PREFIX + ChatColor.DARK_RED + player.getName() +
                        " was kicked for using " + cheatType + ChatColor.DARK_RED + ".");
                notifyOperators(player.getName() + " was kicked for " + cheatType + " (" + violations + "/" + limit + ").");
                kickPlayer(player, cheatType + " detected (" + violations + "/" + limit + ")");
                logToFile(player.getName(), logMessage);

                PlayerData data = playerDataMap.get(player.getUniqueId());
                if (data != null) {
                    data.flyViolations = 0;
                    data.spiderViolations = 0;
                    data.speedViolations = 0;
                    data.spamViolations = 0;
                    data.sequenceViolations = 0;
                    data.attackSpeedViolations = 0;
                }
            } else if (limit > 0) {
                if (violations >= Math.max(1, limit - 1)) {
                    notifyOperators(player.getName() + " is close to a " + cheatType + " kick (" + violations + "/" + limit + ").");
                }
                String warningMessage = "Warning! Detected potential " + cheatType + " (" + violations + "/" + limit + ")";
                player.sendMessage(AC_PREFIX + ChatColor.RED + warningMessage);
            }
        });
    }

    public void logToFile(String source, String message) {
        logManager.logToFile(source, message);
    }

    public boolean shouldBypassChecks(PlayerData data) {
        return data != null && data.bypassAllChecks;
    }

    public void notifyOperators(String message) {
        String fullMessage = AC_PREFIX + ChatColor.YELLOW + message;
        for (Player recipient : getServer().getOnlinePlayers()) {
            if (recipient.isOp()) {
                recipient.sendMessage(fullMessage);
            }
        }
        getServer().getConsoleSender().sendMessage(fullMessage);
    }

    // ===== Accessors used by checks/, listeners/ and commands/ =====

    public File getPluginFile() {
        return super.getFile();
    }

    public String getPrefix() {
        return AC_PREFIX;
    }

    public String getVersion() {
        return AC_VERSION;
    }

    public String getPvpLogReason() {
        return PVP_LOG_REASON;
    }

    public int getCurrentAntiCheatMode() {
        return currentAntiCheatMode;
    }

    public void setCurrentAntiCheatMode(int mode) {
        this.currentAntiCheatMode = mode;
    }

    public boolean isCheckFlightEnabled() {
        return checkFlightEnabled;
    }

    public boolean isCheckSpeedEnabled() {
        return checkSpeedEnabled;
    }

    public boolean isCheckSpiderEnabled() {
        return checkSpiderEnabled;
    }

    public boolean isCheckSpamEnabled() {
        return checkSpamEnabled;
    }

    public boolean isCheckCombatEnabled() {
        return checkCombatEnabled;
    }

    public boolean isCheckPhaseEnabled() {
        return checkPhaseEnabled;
    }

    public boolean isCheckModsEnabled() {
        return checkModsEnabled;
    }

    public List<String> getBannedMods() {
        return bannedMods;
    }

    public double getBaseSpeedLimit() {
        return baseSpeedLimit;
    }

    public double getIceSpeedLimit() {
        return iceSpeedLimit;
    }

    public int getSpeedViolationLimit() {
        return speedViolationLimit;
    }

    public long getGlideGracePeriodMs() {
        return glideGracePeriodMs;
    }

    public int getFlyViolationLimit() {
        return flyViolationLimit;
    }

    public int getSpiderViolationLimit() {
        return spiderViolationLimit;
    }

    public int getSpamViolationLimit() {
        return spamViolationLimit;
    }

    public int getSequenceViolationLimit() {
        return sequenceViolationLimit;
    }

    public int getAttackSpeedViolationLimit() {
        return attackSpeedViolationLimit;
    }

    public long getCombatTimeoutSeconds() {
        return combatTimeoutSeconds;
    }

    public String getCombatTimerPosition() {
        return combatTimerPosition;
    }

    public HardwareManager getHardwareManager() {
        return hardwareManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public UpdateManager getUpdateManager() {
        return updateManager;
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    public void putPlayerData(UUID uuid, PlayerData data) {
        playerDataMap.put(uuid, data);
    }

    public void removePlayerData(UUID uuid) {
        playerDataMap.remove(uuid);
    }

    public Set<UUID> getCombatLoggedPlayers() {
        return combatLoggedPlayers;
    }
}