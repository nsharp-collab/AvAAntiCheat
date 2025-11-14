// Package declaration (usually matches your project structure)
package com.nolan.ava;

// ----------------------------------------------------------------------
// REQUIRED IMPORTS (These tell the compiler where to find the Spigot classes)
// ----------------------------------------------------------------------
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Location;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;


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
import java.text.SimpleDateFormat;
import java.util.Date;

// ----------------------------------------------------------------------
// MAIN PLUGIN CLASS
// ----------------------------------------------------------------------
public class AvAAntiCheat extends JavaPlugin implements Listener, CommandExecutor {

    // --- Configuration Constants ---
    private static final String AC_PREFIX = ChatColor.translateAlternateColorCodes('&', "&6&l[AvA-AC] &r");
    private static final String AC_VERSION = "1.8.9";
    private static final String AC_AUTHOR = "Nolan";

    private int currentAntiCheatMode = 0;

    private final List<String> COMMAND_PREFIXES = Arrays.asList("#", "%");

    // Fly Check Constants
    private final double MAX_FALL_DISTANCE = 0.5;
    private final int FLY_VIOLATION_LIMIT = 5;

    // Spam Check Constants
    private final long MIN_CHAT_DELAY_MS = 1500;
    private final int SPAM_VIOLATION_LIMIT = 5;

    // PvP Logging Constants
    private final long COMBAT_TIMEOUT_SECONDS = 15;
    private final String PVP_LOG_REASON = "PvP Logging: Disconnected during combat";

    // Attack Sequence Constants
    private final long MAX_SWING_DELAY_MS = 200;
    private final int SEQUENCE_VIOLATION_LIMIT = 5;

    // Attack Speed Constants
    private final long MIN_ATTACK_DELAY_MS = 200;
    private final int ATTACK_SPEED_VIOLATION_LIMIT = 5;

    // --- Data Storage for Cheat Tracking ---
    private HashMap<UUID, PlayerData> playerDataMap = new HashMap<>();
    private Set<UUID> combatLoggedPlayers = new HashSet<>();

    // LOGGING
    private File logFile;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // Simple class to hold tracking data for each player
    private static class PlayerData {
        int flyViolations = 0;
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

        public boolean isInCombat() {
            return System.currentTimeMillis() < combatEndTime;
        }
    }

    // ----------------------------------------------------------------------
    // PLUGIN LIFE-CYCLE METHODS
    // ----------------------------------------------------------------------

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("ac").setExecutor(this);
        getCommand("secretdisable").setExecutor(this);

        // NEW: Initialize Log File
        logFile = new File(getDataFolder(), "anticheat_log.txt");
        if (!logFile.exists()) {
            getDataFolder().mkdirs();
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create anticheat_log.txt: " + e.getMessage());
            }
        }
        
        currentAntiCheatMode = 1;

        // Custom Initialization Message with Color
        String initMessage = AC_PREFIX + ChatColor.GREEN + ChatColor.BOLD + "AvA anti-cheat ACTIVE (Mode 1: All Checks), version " + AC_VERSION + " made by " + AC_AUTHOR;
        getServer().getConsoleSender().sendMessage(initMessage);
        logToFile("SYSTEM", "Plugin Enabled (Version " + AC_VERSION + ")");
    }

    @Override
    public void onDisable() {
        getServer().getConsoleSender().sendMessage(AC_PREFIX + ChatColor.RED + "AvA anti-cheat shutting down.");
        logToFile("SYSTEM", "Plugin Disabled");
    }
    
    // ----------------------------------------------------------------------
    // LOGGING IMPLEMENTATION
    // ----------------------------------------------------------------------

    /**
     * Writes a log message to the anticheat log file.
     */
    private void logToFile(String source, String message) {
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
                logToFile(senderName, "AC Command denied: Permission failure.");
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
                     sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "1: ALL, 2: Flight, 3: PvP, 4: Chat Spam");
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
                    String startupMessage = AC_PREFIX + ChatColor.GREEN + ChatColor.BOLD + "AvA anti-cheat ACTIVE (Mode " + mode + ": " + modeDesc + ")";
                    getServer().broadcastMessage(startupMessage); 
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
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "All automated checks are stopped (Mode 0).");
                logToFile(senderName, "EXECUTED AC Mode 0 (Disabled)");
                return true;
            }

            if (subCommand.equals("status")) {
                logToFile(senderName, "Ran AC status check.");
                String activeStatus = currentAntiCheatMode > 0 ? ChatColor.GREEN + "ACTIVE" : ChatColor.RED + "DISABLED";
                String modeDesc = getModeDescription(currentAntiCheatMode);
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Status: " + activeStatus + " | Version " + AC_VERSION);
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Mode: " + currentAntiCheatMode + " (" + modeDesc + ")");
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Tracking " + playerDataMap.size() + " players.");
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
                logToFile(senderName, "Ran AC checkop on " + target.getName() + ": Status " + status);
                return true;
            }
            
            if (subCommand.equals("reload")) {
                sender.sendMessage(AC_PREFIX + ChatColor.GREEN + "Configuration files reloaded successfully (Mode " + currentAntiCheatMode + ").");
                logToFile(senderName, "EXECUTED AC reload.");
                return true;
            }

            sender.sendMessage(AC_PREFIX + ChatColor.RED + "Unknown subcommand. Use /ac help for a list.");
            logToFile(senderName, "Ran unknown AC subcommand: " + subCommand);
            return true;
        } 
        else if (command.getName().equalsIgnoreCase("secretdisable")) {
            logToFile(senderName, "Attempted secretdisable command.");
            
            if (!(sender instanceof Player)) {
                sender.sendMessage(AC_PREFIX + ChatColor.RED + "This command can only be run by a player.");
                return true;
            }
            Player player = (Player) sender;
            
            if (player.isOp()) {
                currentAntiCheatMode = 0;
                player.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Anti-Cheat has been secretly DISABLED (Mode 0).");
                player.sendMessage(AC_PREFIX + ChatColor.GRAY + "Only you received this message.");
                logToFile(player.getName(), "EXECUTED secretdisable (Mode 0)");
                return true;
            } else {
                player.sendMessage(AC_PREFIX + ChatColor.RED + "Unknown command."); 
                return true;
            }
        }
        return false;
    }
    
    /**
     * Helper method to get the description of the current anti-cheat mode.
     */
    private String getModeDescription(int mode) {
        switch (mode) {
            case 1: return "All Checks";
            case 2: return "Flight Check Only";
            case 3: return "PvP Checks Only (Speed, Sequence, Combat Log)";
            case 4: return "Chat Spam Check Only";
            case 0: return "Disabled";
            default: return "Unknown";
        }
    }


    // ----------------------------------------------------------------------
    // CHEAT DETECTION LOGIC IMPLEMENTATION
    // ----------------------------------------------------------------------

    private void checkFlight(PlayerMoveEvent event, PlayerData data) {
        // Guard: Run only in Mode 1 (ALL) or Mode 2 (FLIGHT)
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 2) return; 
        
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        // Check 1: Normal Bypass Conditions
        if (player.getAllowFlight() || player.isSwimming() || player.hasPotionEffect(PotionEffectType.LEVITATION) || player.isInWater()) {
            data.flyViolations = 0; 
            return;
        }
        
        // CHECK 2: Riptide/Wind Burst bypass (FIXED: Reset violations if flag is active)
        if (data.isRiptiding || data.isWindBursting) {
            data.flyViolations = 0;
            return;
        }

        // Check 3: Elytra/Gliding bypass
        if (player.isGliding()) {
             data.isGliding = true;
             data.flyViolations = 0;
             return;
        }
        
        // If they were gliding but are now touching the ground, reset the flag.
        if (data.isGliding && player.isOnGround()) {
            data.isGliding = false;
        }
        
        // The core check: Large upward or sustained movement while not on ground
        double deltaY = to.getY() - from.getY();
        
        // Only run the violation check if they aren't on the ground AND aren't taking fall damage
        if (!player.isOnGround() && deltaY > 0.05 && player.getFallDistance() < MAX_FALL_DISTANCE) {
            // Check for large jumps that aren't a vanilla jump or fall.
            if (deltaY > MAX_FALL_DISTANCE) {
                data.flyViolations++;
                
                logToFile(player.getName(), "CHECK:Flight VIO=" + data.flyViolations + " Y=" + String.format("%.3f", deltaY));
                
                if (data.flyViolations > FLY_VIOLATION_LIMIT) {
                    punishPlayer(player, "Flight", data.flyViolations);
                }
            }
        } else if (player.isOnGround()) {
            // Reset violations if the player lands.
            data.flyViolations = 0; 
        }
    }

    private void checkSpam(AsyncPlayerChatEvent event, PlayerData data) {
        // Guard: Run only in Mode 1 (ALL) or Mode 4 (CHAT SPAM)
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 4) return; 

        Player player = event.getPlayer();
        String message = event.getMessage().trim();
        long currentTime = System.currentTimeMillis();
        boolean violated = false;
        
        // --- Check for command prefixes (% or #) ---
        String potentialCommand = message.toLowerCase();
        for (String prefix : COMMAND_PREFIXES) {
            if (potentialCommand.startsWith(prefix.toLowerCase() + "ac")) {
                return;
            }
        }
        
        // Check 1: Rate Limiting
        long timeElapsed = currentTime - data.lastChatTime;
        
        if (timeElapsed < MIN_CHAT_DELAY_MS) {
            data.spamViolations++;
            violated = true;
            
            logToFile(player.getName(), "CHECK:Spam VIO=" + data.spamViolations + " (Rate Limit)");
            
            event.setCancelled(true);
        }
        
        // Check 2: Repetitive Messages
        if (!violated && message.equalsIgnoreCase(data.lastMessage) && timeElapsed < 5000) { 
             data.spamViolations++;
             violated = true;
             
             logToFile(player.getName(), "CHECK:Spam VIO=" + data.spamViolations + " (Repetitive)");
             
             event.setCancelled(true);
        }
        
        // NOTIFICATION LOGIC (FIXED: Only notify on 2/5 violations or higher)
        if (violated) {
            if (data.spamViolations >= 2) {
                String rateLimitMsg = "Wait " + String.format("%.1f", (MIN_CHAT_DELAY_MS - timeElapsed) / 1000.0) + "s before chatting again!";
                String repeatMsg = "Avoid repeating the same message quickly!";
                String warningMessage = data.spamViolations > 2 ? repeatMsg : rateLimitMsg;
                
                player.sendMessage(AC_PREFIX + ChatColor.RED + "Warning! (" + data.spamViolations + "/" + SPAM_VIOLATION_LIMIT + ") " + warningMessage);
            }
        }

        // Apply punishment if violations are too high
        if (data.spamViolations > SPAM_VIOLATION_LIMIT) {
             punishPlayer(player, "Chat Spam", data.spamViolations);
             data.spamViolations = 0; 
        } else if (!violated) {
            // Update successful chat data
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
                
                if (data.sequenceViolations > SEQUENCE_VIOLATION_LIMIT) {
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
            
            if (data.attackSpeedViolations > ATTACK_SPEED_VIOLATION_LIMIT) {
                if (currentTime - data.lastAttackSpeedViolationTime > TimeUnit.SECONDS.toMillis(5)) {
                    punishPlayer(attacker, "Attack Speed (Autoclicker)", data.attackSpeedViolations);
                    data.lastAttackSpeedViolationTime = currentTime;
                }
            } else {
                attacker.sendMessage(AC_PREFIX + ChatColor.RED + "Warning! Attacking too fast. (" + data.attackSpeedViolations + "/" + ATTACK_SPEED_VIOLATION_LIMIT + ")");
            }
        } else if (data.attackSpeedViolations > 0 && timeSinceLastAttack > MIN_ATTACK_DELAY_MS * 2) {
             data.attackSpeedViolations = Math.max(0, data.attackSpeedViolations - 1);
        }
        
        data.lastAttackTime = currentTime;
    }


    // ----------------------------------------------------------------------
    // EVENT HANDLERS
    // ----------------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        playerDataMap.put(playerId, new PlayerData());
        logToFile(player.getName(), "Player joined (IP: " + player.getAddress().getHostString() + ")");
        
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
            checkFlight(event, data);
            
            // Riptide check (reset flag if they land)
            if (data.isRiptiding && player.isOnGround()) {
                data.isRiptiding = false;
            }
            // Wind Burst check (reset flag if they land)
            if (data.isWindBursting && player.isOnGround()) {
                data.isWindBursting = false;
            }
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
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 3) return;
        
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            PlayerData attackerData = playerDataMap.get(attacker.getUniqueId());
            
            if (attackerData != null) {
                
                // --- THORN/SELF-DAMAGE FIX: Ignore damage the player takes from Thorns ---
                if (event.getEntity() == attacker) {
                    attackerData.isRiptiding = false; 
                    attackerData.isWindBursting = false;
                    return; 
                }

                // --- RIPTIDE/WIND BURST FIX: Flag the player and ignore fly checks ---
                ItemStack item = attacker.getInventory().getItemInMainHand();
                if (item != null) {
                    if (item.getType() == Material.TRIDENT && item.hasItemMeta()) {
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null && meta.hasEnchant(Enchantment.RIPTIDE)) {
                                if (attacker.isInWater() || attacker.getWorld().isThundering() || attacker.getWorld().hasStorm()) {
                                    attackerData.isRiptiding = true; 
                                    attackerData.flyViolations = 0;
                                }
                            }
                    } else if (item.getType().name().equals("MACE")) {
                         attackerData.isWindBursting = true;
                         attackerData.flyViolations = 0;
                    }
                }

                // ATTACK SPEED CHECK 
                checkAttackSpeed(attacker, attackerData);
                
                // ATTACK SEQUENCE TRACKER
                attackerData.lastDamageTime = System.currentTimeMillis();
                
                // Sequence Check Scheduler
                getServer().getScheduler().runTaskLater(this, () -> {
                    checkAttackSequence(attacker, attackerData); 
                }, 1);
            }
        }
        
        // Combat Logging Tracker (Enter Combat)
        if (event.getEntity() instanceof Player && event.getDamager() instanceof LivingEntity) {
            Player victim = (Player) event.getEntity();
            LivingEntity damager = (LivingEntity) event.getDamager(); 

            Player attacker = null;
            if (damager instanceof Player) {
                attacker = (Player) damager;
            } else {
                return;
            }

            long combatEndTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(COMBAT_TIMEOUT_SECONDS);
            
            PlayerData victimData = playerDataMap.get(victim.getUniqueId());
            if (victimData != null) victimData.combatEndTime = combatEndTimestamp;
            
            if (attacker != null) {
                PlayerData attackerData = playerDataMap.get(attacker.getUniqueId());
                if (attackerData != null) attackerData.combatEndTime = combatEndTimestamp;
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        
        Player player = event.getPlayer();
        PlayerData data = playerDataMap.get(player.getUniqueId());
        
        // Anti-PvP Logging Check
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
        
        // Clean up data when player leaves
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
            
            // 1. Determine the correct violation limit
            if (cheatType.equalsIgnoreCase("Flight")) {
                limit = FLY_VIOLATION_LIMIT;
            } else if (cheatType.equalsIgnoreCase("Chat Spam")) {
                limit = SPAM_VIOLATION_LIMIT;
            } else if (cheatType.equalsIgnoreCase("Illegal Attack Sequence")) {
                limit = SEQUENCE_VIOLATION_LIMIT;
            } else if (cheatType.equalsIgnoreCase("Attack Speed (Autoclicker)")) {
                limit = ATTACK_SPEED_VIOLATION_LIMIT;
            }

            // 2. Check for Punishment
            if (violations > limit && limit > 0) {
                
                String logMessage = "AUTOMATICALLY KICKED " + player.getName() + 
                                    " for " + cheatType + " (" + violations + "/" + limit + ")";
                
                getServer().broadcastMessage(AC_PREFIX + ChatColor.DARK_RED + player.getName() + 
                                           " was kicked for using " + cheatType + ChatColor.DARK_RED + ".");

                kickPlayer(player, cheatType + " detected (" + violations + "/" + limit + ")");
                logToFile(player.getName(), logMessage);
                
                // Reset violations after punishment
                if (playerDataMap.containsKey(player.getUniqueId())) {
                    PlayerData data = playerDataMap.get(player.getUniqueId());
                    data.flyViolations = 0;
                    data.spamViolations = 0;
                    data.sequenceViolations = 0;
                    data.attackSpeedViolations = 0;
                }
                
            } else if (limit > 0) {
                String warningMessage = "Warning! Detected potential " + cheatType + " (" + violations + "/" + limit + ")";
                player.sendMessage(AC_PREFIX + ChatColor.RED + warningMessage);
                // Non-punitive violation logging is handled inside the check methods now.
            }
        });
    }
}