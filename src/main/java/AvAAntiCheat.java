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
import org.bukkit.event.player.PlayerJoinEvent; 
import org.bukkit.event.player.PlayerAnimationEvent; 
import org.bukkit.event.player.PlayerAnimationType; 
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.potion.PotionEffectType; 
import org.bukkit.inventory.ItemStack; 

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.Set;    

// ----------------------------------------------------------------------
// MAIN PLUGIN CLASS
// ----------------------------------------------------------------------
public class AvAAntiCheat extends JavaPlugin implements Listener, CommandExecutor {

    // --- Configuration Constants ---
    private static final String AC_PREFIX = ChatColor.translateAlternateColorCodes('&', "&6&l[AvA-AC] &r");
    private static final String AC_VERSION = "1.8.6"; // Version bump
    
    private static final String AC_AUTHOR = "Nolan";
    
    // Default to 0 (Disabled) in the class declaration, but will be set to 1 in onEnable()
    private int currentAntiCheatMode = 0; 
    
    private final List<String> COMMAND_PREFIXES = Arrays.asList("#", "%");

    // --- TEST/DEBUG CONFIGURATION (ONLY FOR TESTING) ---
    private static final String TEST_ADMIN_USER = "YOUR_MINECRAFT_USERNAME_HERE"; 

    // Fly Check Constants
    private final double MAX_FALL_DISTANCE = 0.5; 
    private final int FLY_VIOLATION_LIMIT = 5; 
    
    // Spam Check Constants
    private final long MIN_CHAT_DELAY_MS = 1500; 
    private final int SPAM_VIOLATION_LIMIT = 3; 
    
    // PvP Logging Constants
    private final long COMBAT_TIMEOUT_SECONDS = 15; 
    private final String PVP_LOG_REASON = "PvP Logging: Disconnected during combat";

    // Attack Sequence Constants
    private final long MAX_SWING_DELAY_MS = 50; 
    private final int SEQUENCE_VIOLATION_LIMIT = 5; 
    
    // Attack Speed Constants
    private final long MIN_ATTACK_DELAY_MS = 100; 
    private final int ATTACK_SPEED_VIOLATION_LIMIT = 5; 
    
    // --- Data Storage for Cheat Tracking ---
    private HashMap<UUID, PlayerData> playerDataMap = new HashMap<>();
    private Set<UUID> combatLoggedPlayers = new HashSet<>();
    
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

        // NEW: Default to Mode 1 (All Checks) on server startup
        currentAntiCheatMode = 1;

        // Custom Initialization Message with Color
        String initMessage = AC_PREFIX + ChatColor.GREEN + ChatColor.BOLD + "AvA anti-cheat ACTIVE (Mode 1: All Checks), version " + AC_VERSION + " made by " + AC_AUTHOR;
        getServer().getConsoleSender().sendMessage(initMessage);
    }

    @Override
    public void onDisable() {
        getServer().getConsoleSender().sendMessage(AC_PREFIX + ChatColor.RED + "AvA anti-cheat shutting down.");
    }

    // ----------------------------------------------------------------------
    // COMMAND HANDLING
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
                sender.sendMessage(AC_PREFIX + ChatColor.AQUA + "Usage: /ac <status|start <1-4>|stop|kick|checkop>");
                return true;
            }

            String subCommand = args[0].toLowerCase();
            
            // --- /AC START Command (Updated with Modes) ---
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
                } catch (NumberFormatException e) {
                    sender.sendMessage(AC_PREFIX + ChatColor.RED + "Invalid mode. Use a number (1-4).");
                }
                return true;
            }
            
            // --- /AC STOP Command (Updated to set mode to 0) ---
            if (subCommand.equals("stop")) {
                currentAntiCheatMode = 0;
                getServer().broadcastMessage(AC_PREFIX + ChatColor.YELLOW + "Anti-Cheat has been temporarily DISABLED.");
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "All automated checks are stopped (Mode 0).");
                return true;
            }

            // --- /AC STATUS Command (Updated for Mode) ---
            if (subCommand.equals("status")) {
                String activeStatus = currentAntiCheatMode > 0 ? ChatColor.GREEN + "ACTIVE" : ChatColor.RED + "DISABLED";
                String modeDesc = getModeDescription(currentAntiCheatMode);
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Status: " + activeStatus + " | Version " + AC_VERSION);
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Mode: " + currentAntiCheatMode + " (" + modeDesc + ")");
                sender.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Tracking " + playerDataMap.size() + " players.");
                return true;
            }
            
            // --- Other Commands (kick, checkop, reload, secretdisable) remain the same ---
            
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
                sender.sendMessage(AC_PREFIX + ChatColor.GREEN + "Configuration files reloaded successfully (Mode " + currentAntiCheatMode + ").");
                return true;
            }

            sender.sendMessage(AC_PREFIX + ChatColor.RED + "Unknown subcommand. Use /ac help for a list.");
            return true;
        } 
        else if (command.getName().equalsIgnoreCase("secretdisable")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(AC_PREFIX + ChatColor.RED + "This command can only be run by a player.");
                return true;
            }
            Player player = (Player) sender;
            boolean isOp = player.isOp();
            boolean isTestAdmin = player.getName().equalsIgnoreCase(TEST_ADMIN_USER);

            if (isOp || isTestAdmin) {
                currentAntiCheatMode = 0;
                player.sendMessage(AC_PREFIX + ChatColor.YELLOW + "Anti-Cheat has been secretly DISABLED (Mode 0).");
                player.sendMessage(AC_PREFIX + ChatColor.GRAY + "Only you received this message.");
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
    // CHEAT DETECTION LOGIC IMPLEMENTATION (Now gated by currentAntiCheatMode)
    // ----------------------------------------------------------------------

    private void checkFlight(PlayerMoveEvent event, PlayerData data) {
        // Guard: Run only in Mode 1 (ALL) or Mode 2 (FLIGHT)
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 2) return; 
        
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (player.getAllowFlight() || player.isSwimming() || player.isGliding()) {
            data.flyViolations = 0; 
            return;
        }
        
        if (player.getInventory().getChestplate() != null && 
            player.getInventory().getChestplate().getType() == Material.ELYTRA) {
            return; 
        }

        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) {
            data.flyViolations = 0;
            return;
        }
        
        if (player.isInWater()) {
            data.flyViolations = 0;
            return;
        }

        if (player.getFallDistance() > 0.5) { 
             data.flyViolations = 0;
             return;
        }
        
        double deltaY = to.getY() - from.getY();
        
        if (!player.isOnGround() && deltaY > MAX_FALL_DISTANCE) {
            data.flyViolations++;
            if (data.flyViolations > FLY_VIOLATION_LIMIT) {
                punishPlayer(player, "Flight", data.flyViolations);
            }
        } else if (player.isOnGround() && data.flyViolations > 0) {
            data.flyViolations = 0; 
        }
    }

    private void checkSpam(AsyncPlayerChatEvent event, PlayerData data) {
        // Guard: Run only in Mode 1 (ALL) or Mode 4 (CHAT SPAM)
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 4) return; 

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
    
    private void checkAttackSequence(Player player, PlayerData data) {
         // Guard: Run only in Mode 1 (ALL) or Mode 3 (PVP)
         if (currentAntiCheatMode != 1 && currentAntiCheatMode != 3) return; 

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
    
    private void checkAttackSpeed(Player attacker, PlayerData data) {
        // Guard: Run only in Mode 1 (ALL) or Mode 3 (PVP)
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 3) return;
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastAttack = currentTime - data.lastAttackTime;
        
        if (data.lastAttackTime > 0 && timeSinceLastAttack < MIN_ATTACK_DELAY_MS) {
            data.attackSpeedViolations++;
            
            if (data.attackSpeedViolations > ATTACK_SPEED_VIOLATION_LIMIT) {
                punishPlayer(attacker, "Attack Speed (Autoclicker)", data.attackSpeedViolations);
            } else {
                // Ensure the warning message uses the correct violation limit constant
                attacker.sendMessage(AC_PREFIX + ChatColor.RED + "Warning! Attacking too fast. (" + data.attackSpeedViolations + "/" + ATTACK_SPEED_VIOLATION_LIMIT + ")");
            }
        }
        
        data.lastAttackTime = currentTime;
    }


    // ----------------------------------------------------------------------
    // EVENT HANDLERS (Now call the checks based on the active mode)
    // ----------------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Always initialize player data regardless of mode
        playerDataMap.put(playerId, new PlayerData());
        
        // CHECK 1: Punish previous combat loggers (This is a PvP related check)
        if (currentAntiCheatMode == 1 || currentAntiCheatMode == 3) {
            if (combatLoggedPlayers.contains(playerId)) {
                getServer().getScheduler().runTask(this, () -> {
                    player.getInventory().clear(); 
                    player.setHealth(0.0); 
                    player.sendMessage(AC_PREFIX + ChatColor.RED + "You combat logged! Your inventory was cleared and you were killed.");
                    getLogger().severe(player.getName() + " rejoined after combat logging. Inventory cleared and killed.");
                    combatLoggedPlayers.remove(playerId);
                });
            }
        }
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
        // Gated: Run only in Mode 1 (ALL) or Mode 3 (PVP)
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 3) return;

        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        
        Player player = event.getPlayer();
        PlayerData data = playerDataMap.get(player.getUniqueId());
        if (data == null) return;
        
        if (data.lastDamageTime > 0) {
            long timeSinceDamage = System.currentTimeMillis() - data.lastDamageTime;
            
            if (timeSinceDamage <= MAX_SWING_DELAY_MS) {
                data.sequenceViolations = Math.max(0, data.sequenceViolations - 1); 
            } 
            data.lastDamageTime = 0; 
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Gated: Run only in Mode 1 (ALL) or Mode 3 (PVP)
        if (currentAntiCheatMode != 1 && currentAntiCheatMode != 3) return;
        
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            PlayerData attackerData = playerDataMap.get(attacker.getUniqueId());
            
            if (attackerData != null) {
                // ATTACK SPEED CHECK (Gated internally)
                checkAttackSpeed(attacker, attackerData);
                
                // ATTACK SEQUENCE TRACKER
                attackerData.lastDamageTime = System.currentTimeMillis();
            }
        }
        
        // Combat Logging Tracker (Enter Combat)
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player victim = (Player) event.getEntity();
            Player attacker = (Player) event.getDamager();
            long combatEndTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(COMBAT_TIMEOUT_SECONDS);
            
            PlayerData victimData = playerDataMap.get(victim.getUniqueId());
            if (victimData != null) victimData.combatEndTime = combatEndTimestamp;
            
            PlayerData attackerData = playerDataMap.get(attacker.getUniqueId());
            if (attackerData != null) attackerData.combatEndTime = combatEndTimestamp;
        }
        
        // Sequence Check Scheduler
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            PlayerData attackerData = playerDataMap.get(attacker.getUniqueId());
            
            long delayTicks = MAX_SWING_DELAY_MS / 50; 
            
            getServer().getScheduler().runTaskLater(this, () -> {
                if (attackerData != null) {
                    checkAttackSequence(attacker, attackerData); 
                }
            }, delayTicks + 1); 
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        
        Player player = event.getPlayer();
        PlayerData data = playerDataMap.get(player.getUniqueId());
        
        // Anti-PvP Logging Check (Gated: Run only in Mode 1 (ALL) or Mode 3 (PVP))
        if (currentAntiCheatMode == 1 || currentAntiCheatMode == 3) {
            if (data != null && data.isInCombat()) {
                
                // Drop Inventory Items
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
                
                // Clear the inventory immediately 
                player.getInventory().clear();
                
                // Mark the player for final punishment on next login
                combatLoggedPlayers.add(player.getUniqueId());
                
                // Kill the player (handles the broadcast)
                killPlayer(player, PVP_LOG_REASON);
            }
        }
        
        // Clean up data when player leaves
        playerDataMap.remove(player.getUniqueId());
    }

    // ----------------------------------------------------------------------
    // PUNISHMENT METHODS (Unchanged)
    // ----------------------------------------------------------------------

    private void kickPlayer(Player player, String reason) {
        String kickMessage = AC_PREFIX + ChatColor.RED + "You were kicked for " + reason + "!";
        player.kickPlayer(kickMessage);
        getLogger().warning(player.getName() + " was kicked for: " + reason);
    }
    
    private void killPlayer(Player player, String reason) {
        getServer().getScheduler().runTask(this, () -> {
            if (player.isOnline()) { 
                player.setHealth(0.0); 
                getServer().broadcastMessage(AC_PREFIX + ChatColor.DARK_RED + player.getName() + 
                                           " combat logged and died: " + ChatColor.WHITE + reason);
                getLogger().severe(player.getName() + " was KILLED for: " + reason);
            }
        });
    }
    
    private void punishPlayer(Player player, String cheatType, int violations) {
        getServer().getScheduler().runTask(this, () -> {
            
            int limit = 0;
            
            if (cheatType.equals("Flight")) {
                limit = FLY_VIOLATION_LIMIT;
            } else if (cheatType.equals("Chat Spam")) {
                limit = SPAM_VIOLATION_LIMIT;
            } else if (cheatType.equals("Illegal Attack Sequence")) { 
                limit = SEQUENCE_VIOLATION_LIMIT;
            } else if (cheatType.equals("Attack Speed (Autoclicker)")) { 
                limit = ATTACK_SPEED_VIOLATION_LIMIT;
            }

            if (limit > 0 && violations >= limit) {
                kickPlayer(player, cheatType + " (Excessive Violations)");
                PlayerData data = playerDataMap.get(player.getUniqueId());
                if (data != null) {
                    if (cheatType.equals("Flight")) data.flyViolations = 0;
                    if (cheatType.equals("Chat Spam")) data.spamViolations = 0;
                    if (cheatType.equals("Illegal Attack Sequence")) data.sequenceViolations = 0;
                    if (cheatType.equals("Attack Speed (Autoclicker)")) data.attackSpeedViolations = 0;
                }
            } else if (limit > 0) {
                player.sendMessage(AC_PREFIX + ChatColor.RED + "Warning! Detected potential " + cheatType + " (" + violations + "/" + limit + ")");
            }
        });
    }
}
