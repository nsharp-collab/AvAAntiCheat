// Package declaration (usually matches your project structure)
package com.nolan.ava;

// ----------------------------------------------------------------------
// REQUIRED IMPORTS (These tell the compiler where to find the Spigot classes)
// ----------------------------------------------------------------------
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent; // Needed to initialize player data
import org.bukkit.event.player.PlayerAnimationEvent; // NEW: For detecting arm swings
import org.bukkit.event.player.PlayerAnimationType; // NEW: For checking if the animation is a swing
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.potion.PotionEffectType; 

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// ----------------------------------------------------------------------
// MAIN PLUGIN CLASS
// ----------------------------------------------------------------------
public class AvAAntiCheat extends JavaPlugin implements Listener, CommandExecutor {

    // --- Configuration Constants ---
    private static final String AC_PREFIX = ChatColor.translateAlternateColorCodes('&', "&6&l[AvA-AC] &r");
    private static final String AC_VERSION = "1.8.3"; // Updated Version
    private static final String AC_AUTHOR = "Nolan";
    private boolean antiCheatActive = true; // State flag for the stop/start commands
    private final List<String> COMMAND_PREFIXES = Arrays.asList("#", "%");

    // Fly Check Constants
    private final double MAX_FALL_DISTANCE = 0.5; // Max difference in Y allowed when falling
    private final int FLY_VIOLATION_LIMIT = 5; // Violations before kick
    
    // Spam Check Constants
    private final long MIN_CHAT_DELAY_MS = 1500; // Minimum delay between messages (1.5 seconds)
    private final int SPAM_VIOLATION_LIMIT = 3; // Violations before temporary mute/kick
    
    // PvP Logging Constants
    private final long COMBAT_TIMEOUT_SECONDS = 15; // Time in seconds after the last hit until combat ends
    private final String PVP_LOG_REASON = "PvP Logging: Disconnected during combat";

    // Attack Sequence Constants (NEW)
    private final long MAX_SWING_DELAY_MS = 50; // Max time allowed between a hit and an arm swing (50ms is very quick)
    private final int SEQUENCE_VIOLATION_LIMIT = 5; // Violations before kick
    
    // Attack Speed Constants (NEW CHECK)
    private final long MIN_ATTACK_DELAY_MS = 100; // Minimum delay between successful attacks (100ms is 10 CPS max)
    private final int ATTACK_SPEED_VIOLATION_LIMIT = 5; // Violations before kick
    
    // --- Data Storage for Cheat Tracking ---
    private HashMap<UUID, PlayerData> playerDataMap = new HashMap<>();
    
    // Simple class to hold tracking data for each player
    private static class PlayerData {
        // Fly Check
        int flyViolations = 0;
        
        // Spam Check
        int spamViolations = 0;
        long lastChatTime = 0; 
        String lastMessage = ""; // To detect repeated messages
        
        // Combat Logging Check
        long combatEndTime = 0; // The timestamp when combat ends
        
        // Attack Sequence Check
        long lastDamageTime = 0; // Time of the last EntityDamageByEntityEvent the player caused (Used for swing check)
        int sequenceViolations = 0; // Violations for missing the swing animation
        
        // Attack Speed Check (NEW FIELDS)
        long lastAttackTime = 0; // Time of the last successful attack (Used for speed check)
        int attackSpeedViolations = 0; // Violations for attacking too fast
        
        public boolean isInCombat() {
            // Check if the current time is before the combat end time
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

        // Custom Initialization Message with Color
        String initMessage = AC_PREFIX + ChatColor.GREEN + ChatColor.BOLD + "AvA anti-cheat initializing, version " + AC_VERSION + " made by " + AC_AUTHOR;
        getServer().getConsoleSender().sendMessage(initMessage);
    }

    @Override
    public void onDisable() {
        getServer().getConsoleSender().sendMessage(AC_PREFIX + ChatColor.RED + "AvA anti-cheat shutting down.");
    }

    // ----------------------------------------------------------------------
    // COMMAND HANDLING (Omitted for brevity, kept same as last version)
    // ----------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("ac")) {
            // Permission check for all /ac commands
            if (!sender.hasPermission("ava.admin")) {
                sender.sendMessage(AC_PREFIX + ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(AC_PREFIX + ChatColor.AQUA + "Usage: /ac <status|reload|start|stop|kick|checkop>");
                return true;
            }

            String subCommand = args[0].toLowerCase();
            
            // --- /AC START Command ---
            if (subCommand.equals("start")) {
                 if (args.length != 2 || !args[1].equals("1")) {
                     sender.sendMessage(AC_PREFIX + ChatColor.RED + "Usage: /ac start 1");
                     return true;
                 }
                antiCheatActive = true;
                String startupMessage = AC_PREFIX + ChatColor.GREEN + ChatColor.BOLD + "AvA anti-cheat initializing, version " + AC_VERSION + " made by " + AC_AUTHOR;
                getServer().broadcastMessage(startupMessage); // Broadcast to chat
                sender.sendMessage(AC_PREFIX + ChatColor.GREEN + "Anti-Cheat Mode 1 activated.");
                return true;
            }
            
            // --- /AC STOP Command ---
            if (subCommand.equals("stop")) {
                antiCheatActive = false;
                getServer().broadcastMessage(AC_PREFIX + ChatColor.YELLOW + "Anti-Cheat has been temporarily DISABLED.");
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "All automated checks are stopped.");
                return true;
            }

            // --- /AC KICK Command ---
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
                return true;
            }

            // --- /AC CHECKOP Command ---
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
            
            // --- /AC RELOAD Command ---
            if (subCommand.equals("reload")) {
                // In a real plugin, this would reload configuration files.
                sender.sendMessage(AC_PREFIX + ChatColor.GREEN + "Configuration files reloaded successfully.");
                return true;
            }

            // --- /AC STATUS Command ---
            if (subCommand.equals("status")) {
                String activeStatus = antiCheatActive ? ChatColor.GREEN + "ACTIVE" : ChatColor.RED + "DISABLED";
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Status: " + activeStatus + " | Version " + AC_VERSION);
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Tracking " + playerDataMap.size() + " players.");
                return true;
            }

            sender.sendMessage(AC_PREFIX + ChatColor.RED + "Unknown subcommand. Use /ac help for a list.");
            return true;
        }
        return false;
    }

    // ----------------------------------------------------------------------
    // CHEAT DETECTION LOGIC IMPLEMENTATION
    // ----------------------------------------------------------------------

    private void checkFlight(PlayerMoveEvent event, PlayerData data) {
        if (!antiCheatActive) return; 
        
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        // 1. Basic checks to ignore normal movement
        if (player.getAllowFlight() || player.isInsideVehicle() || player.isSwimming() || player.isGliding()) {
            data.flyViolations = 0; // Reset if legitimate flight
            return;
        }
        
        // 2. Check for Elytra - Ignore if player is wearing an Elytra
        if (player.getInventory().getChestplate() != null && 
            player.getInventory().getChestplate().getType() == Material.ELYTRA) {
            return; 
        }

        // NEW LOGIC: Ignore movement when in special states
        
        // Check 3.1: Levitation Effect
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) {
            data.flyViolations = 0;
            return;
        }
        
        // Check 3.2: Climbing a ladder/vine
        if (player.isClimbing()) {
            data.flyViolations = 0;
            return;
        }

        // Check 3.3: In water (swimming/bobbing)
        if (player.isInWater()) {
            data.flyViolations = 0;
            return;
        }

        // Check 3.4: Falling (allow high deltaY if falling is the cause)
        if (player.getFallDistance() > 0.5) { 
             data.flyViolations = 0;
             return;
        }
        
        double deltaY = to.getY() - from.getY();
        
        // Check 4: Excessive vertical distance while not on the ground
        if (!player.isOnGround() && deltaY > MAX_FALL_DISTANCE) {
            data.flyViolations++;
            if (data.flyViolations > FLY_VIOLATION_LIMIT) {
                punishPlayer(player, "Flight", data.flyViolations);
            }
        } else if (player.isOnGround() && data.flyViolations > 0) {
            data.flyViolations = 0; // Reset if the player lands safely
        }
    }

    private void checkSpam(AsyncPlayerChatEvent event, PlayerData data) {
        if (!antiCheatActive) return; 

        Player player = event.getPlayer();
        String message = event.getMessage().trim();
        long currentTime = System.currentTimeMillis();
        
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
            player.sendMessage(AC_PREFIX + ChatColor.RED + "Wait " + String.format("%.1f", (MIN_CHAT_DELAY_MS - timeElapsed) / 1000.0) + "s before chatting again!");
            event.setCancelled(true);
        }
        
        // Check 2: Repetitive Messages
        if (message.equalsIgnoreCase(data.lastMessage) && timeElapsed < 5000) { 
             data.spamViolations++;
             player.sendMessage(AC_PREFIX + ChatColor.RED + "Avoid repeating the same message quickly!");
             event.setCancelled(true);
        }

        // Apply punishment if violations are too high
        if (data.spamViolations > SPAM_VIOLATION_LIMIT) {
             punishPlayer(player, "Chat Spam", data.spamViolations);
             data.spamViolations = 0; 
        } else {
            // Update successful chat data
            data.lastChatTime = currentTime;
            data.lastMessage = message;
        }
    }
    
    // Attack Sequence Validation Logic (existing)
    private void checkAttackSequence(Player player, PlayerData data) {
         if (!antiCheatActive) return; 

         // Check if a damage event happened recently
         if (data.lastDamageTime > 0) {
            long timeSinceDamage = System.currentTimeMillis() - data.lastDamageTime;
            
            if (timeSinceDamage > MAX_SWING_DELAY_MS) {
                data.sequenceViolations++;
                
                if (data.sequenceViolations > SEQUENCE_VIOLATION_LIMIT) {
                    punishPlayer(player, "Illegal Attack Sequence", data.sequenceViolations);
                } else {
                    player.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Warning: Suspicious attack sequence detected.");
                }
            }
            
            data.lastDamageTime = 0; 
        }
    }
    
    // Attack Speed Check Logic (NEW)
    private void checkAttackSpeed(Player attacker, PlayerData data) {
        if (!antiCheatActive) return;
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastAttack = currentTime - data.lastAttackTime;
        
        // Only check if the player has attacked before and the time is extremely short
        if (data.lastAttackTime > 0 && timeSinceLastAttack < MIN_ATTACK_DELAY_MS) {
            data.attackSpeedViolations++;
            
            if (data.attackSpeedViolations > ATTACK_SPEED_VIOLATION_LIMIT) {
                punishPlayer(attacker, "Attack Speed (Autoclicker)", data.attackSpeedViolations);
            } else {
                attacker.sendMessage(AC_PREFIX + ChatColor.RED + "Warning! Attacking too fast. (" + data.attackSpeedViolations + "/" + ATTACK_SPEED_VIOLATION_LIMIT + ")");
            }
        }
        
        // Always update the last attack time to the current time if the attack was successful
        data.lastAttackTime = currentTime;
    }


    // ----------------------------------------------------------------------
    // EVENT HANDLERS (Used to track and check players)
    // ----------------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Initialize player data when they join
        playerDataMap.put(event.getPlayer().getUniqueId(), new PlayerData());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        PlayerData data = playerDataMap.get(event.getPlayer().getUniqueId());
        if (data != null) {
            checkFlight(event, data);
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
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return; // Only care about the arm swing animation
        }
        
        Player player = event.getPlayer();
        PlayerData data = playerDataMap.get(player.getUniqueId());
        if (data == null) return;
        
        // If the swing happens, we check if a damage event was pending (lastDamageTime > 0)
        if (data.lastDamageTime > 0) {
            long timeSinceDamage = System.currentTimeMillis() - data.lastDamageTime;
            
            if (timeSinceDamage <= MAX_SWING_DELAY_MS) {
                // Valid Sequence: Damage was recorded, and swing followed quickly.
                data.sequenceViolations = Math.max(0, data.sequenceViolations - 1); // Decrease violation
            } 
            
            data.lastDamageTime = 0; // Clear the damage time whether the check was successful or not
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!antiCheatActive) return; 
        
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            PlayerData attackerData = playerDataMap.get(attacker.getUniqueId());
            
            if (attackerData != null) {
                
                // 1. ATTACK SPEED CHECK (NEW)
                checkAttackSpeed(attacker, attackerData);
                
                // 2. ATTACK SEQUENCE TRACKER (Existing)
                // Record the time of the damage for the subsequent swing validation
                attackerData.lastDamageTime = System.currentTimeMillis();
            }
        }
        
        // Combat Logging Tracker (Enter Combat)
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player victim = (Player) event.getEntity();
            Player attacker = (Player) event.getDamager();
            long combatEndTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(COMBAT_TIMEOUT_SECONDS);
            
            // Mark both players as being in combat
            PlayerData victimData = playerDataMap.get(victim.getUniqueId());
            if (victimData != null) victimData.combatEndTime = combatEndTimestamp;
            
            PlayerData attackerData = playerDataMap.get(attacker.getUniqueId());
            if (attackerData != null) attackerData.combatEndTime = combatEndTimestamp;
        }
        
        // Sequence Check Scheduler (Existing)
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            PlayerData attackerData = playerDataMap.get(attacker.getUniqueId());
            
            long delayTicks = MAX_SWING_DELAY_MS / 50; // 1 tick = 50ms
            
            getServer().getScheduler().runTaskLater(this, () -> {
                if (attackerData != null) {
                    checkAttackSequence(attacker, attackerData);
                }
            }, delayTicks + 1); 
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!antiCheatActive) return; 
        
        Player player = event.getPlayer();
        PlayerData data = playerDataMap.get(player.getUniqueId());
        
        // Anti-PvP Logging Check
        if (data != null && data.isInCombat()) {
            killPlayer(player, PVP_LOG_REASON);
        }
        
        // Clean up data when player leaves
        playerDataMap.remove(player.getUniqueId());
    }

    // ----------------------------------------------------------------------
    // PUNISHMENT METHODS
    // ----------------------------------------------------------------------

    private void kickPlayer(Player player, String reason) {
        String kickMessage = AC_PREFIX + ChatColor.RED + "You were kicked for " + reason + "!";
        player.kickPlayer(kickMessage);
        getLogger().warning(player.getName() + " was kicked for: " + reason);
    }
    
    private void killPlayer(Player player, String reason) {
        // Must execute on the main thread to safely interact with player health/inventory.
        getServer().getScheduler().runTask(this, () -> {
            if (player.isOnline()) { 
                player.setHealth(0.0); // Kills the player instantly, causing drops
                // Broadcast the death
                getServer().broadcastMessage(AC_PREFIX + ChatColor.DARK_RED + player.getName() + 
                                           " combat logged and died: " + ChatColor.WHITE + reason);
                getLogger().severe(player.getName() + " was KILLED for: " + reason);
            }
        });
    }
    
    // A centralized method to check violation level and apply punishment
    private void punishPlayer(Player player, String cheatType, int violations) {
        // Punishments must run on the main server thread!
        getServer().getScheduler().runTask(this, () -> {
            
            // All checks use the same kick punishment for now
            int limit = 0;
            
            if (cheatType.equals("Flight")) {
                limit = FLY_VIOLATION_LIMIT;
            } else if (cheatType.equals("Chat Spam")) {
                limit = SPAM_VIOLATION_LIMIT;
            } else if (cheatType.equals("Illegal Attack Sequence")) { 
                limit = SEQUENCE_VIOLATION_LIMIT;
            } else if (cheatType.equals("Attack Speed (Autoclicker)")) { // NEW
                limit = ATTACK_SPEED_VIOLATION_LIMIT;
            }

            if (limit > 0 && violations >= limit) {
                kickPlayer(player, cheatType + " (Excessive Violations)");
                // Reset violations after kick to prevent immediate re-kick upon reconnect test
                PlayerData data = playerDataMap.get(player.getUniqueId());
                if (data != null) {
                    if (cheatType.equals("Flight")) data.flyViolations = 0;
                    if (cheatType.equals("Chat Spam")) data.spamViolations = 0;
                    if (cheatType.equals("Illegal Attack Sequence")) data.sequenceViolations = 0;
                    if (cheatType.equals("Attack Speed (Autoclicker)")) data.attackSpeedViolations = 0; // NEW RESET
                }
            } else if (limit > 0) {
                player.sendMessage(AC_PREFIX + ChatColor.RED + "Warning! Detected potential " + cheatType + " (" + violations + "/" + limit + ")");
            }
        });
    }
}
