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

package com.nolan.ava.listeners;

import com.nolan.ava.AvAAntiCheat;
import com.nolan.ava.checks.ChatCheck;
import com.nolan.ava.checks.CombatChecks;
import com.nolan.ava.checks.DupeCheck;
import com.nolan.ava.checks.MoveSimulator;
import com.nolan.ava.checks.MovementChecks;
import com.nolan.ava.checks.PacketChecks;
import com.nolan.ava.data.PlayerData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.util.Vector;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Wires every Bukkit event this plugin cares about to the appropriate check
 * or piece of plugin state.
 */
public class AvAListener implements Listener, PluginMessageListener {

    private final AvAAntiCheat plugin;
    private final MovementChecks movementChecks;
    private final CombatChecks combatChecks;
    private final ChatCheck chatCheck;
    private final DupeCheck dupeCheck;
    private final PacketChecks packetChecks;
    private final MoveSimulator moveSimulator;

    public AvAListener(AvAAntiCheat plugin,
                       MovementChecks movementChecks,
                       CombatChecks combatChecks,
                       ChatCheck chatCheck,
                       PacketChecks packetChecks,
                       MoveSimulator moveSimulator) {
        this.plugin = plugin;
        this.movementChecks = movementChecks;
        this.combatChecks = combatChecks;
        this.chatCheck = chatCheck;
        this.dupeCheck = new DupeCheck(plugin);
        this.packetChecks = packetChecks;
        this.moveSimulator = moveSimulator;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals("minecraft:brand")) {
            try {
                int length = message[0] & 0xFF;
                if (length <= 0 || 1 + length > message.length) return;
                String brand = new String(message, 1, length, StandardCharsets.UTF_8);

                PlayerData data = plugin.getPlayerData(player.getUniqueId());
                if (data != null) {
                    data.clientBrand = brand;
                }
            } catch (Exception e) {
                // If the packet format is weird, fail silently
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.isCheckCombatEnabled() || (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 3)) return;

        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        if (data != null && data.isInCombat()) {
            String command = event.getMessage().toLowerCase(java.util.Locale.ROOT).trim();
            String base = command.split("\\s+")[0];
            for (String blocked : plugin.getCombatBlockedCommands()) {
                if (base.equals(blocked) || command.startsWith(blocked + " ")) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getPrefix() + ChatColor.RED + "You cannot use that command while in combat!");
                    plugin.logToFile(player.getName(), "Prevented combat log command: " + command);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getCurrentAntiCheatMode() == 0) return;
        Player player = event.getPlayer();
        if (plugin.shouldBypassChecks(player)) return;
        if (player.getGameMode().toString().contains("CREATIVE") || player.getGameMode().toString().contains("SPECTATOR")) return;

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        long now = System.currentTimeMillis();
        if (now - data.scaffoldWindowStart > 1000L) {
            data.scaffoldWindowStart = now;
            data.scaffoldPlaceCount = 1;
            return;
        }
        data.scaffoldPlaceCount++;

        int ping = com.nolan.ava.util.PingUtils.getPlayerPing(player);
        int placeCap = 12 + Math.min(8, ping / 50);
        if (data.scaffoldPlaceCount > placeCap) {
            data.scaffoldViolations++;
            plugin.logToFile(player.getName(), "CHECK:Scaffold VIO=" + data.scaffoldViolations
                    + " Places/s=" + data.scaffoldPlaceCount + " Cap=" + placeCap);
            data.scaffoldPlaceCount = placeCap / 2;
            if (data.scaffoldViolations > plugin.getScaffoldViolationLimit()) {
                plugin.punishPlayer(player, "Scaffold (Place Rate)", data.scaffoldViolations);
            } else if (data.scaffoldViolations >= 2) {
                player.sendMessage(plugin.getPrefix() + ChatColor.RED
                        + "Warning! Unusual block place rate. ("
                        + data.scaffoldViolations + "/" + plugin.getScaffoldViolationLimit() + ")");
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (player.getGameMode().toString().contains("CREATIVE") || player.getGameMode().toString().contains("SPECTATOR")) return;

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            dupeCheck.checkInventoryClick(event, data);

            if (player.isSprinting() && !player.isFlying() && !player.isGliding()) {
                handleInventoryWalk(event, player, data);
            } else if (data.inventoryWalkStreak > 0) {
                data.inventoryWalkStreak = Math.max(0, data.inventoryWalkStreak - 1);
            }
        }
    }

    /**
     * Soft by default: severity (check-severity.inventory-walk) controls when we cancel.
     * 1-3 = log/warn only; 4-5 = cancel after a streak; 6-8 = cancel sooner.
     */
    private void handleInventoryWalk(InventoryClickEvent event, Player player, PlayerData data) {
        int severity = plugin.getInventoryWalkSeverity();
        data.inventoryWalkStreak++;

        int cancelAfter;
        if (severity <= 3) {
            cancelAfter = Integer.MAX_VALUE; // never cancel at soft settings
        } else if (severity <= 5) {
            cancelAfter = 5;
        } else if (severity <= 7) {
            cancelAfter = 2;
        } else {
            cancelAfter = 1;
        }

        if (data.inventoryWalkStreak == 1 || data.inventoryWalkStreak % 4 == 0) {
            plugin.logToFile(player.getName(),
                    "InventoryWalk streak=" + data.inventoryWalkStreak + " severity=" + severity);
        }

        if (data.inventoryWalkStreak >= cancelAfter) {
            event.setCancelled(true);
            if (data.inventoryWalkStreak == cancelAfter || data.inventoryWalkStreak % 3 == 0) {
                player.sendMessage(plugin.getPrefix() + ChatColor.RED
                        + "Inventory actions while sprinting are blocked!");
            }
            return;
        }

        if (severity <= 3 && data.inventoryWalkStreak >= 6 && data.inventoryWalkStreak % 6 == 0) {
            player.sendMessage(plugin.getPrefix() + ChatColor.YELLOW
                    + "Warning: inventory use while sprinting looks suspicious.");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        if (player.getGameMode().toString().contains("CREATIVE") || player.getGameMode().toString().contains("SPECTATOR")) return;

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            dupeCheck.checkInventoryClose(event, data);
        }
    }

    @EventHandler
    public void onPlayerRegisterChannel(PlayerRegisterChannelEvent event) {
        if (!plugin.isCheckModsEnabled() || plugin.getCurrentAntiCheatMode() == 0) return;

        Player player = event.getPlayer();
        if (plugin.shouldBypassChecks(player)) return;

        String channel = event.getChannel().toLowerCase();

        for (String banned : plugin.getBannedMods()) {
            if (banned == null || banned.length() < 3) continue; // avoid short-string false positives
            if (channel.contains(banned.toLowerCase())) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline() || plugin.shouldBypassChecks(player)) return;
                    String logMessage = "AUTOMATICALLY KICKED " + player.getName() + " for Banned Mod Channel: " + channel;
                    plugin.getServer().broadcastMessage(plugin.getPrefix() + ChatColor.DARK_RED + player.getName() + " was kicked for using a banned mod.");
                    plugin.notifyOperators(player.getName() + " was kicked for banned mod channel: " + channel + ".");
                    plugin.kickPlayer(player, "Banned Client Mod (" + banned + ")");
                    plugin.logToFile(player.getName(), logMessage);
                }, 4L);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        plugin.putPlayerData(playerId, new PlayerData());
        String ip = "unknown";
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            ip = player.getAddress().getHostString();
        }
        plugin.logToFile(player.getName(), "Player joined (IP: " + ip + ")");

        if (player.hasPermission("ava.admin") && plugin.getUpdateManager().isUpdateAvailable()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(plugin.getPrefix() + ChatColor.RED + ChatColor.BOLD + "AVA ANTICHEAT UPDATE AVAILABLE!");
                player.sendMessage(plugin.getPrefix() + ChatColor.YELLOW + "Current Version: " + ChatColor.WHITE + plugin.getVersion());
                player.sendMessage(plugin.getPrefix() + ChatColor.YELLOW + "Latest Version: " + ChatColor.GREEN + plugin.getUpdateManager().getLatestVersion());
                player.sendMessage(plugin.getPrefix() + ChatColor.GRAY + "Changelog:\n" + plugin.getUpdateManager().getChangelog());
                if (plugin.getUpdateManager().isAutoUpdateEnabled()) {
                    player.sendMessage(plugin.getPrefix() + ChatColor.GREEN + "The update has been auto-downloaded. Just restart whenever to apply.");
                } else {
                    player.sendMessage(plugin.getPrefix() + ChatColor.GRAY + "Please download the update from Modrinth/Github.");
                }
            }, 60L);
        }

        if (plugin.getCurrentAntiCheatMode() == 1 || plugin.getCurrentAntiCheatMode() == 3) {
            if (plugin.getCombatLoggedPlayers().contains(playerId)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.getInventory().clear();
                    player.setHealth(0.0);
                    player.sendMessage(plugin.getPrefix() + ChatColor.RED + "You combat logged! Your inventory was cleared and you were killed.");
                    plugin.logToFile(player.getName(), "Punished for Combat Logging on rejoin.");
                    plugin.clearCombatLogger(playerId);
                });
            }
        }

        if (plugin.isCheckPacketEnabled()) {
            packetChecks.logUnavailableOnce();
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        // Short grace so /home, portals, chorus, plugin TPs don't trip movement checks.
        long graceMs = 1500L;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            graceMs = 2200L;
        } else if (cause == PlayerTeleportEvent.TeleportCause.UNKNOWN
                || cause == PlayerTeleportEvent.TeleportCause.PLUGIN
                || cause == PlayerTeleportEvent.TeleportCause.COMMAND) {
            graceMs = 2000L;
        }
        data.extendTeleportExempt(graceMs);
        data.flyViolations = 0;
        data.speedViolations = Math.max(0, data.speedViolations - 1);
        data.spiderTicks = 0;
        moveSimulator.applyKnockbackHint(data, player.getVelocity());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode().toString().contains("CREATIVE") || player.getGameMode().toString().contains("SPECTATOR")) return;
        if (event.getTo() == null) return;

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            // Run MoveSimulator before flight updates wasOnGround (jump detection needs prior ground).
            moveSimulator.onPlayerMove(event, data);

            movementChecks.checkSpider(event, data);
            movementChecks.checkFlight(event, data);
            movementChecks.checkSpeed(event, data);
            movementChecks.checkPhase(event, data);

            // Experimental packet Bukkit fallback — no-op when disabled / PE hooks active
            packetChecks.onPlayerMove(event, data);

            if (data.isRiptiding && MovementChecks.isServerOnGround(player)) data.isRiptiding = false;

            if (data.isWindBursting && MovementChecks.isServerOnGround(player)
                    && (System.currentTimeMillis() - data.lastBreezeBoostTime > 1000)) {
                data.isWindBursting = false;
            }
        }
    }

    @EventHandler
    public void onGlideToggle(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player) {
            PlayerData data = plugin.getPlayerData(event.getEntity().getUniqueId());
            if (data != null) {
                data.lastGlideTime = System.currentTimeMillis();
                data.isGliding = event.isGliding();
                if (!event.isGliding()) {
                    data.glideEndTime = System.currentTimeMillis();
                }
            }
        }
    }

    @EventHandler
    public void onPlayerRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            data.isRiptiding = true;
            data.flyViolations = 0;
            data.spiderTicks = 0;
            data.speedViolations = 0;
            data.extendVelocityExempt(1200);
            moveSimulator.applyKnockbackHint(data, player.getVelocity());
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data != null) {
                long durationMs = Math.min(800L, Math.max(200L, (long) (event.getFinalDamage() * 45)));
                if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                        || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                    durationMs = 1200L;
                    data.lastBreezeBoostTime = System.currentTimeMillis();
                    data.isWindBursting = true;
                    data.flyViolations = 0;
                }

                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    data.tookFallDamage = true;
                    data.lastFallDamageTime = System.currentTimeMillis();
                    data.serverFallDistance = 0.0;
                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (item != null && item.getType().name().contains("MACE")) {
                        data.isWindBursting = true;
                        durationMs = Math.max(durationMs, 1000L);
                    }
                }

                data.extendVelocityExempt(durationMs);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();

        if (proj.getType().name().contains("WIND_CHARGE") || proj.getType().name().contains("BREEZE")) {
            Location hitLoc = proj.getLocation();

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getWorld().equals(hitLoc.getWorld()) && p.getLocation().distanceSquared(hitLoc) < 20.25) {
                    PlayerData data = plugin.getPlayerData(p.getUniqueId());
                    if (data != null) {
                        data.lastBreezeBoostTime = System.currentTimeMillis();
                        data.extendVelocityExempt(900);
                        data.isWindBursting = true;
                        data.flyViolations = 0;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerVelocity(org.bukkit.event.player.PlayerVelocityEvent event) {
        PlayerData data = plugin.getPlayerData(event.getPlayer().getUniqueId());
        if (data != null) {
            Vector vel = event.getVelocity();
            double mag = vel.length();
            long durationMs = Math.min(1500L, Math.max(250L, (long) (mag * 800)));
            data.extendVelocityExempt(durationMs);
            data.flyViolations = 0;
            moveSimulator.applyKnockbackHint(data, vel);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        PlayerData data = plugin.getPlayerData(event.getPlayer().getUniqueId());
        if (data != null) {
            chatCheck.checkSpam(event, data);
        }
    }

    @EventHandler
    public void onPlayerAnimate(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        data.lastSwingTime = System.currentTimeMillis();
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            PlayerData victimData = plugin.getPlayerData(victim.getUniqueId());
            if (victimData != null) {
                victimData.extendVelocityExempt(Math.min(800L, Math.max(250L, (long) (event.getFinalDamage() * 45))));

                String damagerType = event.getDamager().getType().name();
                if (damagerType.contains("BREEZE") || damagerType.contains("WIND_CHARGE")) {
                    victimData.lastBreezeBoostTime = System.currentTimeMillis();
                    victimData.isWindBursting = true;
                    victimData.flyViolations = 0;
                    victimData.spiderTicks = 0;
                    victimData.extendVelocityExempt(900);
                }
            }
        }

        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 3) return;
        if (!plugin.isCheckCombatEnabled()) return;

        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            PlayerData attackerData = plugin.getPlayerData(attacker.getUniqueId());
            if (attackerData != null) {
                long now = System.currentTimeMillis();
                attackerData.lastCombatActivityTime = now;

                if (event.getEntity() == attacker) {
                    attackerData.isRiptiding = false;
                    attackerData.isWindBursting = false;
                    return;
                }

                if (com.nolan.ava.util.BlockUtils.isHighMobilityItem(attacker)) {
                    attackerData.isWindBursting = true;
                    attackerData.flyViolations = 0;
                    attackerData.extendVelocityExempt(800);
                } else if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                    combatChecks.checkAttackSpeed(attacker, attackerData);
                    combatChecks.checkAttackSequence(attacker, attackerData);
                    combatChecks.checkReachAndLos(attacker, event.getEntity(), attackerData);
                    combatChecks.checkMultiHit(attacker, event.getEntity(), attackerData);
                }
                attackerData.lastDamageTime = now;
            }
        }

        // Combat-tag for melee and projectile shooters (arrows, tridents, etc.).
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            Player attacker = null;
            if (event.getDamager() instanceof Player) {
                attacker = (Player) event.getDamager();
            } else if (event.getDamager() instanceof Projectile) {
                Projectile proj = (Projectile) event.getDamager();
                if (proj.getShooter() instanceof Player) {
                    attacker = (Player) proj.getShooter();
                }
            }

            if (attacker != null && !attacker.equals(victim)) {
                long now = System.currentTimeMillis();
                long combatEndTimestamp = now + TimeUnit.SECONDS.toMillis(plugin.getCombatTimeoutSeconds());

                PlayerData victimData = plugin.getPlayerData(victim.getUniqueId());
                if (victimData != null) {
                    victimData.combatEndTime = combatEndTimestamp;
                    victimData.lastAttacker = attacker.getUniqueId();
                    victimData.lastCombatActivityTime = now;
                }

                PlayerData attackerData = plugin.getPlayerData(attacker.getUniqueId());
                if (attackerData != null) {
                    attackerData.combatEndTime = combatEndTimestamp;
                    attackerData.lastAttacker = victim.getUniqueId();
                    attackerData.lastCombatActivityTime = now;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        if (data != null) {
            data.combatEndTime = 0;

            if (data.combatBossBar != null) {
                data.combatBossBar.removeAll();
                data.combatBossBar = null;
            }

            if (data.lastAttacker != null) {
                PlayerData attackerData = plugin.getPlayerData(data.lastAttacker);
                if (attackerData != null && attackerData.lastAttacker != null && attackerData.lastAttacker.equals(player.getUniqueId())) {
                    attackerData.combatEndTime = 0;
                    attackerData.lastAttacker = null;

                    if (attackerData.combatBossBar != null) {
                        attackerData.combatBossBar.removeAll();
                        attackerData.combatBossBar = null;
                    }

                    Player attacker = plugin.getServer().getPlayer(data.lastAttacker);
                    if (attacker != null && attacker.isOnline()) {
                        String clearMsg = ChatColor.GREEN + "Combat ended (Opponent died).";
                        if (plugin.getCombatTimerPosition().equalsIgnoreCase("SUBTITLE")) {
                            attacker.sendTitle("", clearMsg, 0, 40, 10);
                        } else if (plugin.getCombatTimerPosition().equalsIgnoreCase("ACTION_BAR")) {
                            attacker.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(clearMsg));
                        }
                    }
                }
            }
            data.lastAttacker = null;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        if (data != null && data.combatBossBar != null) {
            data.combatBossBar.removeAll();
            data.combatBossBar = null;
        }

        if (plugin.isCheckCombatEnabled() && (plugin.getCurrentAntiCheatMode() == 1 || plugin.getCurrentAntiCheatMode() == 3)) {
            if (data != null && data.isInCombat()) {
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }

                player.getInventory().clear();

                plugin.markCombatLogger(player.getUniqueId());
                plugin.killPlayer(player, plugin.getPvpLogReason());
            }
        }
        plugin.removePlayerData(player.getUniqueId());
        plugin.logToFile(player.getName(), "Player quit.");
    }
}
