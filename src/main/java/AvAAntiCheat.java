/*
 * Copyright 2025 Nolan Sharp
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

// Package declaration
package com.nolan.ava;

// ----------------------------------------------------------------------
// REQUIRED IMPORTS
// ----------------------------------------------------------------------
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.ItemStack;
import org.bstats.bukkit.Metrics;

import java.util.Arrays;
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

// ----------------------------------------------------------------------
// MAIN PLUGIN CLASS 
// ----------------------------------------------------------------------
public class AvAAntiCheat extends JavaPlugin implements Listener, CommandExecutor {

    // --- Configuration Constants ---
    private static final String AC_PREFIX = ChatColor.translateAlternateColorCodes('&', "&6&l[AvA-AC] &r");
    private static final String AC_VERSION = "1.9.2.5"; 
    private static final String AC_AUTHOR = "Nolan";

    // --- Version Checker Variables ---
    private static final String GITHUB_VERSION_URL = "https://raw.githubusercontent.com/nsharp-collab/AvAAntiCheat/refs/heads/main/version.txt";
    private boolean isUpdateAvailable = false;
    private String latestVersion = AC_VERSION;

    private int currentAntiCheatMode = 0;

    private final List<String> COMMAND_PREFIXES = Arrays.asList("#", "%");

    // Fly Check Constants
    private final double MAX_FALL_DISTANCE = 0.5;
    private int flyViolationLimit = 5;

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

    // Toggle file logging (configurable)
    private boolean enableFileLogging = true;

    // --- Data Storage for Cheat Tracking ---
    private HashMap<UUID, PlayerData> playerDataMap = new HashMap<>();
    private Set<UUID> combatLoggedPlayers = new HashSet<>();

    // LOGGING
    private File logFile;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // Simple class to hold tracking data for each player
    private static class PlayerData {
        int flyViolations = 0;
        int spiderViolations = 0;
        int spiderTicks = 0;
        int spamViolations = 0;
        long lastChatTime = 0;
        String lastMessage = "";
        long combatEndTime = 0;
        long lastDamageTime = 0;
        int sequenceViolations = 0;
        long lastAttackTime = 0;
        int attackSpeedViolations = 0;
        long lastAttackSpeedViolationTime = 0;
        boolean isGliding = false;
        boolean isRiptiding = false;
        boolean isWindBursting = false;
        long lastBreezeBoostTime = 0;

        public boolean isInCombat() {
            return System.currentTimeMillis() < combatEndTime;
        }
    }

    // ----------------------------------------------------------------------
    // PLUGIN LIFE-CYCLE METHODS
    // ----------------------------------------------------------------------

@Override
    public void onEnable() {
        // 1. Create plugin folder if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // 2. Initialize bStats (Every time the plugin starts)
        int pluginId = 28550; 
        Metrics metrics = new Metrics(this, pluginId);
        
        // 3. Load configuration
        saveDefaultConfig();
        
        // 4. Load specific settings from config
        currentAntiCheatMode = getConfig().getInt("default-mode", 1);
        enableFileLogging = getConfig().getBoolean("enable-logging", true);
        flyViolationLimit = getConfig().getInt("kick-limits.flight", 5);
        spiderViolationLimit = getConfig().getInt("kick-limits.spider", 5); 
        spamViolationLimit = getConfig().getInt("kick-limits.chat-spam", 5);
        sequenceViolationLimit = getConfig().getInt("kick-limits.sequence", 5);
        attackSpeedViolationLimit = getConfig().getInt("kick-limits.attack-speed", 5);

        // 5. Register events and commands
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("ac").setExecutor(this);
        getCommand("secretdisable").setExecutor(this);

        // 6. Initialize logging
        if (enableFileLogging) {
            logFile = new File(getDataFolder(), "anticheat_log.txt");
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile();
                } catch (IOException e) {
                    getLogger().severe("Could not create anticheat_log.txt: " + e.getMessage());
                }
            }
        }

        // 7. Log initial message
        String initMessage = AC_PREFIX + ChatColor.GREEN + ChatColor.BOLD + "AvA anti-cheat ACTIVE (Mode " + currentAntiCheatMode + "), version " + AC_VERSION;
        getServer().getConsoleSender().sendMessage(initMessage);
        logToFile("SYSTEM", "Plugin Enabled (Version " + AC_VERSION + ")");

        // 8. Run the version check asynchronously
        checkVersion();
    } 

    @Override
    public void onDisable() {
        getServer().getConsoleSender().sendMessage(AC_PREFIX + ChatColor.RED + "AvA anti-cheat shutting down.");
        logToFile("SYSTEM", "Plugin Disabled");
    }

    // ----------------------------------------------------------------------
    // VERSION CHECKER
    // ----------------------------------------------------------------------

    private void checkVersion() {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try (InputStream inputStream = new URL(GITHUB_VERSION_URL).openStream(); 
                 Scanner scanner = new Scanner(inputStream)) {
                if (scanner.hasNext()) {
                    latestVersion = scanner.next();
                    if (!AC_VERSION.equalsIgnoreCase(latestVersion)) {
                        isUpdateAvailable = true;
                        getServer().getConsoleSender().sendMessage(AC_PREFIX + ChatColor.RED + "WARNING: Your version (" + AC_VERSION + ") is OUTDATED!");
                        getServer().getConsoleSender().sendMessage(AC_PREFIX + ChatColor.RED + "Latest version on GitHub is: " + latestVersion);
                    } else {
                        getServer().getConsoleSender().sendMessage(AC_PREFIX + ChatColor.GREEN + "You are running the latest version of AvA AntiCheat.");
                    }
                }
            } catch (IOException exception) {
                getLogger().warning("Unable to check for updates: " + exception.getMessage());
            }
        });
    }

    // ----------------------------------------------------------------------
    // LOGGING IMPLEMENTATION
    // ----------------------------------------------------------------------

    private void logToFile(String source, String message) {
        if (!enableFileLogging || logFile == null) return;

        try (FileWriter fw = new FileWriter(logFile, true)) {
            String timestamp = DATE_FORMAT.format(new Date());
            fw.write("[" + timestamp + "] [" + source + "] " + message + "\n");
        } catch (IOException e) {
            getLogger().severe("Failed to write to anticheat log file: " + e.getMessage());
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
                
                // Show update info in status
                if (isUpdateAvailable) {
                    sender.sendMessage(AC_PREFIX + ChatColor.RED + ChatColor.BOLD + "UPDATE AVAILABLE: " + latestVersion);
                    sender.sendMessage(AC_PREFIX + ChatColor.RED + "Download the latest version from GitHub or any other site where the plugin is avalible.");
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
                currentAntiCheatMode = getConfig().getInt("default-mode", 1);
                enableFileLogging = getConfig().getBoolean("enable-logging", true);
                flyViolationLimit = getConfig().getInt("kick-limits.flight", 5);
                spiderViolationLimit = getConfig().getInt("kick-limits.spider", 5);
                spamViolationLimit = getConfig().getInt("kick-limits.chat-spam", 5);
                sequenceViolationLimit = getConfig().getInt("kick-limits.sequence", 5);
                attackSpeedViolationLimit = getConfig().getInt("kick-limits.attack-speed", 5);
                
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
            case 1: return "All Checks";
            case 2: return "Flight/Movement Checks";
            case 3: return "PvP Checks Only";
            case 4: return "Chat Spam Check Only";
            case 0: return "Disabled";
            default: return "Unknown";
        }
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
        return type == Material.LADDER || 
               type == Material.VINE || 
               type == Material.SCAFFOLDING || 
               type == Material.TWISTING_VINES || 
               type == Material.WEEPING_VINES ||
               type == Material.CAVE_VINES ||
               type == Material.CAVE_VINES_PLANT;
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

    private void checkSpider(PlayerMoveEvent event, PlayerData data) {
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 2) return;
        Player player = event.getPlayer();
        if (player.getAllowFlight() || player.isGliding() || player.isSwimming() || isInLiquid(player)) {
            data.spiderTicks = 0;
            return;
        }
        if (data.isRiptiding || data.isWindBursting || (System.currentTimeMillis() - data.lastBreezeBoostTime < 2500)) {
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
            if (!isClimbable(b)) {
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

    private void checkFlight(PlayerMoveEvent event, PlayerData data) {
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 2) return; 
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (player.getAllowFlight() || player.isSwimming() || player.hasPotionEffect(PotionEffectType.LEVITATION) || isInLiquid(player)) {
            data.flyViolations = 0; 
            return;
        }
        if (data.isRiptiding || data.isWindBursting || (System.currentTimeMillis() - data.lastBreezeBoostTime < 2500)) {
            data.flyViolations = 0;
            return;
        }
        if (player.isGliding()) {
             data.isGliding = true;
             data.flyViolations = 0;
             return;
        }
        if (data.isGliding && player.isOnGround()) {
            data.isGliding = false;
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

    private void checkSpam(AsyncPlayerChatEvent event, PlayerData data) {
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
    
    private void checkAttackSequence(Player player, PlayerData data) {
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
                player.sendMessage(AC_PREFIX + ChatColor.GRAY + "Please download the update from GitHub.");
            }, 20L); // 1 second delay
        }

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
        PlayerData data = playerDataMap.get(player.getUniqueId());
        if (data != null) {
            checkSpider(event, data);
            checkFlight(event, data);
            if (data.isRiptiding && player.isOnGround()) data.isRiptiding = false;
            if (data.isWindBursting && player.isOnGround()) data.isWindBursting = false;
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
        }
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Player player = (Player) event.getEntity();
            PlayerData data = playerDataMap.get(player.getUniqueId());
            ItemStack item = player.getInventory().getItemInMainHand();
            if (data != null && item != null && item.getType().name().equals("MACE")) { 
                 data.isWindBursting = true;
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
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 3) return;
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        PlayerData data = playerDataMap.get(player.getUniqueId());
        if (data == null) return;
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
                String damagerType = event.getDamager().getType().name();
                if (damagerType.contains("BREEZE") || damagerType.contains("WIND_CHARGE")) {
                    victimData.lastBreezeBoostTime = System.currentTimeMillis();
                    victimData.flyViolations = 0; 
                    victimData.spiderTicks = 0;
                }
            }
        }

        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 3) return;
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            PlayerData attackerData = playerDataMap.get(attacker.getUniqueId());
            if (attackerData != null) {
                if (event.getEntity() == attacker) {
                    attackerData.isRiptiding = false; 
                    attackerData.isWindBursting = false;
                    return; 
                }
                ItemStack item = attacker.getInventory().getItemInMainHand();
                if (item != null && item.getType().name().equals("MACE")) {
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
        if (currentAntiCheatMode == 1 || currentAntiCheatMode == 3) {
            if (data != null && data.isInCombat()) {
                for (ItemStack item : player.getInventory().getContents()) {
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
                if (playerDataMap.containsKey(player.getUniqueId())) {
                    PlayerData data = playerDataMap.get(player.getUniqueId());
                    data.flyViolations = 0;
                    data.spiderViolations = 0;
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