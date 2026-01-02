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

// ----------------------------------------------------------------------
// REQUIRED IMPORTS
// ----------------------------------------------------------------------
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.Set;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.BufferedReader;
import java.io.InputStreamReader;

// ----------------------------------------------------------------------
// MAIN PLUGIN CLASS 
// ----------------------------------------------------------------------
public class AvAAntiCheat extends JavaPlugin implements Listener, CommandExecutor {

    // --- Configuration Constants ---
    private static final String AC_PREFIX = ChatColor.translateAlternateColorCodes('&', "&6&l[AvA-AC] &r");
    private static final String AC_VERSION = "1.9.3.5";
    private static final String AC_AUTHOR = "Nolan";

    // --- Version Checker & Auto-Update Variables ---
    private static final String GITHUB_VERSION_URL = "https://raw.githubusercontent.com/nsharp-collab/AvAAntiCheat/refs/heads/main/version.txt";
    private static final String GITHUB_JAR_URL = "https://github.com/nsharp-collab/AvAAntiCheat/releases/latest/download/AvAAntiCheat.jar";
    private static final String GITHUB_CHANGELOG_URL = "https://raw.githubusercontent.com/nsharp-collab/AvAAntiCheat/refs/heads/main/changelog.txt";
    
    private boolean isUpdateAvailable = false;
    private String latestVersion = AC_VERSION;
    private String updateChangelog = "No changelog available.";
    private boolean autoUpdateEnabled = true;

    private int currentAntiCheatMode = 0;

    private final List<String> COMMAND_PREFIXES = Arrays.asList("#", "%");

    // --- Configurable Toggles ---
    private boolean checkFlightEnabled = true;
    private boolean checkSpeedEnabled = true;
    private boolean checkSpiderEnabled = true;
    private boolean checkSpamEnabled = true;
    private boolean checkCombatEnabled = true;

    // Fly Check Constants
    private final double MAX_FALL_DISTANCE = 0.5;
    private int flyViolationLimit = 5;

    // Speed Check Constants
    private double baseSpeedLimit = 0.65;
    private double iceSpeedLimit = 1.3;
    private int speedViolationLimit = 5;

    // Spider Check Constants
    private int spiderViolationLimit = 5;

    // Spam Check Constants
    private final long MIN_CHAT_DELAY_MS = 1500;
    private int spamViolationLimit = 5;

    // PvP Logging Constants
    private final long COMBAT_TIMEOUT_SECONDS = 15;
    private final String PVP_LOG_REASON = "PvP Logging: Disconnected during combat";

    // Attack Sequence Constants
    private final long MAX_SWING_DELAY_MS = 200;
    private int sequenceViolationLimit = 5;

    // Attack Speed Constants
    private final long MIN_ATTACK_DELAY_MS = 200;
    private int attackSpeedViolationLimit = 5;

    // --- LOGGING SYSTEM VARIABLES ---
    private boolean enableFileLogging = true;
    private File logFolder;
    private File currentLogFile;
    private int maxLogFiles = 20;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat FILE_NAME_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    // --- Data Storage ---
    private HashMap<UUID, PlayerData> playerDataMap = new HashMap<>();
    private Set<UUID> combatLoggedPlayers = new HashSet<>();

    // Simple class to hold tracking data
    private static class PlayerData {
        int flyViolations = 0;
        int spiderViolations = 0;
        int spiderTicks = 0;
        int speedViolations = 0;
        int spamViolations = 0;
        
        long lastChatTime = 0;
        String lastMessage = "";
        
        long combatEndTime = 0;
        long lastDamageTime = 0;
        
        int sequenceViolations = 0;
        long lastAttackTime = 0;
        int attackSpeedViolations = 0;
        long lastAttackSpeedViolationTime = 0;
        
        // --- NEW MOBILITY TRACKING ---
        boolean isGliding = false;
        long lastGlideTime = 0; // Tracks when they stopped gliding
        
        boolean isRiptiding = false;
        boolean isWindBursting = false; // Covers Wind Charge, Breeze, and Mace Smash
        
        long lastBreezeBoostTime = 0;
        long lastVelocityTime = 0;

        public boolean isInCombat() {
            return System.currentTimeMillis() < combatEndTime;
        }
    }

    // ----------------------------------------------------------------------
    // PLUGIN LIFE-CYCLE METHODS
    // ----------------------------------------------------------------------

    @Override
    public void onEnable() {
        // 1. Setup Folders
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        
        // Setup specialized log folder
        logFolder = new File(getDataFolder(), "logs");
        if (!logFolder.exists()) logFolder.mkdirs();

        // 2. Initialize bStats
        int pluginId = 28550; 
        Metrics metrics = new Metrics(this, pluginId);
        
        // 3. Load configuration
        saveDefaultConfig();
        loadConfigValues();

        // 4. Initialize Logging (New Log File Creation & Rotation)
        if (enableFileLogging) {
            setupLoggingSession();
        }

        // 5. Register bStats
        try {
            metrics.addCustomChart(new SimplePie("anti_cheat_mode", () -> getModeDescription(currentAntiCheatMode)));
        } catch (Exception e) {
            getLogger().warning("Failed to register bStats chart: " + e.getMessage());
        }

        // 6. Register events and commands
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("ac").setExecutor(this);
        getCommand("secretdisable").setExecutor(this);

        // 7. Send Word Art Banner (DELAYED for visibility)
        Bukkit.getScheduler().runTaskLater(this, this::sendStartupBanner, 60L); // 3 Seconds delay

        logToFile("SYSTEM", "Plugin Enabled - Session Started (Version " + AC_VERSION + ")");

        // 8. Run Version Check & Auto-Download
        checkVersionAndDownload();
    } 

    private void loadConfigValues() {
        // Core
        currentAntiCheatMode = getConfig().getInt("default-mode", 1);
        
        // Logging & Updates
        enableFileLogging = getConfig().getBoolean("enable-logging", true);
        maxLogFiles = getConfig().getInt("max-logs", 20);
        autoUpdateEnabled = getConfig().getBoolean("auto-update", true);
        
        // Checks
        checkFlightEnabled = getConfig().getBoolean("enabled-checks.flight", true);
        checkSpeedEnabled = getConfig().getBoolean("enabled-checks.speed", true);
        checkSpiderEnabled = getConfig().getBoolean("enabled-checks.spider", true);
        checkSpamEnabled = getConfig().getBoolean("enabled-checks.chat-spam", true);
        checkCombatEnabled = getConfig().getBoolean("enabled-checks.combat", true);

        // Limits
        flyViolationLimit = getConfig().getInt("kick-limits.flight", 5);
        speedViolationLimit = getConfig().getInt("kick-limits.speed", 5);
        spiderViolationLimit = getConfig().getInt("kick-limits.spider", 5); 
        spamViolationLimit = getConfig().getInt("kick-limits.chat-spam", 5);
        sequenceViolationLimit = getConfig().getInt("kick-limits.sequence", 5);
        attackSpeedViolationLimit = getConfig().getInt("kick-limits.attack-speed", 5);

        // Thresholds
        baseSpeedLimit = getConfig().getDouble("speed-check.base-limit", 0.65);
        iceSpeedLimit = getConfig().getDouble("speed-check.ice-limit", 1.3);
    }

    @Override
    public void onDisable() {
        getServer().getConsoleSender().sendMessage(AC_PREFIX + ChatColor.RED + "AvA anti-cheat shutting down.");
        logToFile("SYSTEM", "Plugin Disabled");
    }

    // ----------------------------------------------------------------------
    // STARTUP VISUALS (WORD ART)
    // ----------------------------------------------------------------------

    private void sendStartupBanner() {
        // 50 dashes for a standard console width
        String dash = ChatColor.GRAY + "──────────────────────────────────────────────────";
        
        String[] art = {
            dash,
            // Centered the Art (added spaces to the left) to match the dash width
            ChatColor.GOLD + "         _                       _   ",
            ChatColor.GOLD + "        / \\      __   __      / \\  ",
            ChatColor.GOLD + "       / _ \\     \\ \\ / /     / _ \\ ",
            ChatColor.GOLD + "      / ___ \\     \\ V /     / ___ \\",
            ChatColor.GOLD + "     /_/   \\_\\     \\_/     /_/   \\_\\",
            " ", // Empty line for spacing
            ChatColor.YELLOW + "  AvA AntiCheat v" + AC_VERSION,
            ChatColor.YELLOW + "  Running on " + Bukkit.getBukkitVersion(),
            ChatColor.YELLOW + "  Author: " + AC_AUTHOR,
            ChatColor.GRAY +   "  Active Mode: " + currentAntiCheatMode + " (" + getModeDescription(currentAntiCheatMode) + ")",
            ChatColor.GRAY +   "  Auto-Update: " + (autoUpdateEnabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"),
            ChatColor.AQUA +   "  Discord: https://discord.gg/CNb3Qwezpa",
            dash
        };
        for (String line : art) {
            getServer().getConsoleSender().sendMessage(line);
        }
    }

    // ----------------------------------------------------------------------
    // LOGGING & ROTATION LOGIC
    // ----------------------------------------------------------------------

    private void setupLoggingSession() {
        // 1. Create unique file for this start
        String fileName = "log_" + FILE_NAME_FORMAT.format(new Date()) + ".txt";
        currentLogFile = new File(logFolder, fileName);
        
        try {
            currentLogFile.createNewFile();
        } catch (IOException e) {
            getLogger().severe("Could not create new log file: " + e.getMessage());
        }

        // 2. Rotate logs (Delete oldest if > maxLogFiles)
        rotateLogs();
    }

    private void rotateLogs() {
        File[] files = logFolder.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files != null && files.length > maxLogFiles) {
            // Sort by last modified (Oldest first)
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            
            int filesToDelete = files.length - maxLogFiles;
            for (int i = 0; i < filesToDelete; i++) {
                if (files[i].delete()) {
                    getLogger().info("Deleted old log file: " + files[i].getName());
                }
            }
        }
    }

    private void logToFile(String source, String message) {
        if (!enableFileLogging || currentLogFile == null) return;

        try (FileWriter fw = new FileWriter(currentLogFile, true)) {
            String timestamp = DATE_FORMAT.format(new Date());
            fw.write("[" + timestamp + "] [" + source + "] " + message + "\n");
        } catch (IOException e) {
            getLogger().severe("Failed to write to log: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------
    // VERSION CHECKER & AUTO DOWNLOADER
    // ----------------------------------------------------------------------

    private void checkVersionAndDownload() {
     Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
        try {
            // Check Version
            try (InputStream in = new URL(GITHUB_VERSION_URL).openStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                latestVersion = reader.readLine();
            }

            // Check Changelog
            try (InputStream in = new URL(GITHUB_CHANGELOG_URL).openStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                 StringBuilder changelogBuilder = new StringBuilder();
                 String line;
                 while ((line = reader.readLine()) != null) {
                     changelogBuilder.append(line).append("\n");
                 }
                 if (changelogBuilder.length() > 0) {
                     updateChangelog = changelogBuilder.toString();
                 }
            } catch (Exception ignored) {
                // Changelog might not exist yet, ignore
            }
            
            if (latestVersion != null) {
                if (isNewerVersion(AC_VERSION, latestVersion)) {
                    isUpdateAvailable = true;
                    getLogger().info("A new version is available: " + latestVersion);
                    getLogger().info("--- CHANGELOG ---");
                    getLogger().info(updateChangelog);
                    getLogger().info("-----------------");

                    if (autoUpdateEnabled) {
                        downloadUpdate();
                    }
                } else {
                    getLogger().info("You are running the latest version (or a development build).");
                }
            }
        } catch (IOException e) {
            getLogger().warning("Could not check for updates: " + e.getMessage());
        }
    });
}

/**
 * Compares two version strings. 
 * Returns true only if the 'online' version is numerically higher than 'current'.
 */
     private boolean isNewerVersion(String current, String online) {
      try {
        String[] currentParts = current.split("\\.");
        String[] onlineParts = online.split("\\.");
        int length = Math.max(currentParts.length, onlineParts.length);
        
        for (int i = 0; i < length; i++) {
            int currentVal = i < currentParts.length ? Integer.parseInt(currentParts[i].replaceAll("[^0-9]", "")) : 0;
            int onlineVal = i < onlineParts.length ? Integer.parseInt(onlineParts[i].replaceAll("[^0-9]", "")) : 0;
            
            if (onlineVal > currentVal) return true;  // Online is newer
            if (currentVal > onlineVal) return false; // Local is newer (Dev build)
        }
    } catch (Exception e) {
        // Fallback to simple check if someone puts a weird character in version.txt
        return !current.equalsIgnoreCase(online);
    }
    return false;
}

    private void downloadUpdate() {
        getLogger().info("Automatically downloading update...");
        
        // 1. Target the 'update' folder specifically
        File updateFolder = Bukkit.getServer().getUpdateFolderFile();
        if (!updateFolder.exists()) {
            updateFolder.mkdirs();
        }

        // 2. Use the current jar name to ensure the server recognizes the swap
        File targetFile = new File(updateFolder, getFile().getName());

        try (InputStream in = new URL(GITHUB_JAR_URL).openStream()) {
            // 3. Copy to the update folder so it doesn't conflict with the running file
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            getLogger().info("Update downloaded to " + targetFile.getPath());
            getLogger().info("The update will be applied automatically ON THE NEXT RESTART.");
            
            // Notify admins online
            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.broadcast(AC_PREFIX + ChatColor.GREEN + "Plugin update " + latestVersion + " is ready. Restart the server to apply.", "ava.admin");
            });
            
        } catch (IOException e) {
            getLogger().severe("Failed to auto-download update: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------
    // COMMAND HANDLING
    // ----------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String senderName = sender instanceof Player ? sender.getName() : "CONSOLE";
        String fullCommand = "/" + command.getName() + " " + String.join(" ", args);
        
        if (command.getName().equalsIgnoreCase("ac")) {
            logToFile(senderName, "Attempted AC command: " + fullCommand);
            
            if (!sender.hasPermission("ava.admin")) {
                sender.sendMessage(AC_PREFIX + ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(AC_PREFIX + ChatColor.AQUA + "Usage: /ac <status|start <1-4>|stop|kick|checkop|reload>");
                return true;
            }

            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("start")) {
                 if (args.length != 2) {
                     sender.sendMessage(AC_PREFIX + ChatColor.RED + "Usage: /ac start <1|2|3|4>");
                     sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "1: ALL, 2: Flight/Move, 3: PvP, 4: Chat Spam");
                     return true;
                 }
                try {
                    int mode = Integer.parseInt(args[1]);
                    if (mode < 1 || mode > 4) {
                        sender.sendMessage(AC_PREFIX + ChatColor.RED + "Invalid mode. Use 1, 2, 3, or 4.");
                        return true;
                    }
                    currentAntiCheatMode = mode;
                    String modeDesc = getModeDescription(mode);
                    getServer().broadcastMessage(AC_PREFIX + ChatColor.GREEN + ChatColor.BOLD + "AvA anti-cheat ACTIVE (Mode " + mode + ": " + modeDesc + ")"); 
                    sender.sendMessage(AC_PREFIX + ChatColor.GREEN + "Anti-Cheat Mode " + mode + " activated.");
                    logToFile(senderName, "EXECUTED AC Mode " + mode + " (" + modeDesc + ")");
                } catch (NumberFormatException e) {
                    sender.sendMessage(AC_PREFIX + ChatColor.RED + "Invalid mode. Use a number (1-4).");
                }
                return true;
            }
            
            if (subCommand.equals("stop")) {
                currentAntiCheatMode = 0;
                getServer().broadcastMessage(AC_PREFIX + ChatColor.YELLOW + "Anti-Cheat has been temporarily DISABLED.");
                logToFile(senderName, "EXECUTED AC Mode 0 (Disabled)");
                return true;
            }

            if (subCommand.equals("status")) {
                String activeStatus = currentAntiCheatMode > 0 ? ChatColor.GREEN + "ACTIVE" : ChatColor.RED + "DISABLED";
                String modeDesc = getModeDescription(currentAntiCheatMode);
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Status: " + activeStatus + " | Version: " + AC_VERSION);
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Current Mode: " + currentAntiCheatMode + " (" + modeDesc + ")");
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Active Checks: " + getEnabledChecksString());
                
                if (isUpdateAvailable) {
                    sender.sendMessage(AC_PREFIX + ChatColor.RED + ChatColor.BOLD + "UPDATE AVAILABLE: " + latestVersion);
                    sender.sendMessage(AC_PREFIX + ChatColor.GRAY + "Changelog:\n" + updateChangelog);
                    if(autoUpdateEnabled) {
                         sender.sendMessage(AC_PREFIX + ChatColor.GREEN + "Auto-downloaded. Restart to apply.");
                    } else {
                         sender.sendMessage(AC_PREFIX + ChatColor.RED + "Auto-update disabled. Download manually.");
                    }
                } else {
                    sender.sendMessage(AC_PREFIX + ChatColor.GRAY + "Plugin is up to date.");
                }
                return true;
            }
            
            if (subCommand.equals("kick")) {
                if (args.length < 2) {
                    sender.sendMessage(AC_PREFIX + ChatColor.RED + "Usage: /ac kick <player> [reason]");
                    return true;
                }
                Player target = getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(AC_PREFIX + ChatColor.RED + "Player " + args[1] + " not found or is offline.");
                    return true;
                }
                String reason = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Kicked by Admin.";
                target.kickPlayer(ChatColor.RED + "You were kicked: " + ChatColor.WHITE + reason);
                getServer().broadcastMessage(AC_PREFIX + ChatColor.RED + target.getName() + " was kicked by " + sender.getName() + ".");
                logToFile(senderName, "EXECUTED AC kick: " + target.getName() + " for: " + reason);
                return true;
            }

            if (subCommand.equals("checkop")) {
                if (args.length < 2) {
                    sender.sendMessage(AC_PREFIX + ChatColor.RED + "Usage: /ac checkop <player>");
                    return true;
                }
                Player target = getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(AC_PREFIX + ChatColor.RED + "Player " + args[1] + " not found or is offline.");
                    return true;
                }
                String status = target.isOp() ? ChatColor.GREEN + " [OP] " : ChatColor.RED + " [NOT OP] ";
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + target.getName() + "'s status: " + status);
                return true;
            }
            
            if (subCommand.equals("reload")) {
                reloadConfig();
                loadConfigValues();
                
                sender.sendMessage(AC_PREFIX + ChatColor.GREEN + "Configuration files reloaded successfully.");
                logToFile(senderName, "EXECUTED AC reload.");
                return true;
            }

            sender.sendMessage(AC_PREFIX + ChatColor.RED + "Unknown subcommand. Use /ac help for a list.");
            return true;
        } 
        else if (command.getName().equalsIgnoreCase("secretdisable")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (player.isOp()) {
                currentAntiCheatMode = 0;
                player.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Anti-Cheat has been secretly DISABLED (Mode 0).");
                logToFile(player.getName(), "EXECUTED secretdisable (Mode 0)");
                return true;
            }
        }
        return false;
    }
    
    private String getModeDescription(int mode) {
        switch (mode) {
            case 1: return "All Checks (Config Filtered)";
            case 2: return "Flight/Movement/Speed";
            case 3: return "PvP Checks";
            case 4: return "Chat Spam";
            case 0: return "Disabled";
            default: return "Unknown";
        }
    }
    
    private String getEnabledChecksString() {
        StringBuilder sb = new StringBuilder();
        if (checkFlightEnabled) sb.append("Fly, ");
        if (checkSpeedEnabled) sb.append("Speed, ");
        if (checkSpiderEnabled) sb.append("Spider, ");
        if (checkCombatEnabled) sb.append("PvP, ");
        if (checkSpamEnabled) sb.append("Spam");
        if (sb.length() == 0) return "None";
        return sb.toString();
    }

    // ----------------------------------------------------------------------
    // CHEAT DETECTION LOGIC
    // ----------------------------------------------------------------------

    private boolean isNearSolidBlock(Player player) {
        Location loc = player.getLocation();
        Block block = loc.getBlock();
        return block.getRelative(BlockFace.NORTH).getType().isSolid() ||
               block.getRelative(BlockFace.SOUTH).getType().isSolid() ||
               block.getRelative(BlockFace.EAST).getType().isSolid() ||
               block.getRelative(BlockFace.WEST).getType().isSolid();
    }

    private boolean isClimbable(Block block) {
        Material type = block.getType();
        if (type == Material.LADDER || type == Material.VINE || type == Material.SCAFFOLDING) return true;
        
        String name = type.name();
        if (name.contains("VINES") || name.contains("VINE")) return true;

        return false;
    }
    
    private boolean isIce(Block block) {
        Material type = block.getType();
        return type == Material.ICE || type == Material.PACKED_ICE || type == Material.BLUE_ICE;
    }
    
    private boolean isSoulBlock(Block block) {
        Material type = block.getType();
        return type == Material.SOUL_SAND || type == Material.SOUL_SOIL;
    }
    
    private boolean isInLiquid(Player player) {
        Location loc = player.getLocation();
        Block block = loc.getBlock();
        Material mCenter = block.getType();
        Material mBelow = block.getRelative(BlockFace.DOWN).getType();
        Material mAbove = block.getRelative(BlockFace.UP).getType();
        return mCenter == Material.WATER || mBelow == Material.WATER || mAbove == Material.WATER
                || mCenter == Material.LAVA || mBelow == Material.LAVA || mAbove == Material.LAVA
                || mCenter == Material.BUBBLE_COLUMN || mBelow == Material.BUBBLE_COLUMN || mAbove == Material.BUBBLE_COLUMN;
    }

    /**
     * Checks if the player is holding a high mobility item like a Spear (custom), Mace, or Trident.
     * This handles "Spear" from addons by checking the display name or material name.
     */
    private boolean isHighMobilityItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return false;

        String matName = item.getType().name();
        if (matName.contains("MACE") || matName.contains("TRIDENT")) return true;
        
        // Check for Custom Items named "Spear" (e.g. Mounts of Maham)
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
            if (displayName.contains("spear")) return true;
        }
        
        return false;
    }

    // --- SPIDER CHECK ---
    private void checkSpider(PlayerMoveEvent event, PlayerData data) {
        if (!checkSpiderEnabled) return;
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 2) return;
        
        Player player = event.getPlayer();
        if (player.getAllowFlight() || player.isGliding() || player.isSwimming() || isInLiquid(player)) {
            data.spiderTicks = 0;
            return;
        }
        
        // Extended Immunity for Wind Charges and Spears
        if (data.isRiptiding || data.isWindBursting || (System.currentTimeMillis() - data.lastBreezeBoostTime < 4000)) {
            data.spiderTicks = 0;
            return;
        }
        
        if (player.hasPotionEffect(PotionEffectType.LEVITATION) || player.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
            data.spiderTicks = 0;
            return;
        }

        double deltaY = event.getTo().getY() - event.getFrom().getY();
        if (deltaY > 0 && !player.isOnGround() && isNearSolidBlock(player)) {
            Block b = player.getLocation().getBlock();
            // BEDROCK FIX: Check block below player for ladders too (hitbox offset)
            if (!isClimbable(b) && !isClimbable(b.getRelative(BlockFace.DOWN))) {
                data.spiderTicks++;
                if (data.spiderTicks > 10) {
                    data.spiderViolations++;
                    logToFile(player.getName(), "CHECK:Spider VIO=" + data.spiderViolations + " Ticks=" + data.spiderTicks);
                    data.spiderTicks = 5; 
                    if (data.spiderViolations > spiderViolationLimit) {
                         punishPlayer(player, "Spider (WallClimb)", data.spiderViolations);
                    }
                }
            } else {
                data.spiderTicks = 0;
            }
        } else {
            data.spiderTicks = 0;
        }
    }

    // --- FLIGHT CHECK ---
    private void checkFlight(PlayerMoveEvent event, PlayerData data) {
        if (!checkFlightEnabled) return;
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 2) return; 
        
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        // 1. BEDROCK LADDER FIX:
        // Bedrock players often appear to be floating above the ladder.
        // We check the block at feet AND the block directly below.
        if (isClimbable(player.getLocation().getBlock()) || isClimbable(player.getLocation().getBlock().getRelative(BlockFace.DOWN))) {
            data.flyViolations = 0;
            return;
        }

        // 2. ELYTRA GLIDE GRACE PERIOD
        if (player.isGliding()) {
             data.isGliding = true;
             data.lastGlideTime = System.currentTimeMillis();
             data.flyViolations = 0;
             return;
        }
        // If they stopped gliding less than 3 seconds ago, allow "floating" from momentum
        if (System.currentTimeMillis() - data.lastGlideTime < 3000) {
             data.flyViolations = 0;
             return;
        }
        if (data.isGliding && player.isOnGround()) {
            data.isGliding = false;
        }

        // 3. WIND CHARGE / MACE / SPEAR IMMUNITY
        // Increased immunity time to 4 seconds to account for high arcs
        if (player.isRiptiding() || data.isWindBursting || (System.currentTimeMillis() - data.lastBreezeBoostTime < 4000)) {
            data.flyViolations = 0; 
            return;
        }
        if (System.currentTimeMillis() - data.lastVelocityTime < 4000) {
            return;
        }

        // Standard Bypass
        if (player.getAllowFlight() || player.isSwimming() || player.hasPotionEffect(PotionEffectType.LEVITATION) || isInLiquid(player) || player.isInsideVehicle()) {
            data.flyViolations = 0; 
            return;
        }

        double deltaY = to.getY() - from.getY();
        if (!player.isOnGround() && deltaY > 0.05 && player.getFallDistance() < MAX_FALL_DISTANCE) {
            if (deltaY > MAX_FALL_DISTANCE) {
                if (data.spiderTicks > 0) return;
                data.flyViolations++;
                logToFile(player.getName(), "CHECK:Flight VIO=" + data.flyViolations + " Y=" + String.format("%.3f", deltaY));
                if (data.flyViolations > flyViolationLimit) {
                    punishPlayer(player, "Flight", data.flyViolations);
                }
            }
        } else if (player.isOnGround()) {
            data.flyViolations = 0; 
        }
    }

    // --- SPEED CHECK ---
    private void checkSpeed(PlayerMoveEvent event, PlayerData data) {
        if (!checkSpeedEnabled) return;
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 2) return;

        Player player = event.getPlayer();
        
        // ELYTRA FIX: Return immediately if gliding
        if (player.isGliding()) {
            data.speedViolations = 0;
            return;
        }
        // ELYTRA GRACE: If recently gliding, allow speed
        if (System.currentTimeMillis() - data.lastGlideTime < 3000) {
            data.speedViolations = 0;
            return;
        }

        if (player.getAllowFlight() || player.isFlying() || player.isInsideVehicle()) return;
        if (player.isRiptiding()) return;
        
        // WIND CHARGE / SPEAR IMMUNITY
        // If they recently used a wind charge or spear, relax speed checks
        if (data.isWindBursting || (System.currentTimeMillis() - data.lastBreezeBoostTime < 4000)) {
            return;
        }
        
        if (System.currentTimeMillis() - data.lastVelocityTime < 3000) {
            return; 
        }
        
        Location from = event.getFrom();
        Location to = event.getTo();
        
        double deltaX = to.getX() - from.getX();
        double deltaZ = to.getZ() - from.getZ();
        double horizontalDistance = Math.hypot(deltaX, deltaZ);
        
        double speedLimit = baseSpeedLimit;
        
        Block blockBelow = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        if (isIce(blockBelow)) {
            speedLimit = iceSpeedLimit;
        }
        
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            speedLimit += (amplifier * 0.15); 
        }
        
        if (player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) {
            speedLimit += 0.4;
        }

        if (isSoulBlock(blockBelow)) {
            ItemStack boots = player.getInventory().getBoots();
            if (boots != null && boots.containsEnchantment(Enchantment.SOUL_SPEED)) {
                int level = boots.getEnchantmentLevel(Enchantment.SOUL_SPEED);
                speedLimit += (level * 0.15);
            }
        }
        
        // SPEAR / MACE COMPENSATION
        // If holding a spear/mace, increase speed limit slightly to account for lunges
        if (isHighMobilityItem(player)) {
            speedLimit += 0.6; 
        }

        if (horizontalDistance > speedLimit) {
            data.speedViolations++;
            logToFile(player.getName(), "CHECK:Speed VIO=" + data.speedViolations + " Dist=" + String.format("%.3f", horizontalDistance) + " Limit=" + speedLimit);
            
            if (data.speedViolations > speedViolationLimit) {
                punishPlayer(player, "Speed", data.speedViolations);
            }
        } else {
            if (data.speedViolations > 0) data.speedViolations--;
        }
    }

    // --- SPAM CHECK ---
    private void checkSpam(AsyncPlayerChatEvent event, PlayerData data) {
        if (!checkSpamEnabled) return;
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 4) return; 
        
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
            logToFile(player.getName(), "CHECK:Spam VIO=" + data.spamViolations + " (Rate Limit)");
            event.setCancelled(true);
        }

        if (!violated && message.equalsIgnoreCase(data.lastMessage) && timeElapsed < 5000) { 
             data.spamViolations++;
             violated = true;
             logToFile(player.getName(), "CHECK:Spam VIO=" + data.spamViolations + " (Repetitive)");
             event.setCancelled(true);
        }

        if (violated) {
            if (data.spamViolations >= 2) {
                String rateLimitMsg = "Wait " + String.format("%.1f", (MIN_CHAT_DELAY_MS - timeElapsed) / 1000.0) + "s before chatting again!";
                String repeatMsg = "Avoid repeating the same message quickly!";
                String warningMessage = data.spamViolations > 2 ? repeatMsg : rateLimitMsg;
                player.sendMessage(AC_PREFIX + ChatColor.RED + "Warning! (" + data.spamViolations + "/" + spamViolationLimit + ") " + warningMessage);
            }
        }

        if (data.spamViolations > spamViolationLimit) {
             punishPlayer(player, "Chat Spam", data.spamViolations);
             data.spamViolations = 0; 
        } else if (!violated) {
            data.lastChatTime = currentTime;
            data.lastMessage = message;
        }
    }
    
    // --- COMBAT CHECKS ---
    private void checkAttackSequence(Player player, PlayerData data) {
         if (!checkCombatEnabled) return;
         if (currentAntiCheatMode != 1 && currentAntiCheatMode != 3) return; 
         
         if (data.lastDamageTime > 0) {
            long timeSinceDamage = System.currentTimeMillis() - data.lastDamageTime;
            if (timeSinceDamage > MAX_SWING_DELAY_MS) {
                data.sequenceViolations++;
                logToFile(player.getName(), "CHECK:Sequence VIO=" + data.sequenceViolations + " Delay=" + timeSinceDamage + "ms");
                if (data.sequenceViolations > sequenceViolationLimit) {
                    punishPlayer(player, "Illegal Attack Sequence", data.sequenceViolations);
                } else {
                    player.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Warning: Suspicious attack sequence detected. (Swing check failed)");
                }
            }
            data.lastDamageTime = 0; 
        }
    }
    
    private void checkAttackSpeed(Player attacker, PlayerData data) {
        if (!checkCombatEnabled) return;
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 3) return;
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastAttack = currentTime - data.lastAttackTime;

        if (data.lastAttackTime > 0 && timeSinceLastAttack < MIN_ATTACK_DELAY_MS) {
            data.attackSpeedViolations++;
            logToFile(attacker.getName(), "CHECK:AttackSpeed VIO=" + data.attackSpeedViolations + " Delay=" + timeSinceLastAttack + "ms");
            if (data.attackSpeedViolations > attackSpeedViolationLimit) {
                if (currentTime - data.lastAttackSpeedViolationTime > TimeUnit.SECONDS.toMillis(5)) {
                    punishPlayer(attacker, "Attack Speed (Autoclicker)", data.attackSpeedViolations);
                    data.lastAttackSpeedViolationTime = currentTime;
                }
            } else {
                attacker.sendMessage(AC_PREFIX + ChatColor.RED + "Warning! Attacking too fast. (" + data.attackSpeedViolations + "/" + attackSpeedViolationLimit + ")");
            }
        } else if (data.attackSpeedViolations > 0 && timeSinceLastAttack > MIN_ATTACK_DELAY_MS * 2) {
             data.attackSpeedViolations = Math.max(0, data.attackSpeedViolations - 1);
        }
        data.lastAttackTime = currentTime;
    }

    // ----------------------------------------------------------------------
    // EVENT LISTENERS
    // ----------------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        playerDataMap.put(playerId, new PlayerData());
        logToFile(player.getName(), "Player joined (IP: " + player.getAddress().getHostString() + ")");

        // --- Update Notification Logic ---
        if (player.isOp() && isUpdateAvailable) {
            getServer().getScheduler().runTaskLater(this, () -> {
                player.sendMessage(AC_PREFIX + ChatColor.RED + ChatColor.BOLD + "AVANT ANTICHEAT UPDATE AVAILABLE!");
                player.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Current Version: " + ChatColor.WHITE + AC_VERSION);
                player.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Latest Version: " + ChatColor.GREEN + latestVersion);
                player.sendMessage(AC_PREFIX + ChatColor.GRAY + "Changelog:\n" + updateChangelog);
                if (autoUpdateEnabled) {
                     player.sendMessage(AC_PREFIX + ChatColor.GREEN + "The update has been auto-downloaded. Restart to apply.");
                } else {
                     player.sendMessage(AC_PREFIX + ChatColor.GRAY + "Please download the update from GitHub.");
                }
            }, 60L); // 3-second delay
        }

        // --- Combat Logging Punishment (Kill on Rejoin) ---
        if (currentAntiCheatMode == 1 || currentAntiCheatMode == 3) {
            if (combatLoggedPlayers.contains(playerId)) {
                getServer().getScheduler().runTask(this, () -> {
                    player.getInventory().clear(); 
                    player.setHealth(0.0); 
                    player.sendMessage(AC_PREFIX + ChatColor.RED + "You combat logged! Your inventory was cleared and you were killed.");
                    logToFile(player.getName(), "Punished for Combat Logging on rejoin.");
                    combatLoggedPlayers.remove(playerId);
                });
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode().toString().contains("CREATIVE") || player.getGameMode().toString().contains("SPECTATOR")) return;
        
        PlayerData data = playerDataMap.get(player.getUniqueId());
        if (data != null) {
            checkSpider(event, data);
            checkFlight(event, data);
            checkSpeed(event, data); 
            
            if (data.isRiptiding && player.isOnGround()) data.isRiptiding = false;
            
            // Only reset wind bursting if they've been on ground for a bit to avoid "bouncing" flags
            if (data.isWindBursting && player.isOnGround() && (System.currentTimeMillis() - data.lastBreezeBoostTime > 1000)) {
                data.isWindBursting = false;
            }
        }
    }

    // --- ELYTRA FIX ---
    @EventHandler
    public void onGlideToggle(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player) {
            PlayerData data = playerDataMap.get(event.getEntity().getUniqueId());
            if (data != null) {
                data.lastGlideTime = System.currentTimeMillis();
                data.isGliding = event.isGliding();
            }
        }
    }
    
    @EventHandler
    public void onPlayerRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        PlayerData data = playerDataMap.get(player.getUniqueId());
        if (data != null) {
            data.isRiptiding = true;
            data.flyViolations = 0; 
            data.spiderTicks = 0;
            data.speedViolations = 0;
            data.lastVelocityTime = System.currentTimeMillis();
        }
    }
    
    // --- WIND CHARGE & EXPLOSION DETECTION ---
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
             Player player = (Player) event.getEntity();
             PlayerData data = playerDataMap.get(player.getUniqueId());
             if (data != null) {
                 // Register velocity/knockback time
                 data.lastVelocityTime = System.currentTimeMillis();
                 
                 // Wind Charge Detection (Explosion Damage)
                 if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || 
                     event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                     data.lastBreezeBoostTime = System.currentTimeMillis();
                     data.isWindBursting = true;
                     data.flyViolations = 0;
                 }
                 
                 // Mace Smash Detection
                 if (event.getCause() == EntityDamageEvent.DamageCause.FALL && data != null) {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (item != null && item.getType().name().contains("MACE")) { 
                         data.isWindBursting = true;
                         data.lastVelocityTime = System.currentTimeMillis();
                    }
                 }
             }
        }
    }
    
    // Explicit Wind Charge Projectile Detection
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getHitEntity() instanceof Player) {
            Projectile proj = event.getEntity();
            // Check for Wind Charge projectiles
            if (proj.getType().name().contains("WIND_CHARGE") || proj.getType().name().contains("BREEZE")) {
                PlayerData data = playerDataMap.get(event.getHitEntity().getUniqueId());
                if (data != null) {
                    data.lastBreezeBoostTime = System.currentTimeMillis();
                    data.lastVelocityTime = System.currentTimeMillis();
                    data.isWindBursting = true;
                    data.flyViolations = 0;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        PlayerData data = playerDataMap.get(event.getPlayer().getUniqueId());
        if (data != null) {
            checkSpam(event, data); 
        }
    }
    
    @EventHandler
    public void onPlayerAnimate(PlayerAnimationEvent event) {
        if (!checkCombatEnabled) return;
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 3) return;
        
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        PlayerData data = playerDataMap.get(player.getUniqueId());
        if (data == null) return;
        
        // If holding a Spear, relax sequence checks
        if (isHighMobilityItem(player)) {
            data.lastDamageTime = 0; // Reset validation on spear swing
            return;
        }

        if (data.lastDamageTime > 0) {
            data.sequenceViolations = Math.max(0, data.sequenceViolations - 1); 
            data.lastDamageTime = 0;
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            PlayerData victimData = playerDataMap.get(victim.getUniqueId());
            if (victimData != null) {
                // Velocity handling for victim
                victimData.lastVelocityTime = System.currentTimeMillis();
                
                String damagerType = event.getDamager().getType().name();
                if (damagerType.contains("BREEZE") || damagerType.contains("WIND_CHARGE")) {
                    victimData.lastBreezeBoostTime = System.currentTimeMillis();
                    victimData.isWindBursting = true;
                    victimData.flyViolations = 0; 
                    victimData.spiderTicks = 0;
                }
            }
        }

        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 3) return;
        if (!checkCombatEnabled) return;

        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            PlayerData attackerData = playerDataMap.get(attacker.getUniqueId());
            if (attackerData != null) {
                if (event.getEntity() == attacker) {
                    attackerData.isRiptiding = false; 
                    attackerData.isWindBursting = false;
                    return; 
                }
                
                // Spear/Mace Logic
                if (isHighMobilityItem(attacker)) {
                     attackerData.isWindBursting = true;
                     attackerData.flyViolations = 0;
                }
                
                if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                    checkAttackSpeed(attacker, attackerData);
                }
                attackerData.lastDamageTime = System.currentTimeMillis();
                getServer().getScheduler().runTaskLater(this, () -> {
                    checkAttackSequence(attacker, attackerData); 
                }, 1);
            }
        }

        if (event.getEntity() instanceof Player && event.getDamager() instanceof LivingEntity) {
            Player victim = (Player) event.getEntity();
            LivingEntity damager = (LivingEntity) event.getDamager(); 
            if (damager instanceof Player) {
                Player attacker = (Player) damager;
                long combatEndTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(COMBAT_TIMEOUT_SECONDS);
                PlayerData victimData = playerDataMap.get(victim.getUniqueId());
                if (victimData != null) victimData.combatEndTime = combatEndTimestamp;
                PlayerData attackerData = playerDataMap.get(attacker.getUniqueId());
                if (attackerData != null) attackerData.combatEndTime = combatEndTimestamp;
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = playerDataMap.get(player.getUniqueId());
        
        if (checkCombatEnabled && (currentAntiCheatMode == 1 || currentAntiCheatMode == 3)) {
            if (data != null && data.isInCombat()) {
                // DROP ITEMS LOOP RESTORED
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
                for (ItemStack item : player.getInventory().getArmorContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
                player.getInventory().clear();
                
                combatLoggedPlayers.add(player.getUniqueId());
                killPlayer(player, PVP_LOG_REASON);
            }
        }
        playerDataMap.remove(player.getUniqueId());
        logToFile(player.getName(), "Player quit.");
    }

    // ----------------------------------------------------------------------
    // PUNISHMENT METHODS
    // ----------------------------------------------------------------------

    private void kickPlayer(Player player, String reason) {
        String kickMessage = AC_PREFIX + ChatColor.RED + "You were kicked for " + reason + "!";
        player.kickPlayer(kickMessage);
        logToFile(player.getName(), "KICKED for: " + reason);
    }
    
    private void killPlayer(Player player, String reason) {
        getServer().getScheduler().runTask(this, () -> {
            if (player.isOnline()) { 
                player.setHealth(0.0); 
                getServer().broadcastMessage(AC_PREFIX + ChatColor.DARK_RED + player.getName() + 
                                           " combat logged and died: " + ChatColor.WHITE + reason);
                logToFile(player.getName(), "KILLED for: " + reason);
            }
        });
    }
    
    private void punishPlayer(Player player, String cheatType, int violations) {
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
                kickPlayer(player, cheatType + " detected (" + violations + "/" + limit + ")");
                logToFile(player.getName(), logMessage);
                
                // Reset violations after kick
                if (playerDataMap.containsKey(player.getUniqueId())) {
                    PlayerData data = playerDataMap.get(player.getUniqueId());
                    data.flyViolations = 0;
                    data.spiderViolations = 0;
                    data.speedViolations = 0;
                    data.spamViolations = 0;
                    data.sequenceViolations = 0;
                    data.attackSpeedViolations = 0;
                }
            } else if (limit > 0) {
                String warningMessage = "Warning! Detected potential " + cheatType + " (" + violations + "/" + limit + ")";
                player.sendMessage(AC_PREFIX + ChatColor.RED + warningMessage);
            }
        });
    }
}