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

package com.nolan.ava;

import com.nolan.ava.checks.ChatCheck;
import com.nolan.ava.checks.CombatChecks;
import com.nolan.ava.checks.MoveSimulator;
import com.nolan.ava.checks.MovementChecks;
import com.nolan.ava.checks.PacketChecks;
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class AvAAntiCheat extends JavaPlugin {

    // Basic plugin identity details
    private static final String AC_PREFIX = ChatColor.translateAlternateColorCodes('&', "&6&l[AvA-AC] &r");
    private static final String AC_VERSION = "DEV-1.9.5-PACKET";
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
    private boolean checkDupeEnabled = true;
    private boolean checkPacketEnabled = false;
    private boolean checkMoveSimulatorEnabled = false;
    private boolean experimentalLogOnly = true;

    private List<String> bannedMods = new ArrayList<>();

    private double baseSpeedLimit = 0.36;
    private double iceSpeedLimit = 1.3;
    private int speedViolationLimit = 5;
    private int dupeViolationLimit = 1;
    private long glideGracePeriodMs = 7000;

    private int flyViolationLimit = 5;
    private int spiderViolationLimit = 5;
    private int spamViolationLimit = 5;
    private int sequenceViolationLimit = 5;
    private int attackSpeedViolationLimit = 5;
    private int reachViolationLimit = 5;
    private int losViolationLimit = 5;
    private int multiAuraViolationLimit = 5;
    private int noFallViolationLimit = 5;
    private int scaffoldViolationLimit = 8;

    private int flightSeverity = 5;
    private int speedSeverity = 5;
    private int spiderSeverity = 5;
    private int phaseSeverity = 5;
    private int spamSeverity = 5;
    private int combatSeverity = 5;
    private int modDetectorSeverity = 5;
    private int dupeSeverity = 5;
    private int inventoryWalkSeverity = 3;

    private long combatTimeoutSeconds = 30;
    private String combatTimerPosition = "ACTION_BAR";
    private List<String> combatBlockedCommands = new ArrayList<>();

    private boolean enableFileLogging = true;
    private int maxLogFiles = 20;

    private final HashMap<UUID, PlayerData> playerDataMap = new HashMap<>();
    private final Set<UUID> combatLoggedPlayers = new HashSet<>();
    private File combatLoggersFile;

    private HardwareManager hardwareManager;
    private LogManager logManager;
    private UpdateManager updateManager;
    private PacketChecks packetChecks;

    @Override
    public void onEnable() {
        hardwareManager = new HardwareManager(getLogger());
        hardwareManager.detect();

        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        File logFolder = new File(getDataFolder(), "logs");
        if (!logFolder.exists()) logFolder.mkdirs();

        saveDefaultConfig();

        int currentConfigVersion = 5;
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
        updateManager = new UpdateManager(this, AC_VERSION, AC_PREFIX, false);

        loadConfigValues();
        combatLoggersFile = new File(getDataFolder(), "combat-loggers.txt");
        loadCombatLoggers();

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
        packetChecks = new PacketChecks(this);
        MoveSimulator moveSimulator = new MoveSimulator(this);
        AvAListener listener = new AvAListener(this, movementChecks, combatChecks, chatCheck, packetChecks, moveSimulator);

        // Register incoming plugin channels for Brand reading (Strictly 1.13+ Format)
        getServer().getMessenger().registerIncomingPluginChannel(this, "minecraft:brand", listener);

        // This allows the server to send plugin messages properly (e.g. BungeeCord hooks)
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        getServer().getPluginManager().registerEvents(listener, this);

        ACCommandExecutor commandExecutor = new ACCommandExecutor(this);
        getCommand("ac").setExecutor(commandExecutor);

        // Optional PacketEvents hooks (softdepend) — no-op unless PE is installed + config enabled.
        packetChecks.tryRegisterPacketListeners();

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
        saveCombatLoggers();

        if (packetChecks != null) {
            packetChecks.unregisterPacketListeners();
        }

        getServer().getConsoleSender().sendMessage(AC_PREFIX + ChatColor.RED + "AvA anti-cheat shutting down.");
        logToFile("SYSTEM", "Plugin Disabled");
    }

    private void loadCombatLoggers() {
        combatLoggedPlayers.clear();
        if (combatLoggersFile == null || !combatLoggersFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(combatLoggersFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                try {
                    combatLoggedPlayers.add(UUID.fromString(line));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (!combatLoggedPlayers.isEmpty()) {
                getLogger().info("Loaded " + combatLoggedPlayers.size() + " pending combat-logger punishment(s).");
            }
        } catch (IOException e) {
            getLogger().warning("Could not load combat-loggers.txt: " + e.getMessage());
        }
    }

    public void saveCombatLoggers() {
        if (combatLoggersFile == null) return;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(combatLoggersFile))) {
            writer.write("# UUIDs pending combat-log punishment after reconnect\n");
            for (UUID id : combatLoggedPlayers) {
                writer.write(id.toString());
                writer.newLine();
            }
        } catch (IOException e) {
            getLogger().warning("Could not save combat-loggers.txt: " + e.getMessage());
        }
    }

    public void markCombatLogger(UUID playerId) {
        if (playerId == null) return;
        combatLoggedPlayers.add(playerId);
        saveCombatLoggers();
    }

    public void clearCombatLogger(UUID playerId) {
        if (playerId == null) return;
        if (combatLoggedPlayers.remove(playerId)) {
            saveCombatLoggers();
        }
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

    /**
     * Re-evaluate packet check registration after a config reload.
     */
    public void refreshPacketChecks() {
        if (packetChecks != null) {
            packetChecks.tryRegisterPacketListeners();
        }
    }

    public void loadConfigValues() {
        currentAntiCheatMode = getConfig().getInt("default-mode", 1);

        enableFileLogging = getConfig().getBoolean("enable-logging", true);
        maxLogFiles = getConfig().getInt("max-logs", 20);
        boolean autoUpdateEnabled = getConfig().getBoolean("auto-update", false);
        if (updateManager != null) updateManager.setAutoUpdateEnabled(autoUpdateEnabled);
        if (logManager != null) logManager.updateSettings(enableFileLogging, maxLogFiles);

        checkFlightEnabled = getConfig().getBoolean("enabled-checks.flight", true);
        checkSpeedEnabled = getConfig().getBoolean("enabled-checks.speed", true);
        checkSpiderEnabled = getConfig().getBoolean("enabled-checks.spider", true);
        checkSpamEnabled = getConfig().getBoolean("enabled-checks.chat-spam", true);
        checkCombatEnabled = getConfig().getBoolean("enabled-checks.combat", true);
        checkPhaseEnabled = getConfig().getBoolean("enabled-checks.phase", true);
        checkModsEnabled = getConfig().getBoolean("enabled-checks.mod-detector", true);
        checkDupeEnabled = getConfig().getBoolean("enabled-checks.dupe", true);
        checkPacketEnabled = getConfig().getBoolean("enabled-checks.packet", false);
        checkMoveSimulatorEnabled = getConfig().getBoolean("enabled-checks.move-simulator", false);
        experimentalLogOnly = getConfig().getBoolean("experimental.log-only", true);

        bannedMods = getConfig().getStringList("banned-mods");
        if (bannedMods == null) bannedMods = new ArrayList<>();

        flyViolationLimit = getConfig().getInt("kick-limits.flight", 5);
        speedViolationLimit = getConfig().getInt("kick-limits.speed", 10);
        spiderViolationLimit = getConfig().getInt("kick-limits.spider", 5);
        spamViolationLimit = getConfig().getInt("kick-limits.chat-spam", 5);
        sequenceViolationLimit = getConfig().getInt("kick-limits.sequence", 5);
        attackSpeedViolationLimit = getConfig().getInt("kick-limits.attack-speed", 5);
        dupeViolationLimit = getConfig().getInt("kick-limits.dupe", 5);
        reachViolationLimit = getConfig().getInt("kick-limits.reach", 5);
        losViolationLimit = getConfig().getInt("kick-limits.los", 5);
        multiAuraViolationLimit = getConfig().getInt("kick-limits.multi-aura", 5);
        noFallViolationLimit = getConfig().getInt("kick-limits.nofall", 5);
        scaffoldViolationLimit = getConfig().getInt("kick-limits.scaffold", 8);

        flightSeverity = normalizeSeverity(getConfig().getInt("check-severity.flight", 5));
        speedSeverity = normalizeSeverity(getConfig().getInt("check-severity.speed", 5));
        spiderSeverity = normalizeSeverity(getConfig().getInt("check-severity.spider", 5));
        phaseSeverity = normalizeSeverity(getConfig().getInt("check-severity.phase", 5));
        spamSeverity = normalizeSeverity(getConfig().getInt("check-severity.chat-spam", 5));
        combatSeverity = normalizeSeverity(getConfig().getInt("check-severity.combat", 5));
        modDetectorSeverity = normalizeSeverity(getConfig().getInt("check-severity.mod-detector", 5));
        dupeSeverity = normalizeSeverity(getConfig().getInt("check-severity.dupe", 5));
        inventoryWalkSeverity = normalizeSeverity(getConfig().getInt("check-severity.inventory-walk", 3));

        baseSpeedLimit = getConfig().getDouble("speed-check.base-limit", 0.36);
        iceSpeedLimit = getConfig().getDouble("speed-check.ice-limit", 1.3);
        glideGracePeriodMs = getConfig().getLong("speed-check.grace-period-ms", 7000);

        combatTimeoutSeconds = getConfig().getLong("combat-timeout-seconds", 30);
        combatTimerPosition = getConfig().getString("combat-timer-position", "ACTION_BAR");

        combatBlockedCommands = getConfig().getStringList("combat-blocked-commands");
        if (combatBlockedCommands == null || combatBlockedCommands.isEmpty()) {
            combatBlockedCommands = new ArrayList<>(List.of(
                    "/home", "/spawn", "/tpa", "/tpaccept", "/back", "/warp", "/rtp", "/tp"
            ));
        } else {
            List<String> normalized = new ArrayList<>();
            for (String cmd : combatBlockedCommands) {
                if (cmd == null || cmd.isBlank()) continue;
                String c = cmd.trim().toLowerCase(Locale.ROOT);
                if (!c.startsWith("/")) c = "/" + c;
                normalized.add(c);
            }
            combatBlockedCommands = normalized;
        }
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
        if (checkDupeEnabled) sb.append("Dupe, ");
        if (checkModsEnabled) sb.append("Mods, ");
        if (checkPacketEnabled) sb.append("Packet(exp), ");
        if (checkMoveSimulatorEnabled) sb.append("MoveSim(exp)");
        if (sb.length() == 0) return "None";
        String result = sb.toString();
        if (result.endsWith(", ")) {
            result = result.substring(0, result.length() - 2);
        }
        return result;
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
            int limit = resolveKickLimit(cheatType);

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
                    data.dupeViolations = 0;
                    data.sequenceViolations = 0;
                    data.attackSpeedViolations = 0;
                    data.reachViolations = 0;
                    data.multiHitViolations = 0;
                    data.noFallViolations = 0;
                    data.scaffoldViolations = 0;
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

    private int resolveKickLimit(String cheatType) {
        if (cheatType.equalsIgnoreCase("Flight")) return flyViolationLimit;
        if (cheatType.equalsIgnoreCase("Speed")) return speedViolationLimit;
        if (cheatType.equalsIgnoreCase("Spider (WallClimb)")) return spiderViolationLimit;
        if (cheatType.equalsIgnoreCase("Chat Spam")) return spamViolationLimit;
        if (cheatType.equalsIgnoreCase("Illegal Attack Sequence")) return sequenceViolationLimit;
        if (cheatType.equalsIgnoreCase("Attack Speed (Autoclicker)")) return attackSpeedViolationLimit;
        if (cheatType.equalsIgnoreCase("Item Duplication")) return dupeViolationLimit;
        if (cheatType.equalsIgnoreCase("Reach")) return reachViolationLimit;
        if (cheatType.equalsIgnoreCase("Hit Through Blocks (LOS)")) return losViolationLimit;
        if (cheatType.equalsIgnoreCase("MultiAura")) return multiAuraViolationLimit;
        if (cheatType.equalsIgnoreCase("NoFall")) return noFallViolationLimit;
        if (cheatType.equalsIgnoreCase("Scaffold (Place Rate)")) return scaffoldViolationLimit;
        return 0;
    }

    public void logToFile(String source, String message) {
        logManager.logToFile(source, message);
    }

    /**
     * Staff / trusted bypass via permission only (no channel backdoors).
     */
    public boolean shouldBypassChecks(Player player) {
        return player != null && player.hasPermission("ava.bypass");
    }

    public void notifyOperators(String message) {
        String fullMessage = AC_PREFIX + ChatColor.YELLOW + message;
        for (Player recipient : getServer().getOnlinePlayers()) {
            if (recipient.hasPermission("ava.admin")) {
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
        // Persist so /ac start|stop survives restarts (and matches default-mode in config).
        getConfig().set("default-mode", mode);
        saveConfig();
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

    public boolean isCheckDupeEnabled() {
        return checkDupeEnabled;
    }

    public boolean isCheckPacketEnabled() {
        return checkPacketEnabled;
    }

    public boolean isCheckMoveSimulatorEnabled() {
        return checkMoveSimulatorEnabled;
    }

    public boolean isExperimentalLogOnly() {
        return experimentalLogOnly;
    }

    public void setCheckDupeEnabled(boolean enabled) {
        this.checkDupeEnabled = enabled;
    }

    public int getDupeViolationLimit() {
        return dupeViolationLimit;
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

    public int getReachViolationLimit() {
        return reachViolationLimit;
    }

    public int getLosViolationLimit() {
        return losViolationLimit;
    }

    public int getMultiAuraViolationLimit() {
        return multiAuraViolationLimit;
    }

    public int getNoFallViolationLimit() {
        return noFallViolationLimit;
    }

    public int getScaffoldViolationLimit() {
        return scaffoldViolationLimit;
    }

    public int getFlightSeverity() {
        return flightSeverity;
    }

    public int getSpeedSeverity() {
        return speedSeverity;
    }

    public int getSpiderSeverity() {
        return spiderSeverity;
    }

    public int getPhaseSeverity() {
        return phaseSeverity;
    }

    public int getSpamSeverity() {
        return spamSeverity;
    }

    public int getCombatSeverity() {
        return combatSeverity;
    }

    public int getModDetectorSeverity() {
        return modDetectorSeverity;
    }

    public int getDupeSeverity() {
        return dupeSeverity;
    }

    public int getInventoryWalkSeverity() {
        return inventoryWalkSeverity;
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

    public int normalizeSeverity(int severity) {
        return Math.max(1, Math.min(8, severity));
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

    public List<String> getCombatBlockedCommands() {
        return combatBlockedCommands;
    }
}