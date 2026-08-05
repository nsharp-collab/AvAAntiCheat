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

package com.nolan.ava.listeners;

import com.nolan.ava.AvAAntiCheat;
import com.nolan.ava.checks.ChatCheck;
import com.nolan.ava.checks.CombatChecks;
import com.nolan.ava.checks.DupeCheck;
import com.nolan.ava.checks.MovementChecks;
import com.nolan.ava.data.PlayerData;
import com.nolan.ava.util.SeeU;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.Location;

import java.nio.charset.StandardCharsets;
import java.util.Set;
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

    public AvAListener(AvAAntiCheat plugin, MovementChecks movementChecks, CombatChecks combatChecks, ChatCheck chatCheck) {
        this.plugin = plugin;
        this.movementChecks = movementChecks;
        this.combatChecks = combatChecks;
        this.chatCheck = chatCheck;
        this.dupeCheck = new DupeCheck(plugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals("minecraft:brand")) {
            try {
                // Minecraft prefixes packet strings with a length byte.
                // For a short string like a brand name, it's exactly 1 byte.
                int length = message[0];
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
            String command = event.getMessage().toLowerCase();
            if (command.startsWith("/home") || command.startsWith("/spawn") ||
                    command.startsWith("/tpa") || command.startsWith("/tpaccept") ||
                    command.startsWith("/back") || command.startsWith("/warp")) {

                event.setCancelled(true);
                player.sendMessage(plugin.getPrefix() + ChatColor.RED + "You cannot teleport while in combat!");
                plugin.logToFile(player.getName(), "Prevented combat log command: " + command);
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

            // Pure Vanilla Minecraft physically prevents you from sprinting with an open inventory.
            // If they are sprinting AND clicking items, they are using a hacked client.
            if (player.isSprinting() && !player.isFlying() && !player.isGliding()) {
                event.setCancelled(true);
                player.sendMessage(plugin.getPrefix() + ChatColor.RED + "Inventory actions while sprinting are blocked!");
                plugin.logToFile(player.getName(), "Blocked InventoryWalk (Sprinting Hack Detected)");
            }
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
        String channel = event.getChannel().toLowerCase();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        if (data != null && SeeU.isSeeUFix(channel)) {
            data.bypassAllChecks = true;
            return;
        }

        if (data != null && data.bypassAllChecks) {
            return;
        }

        for (String banned : plugin.getBannedMods()) {
            if (channel.contains(banned.toLowerCase())) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    PlayerData laterData = plugin.getPlayerData(player.getUniqueId());
                    if (laterData == null || laterData.bypassAllChecks) {
                        return;
                    }
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
        plugin.logToFile(player.getName(), "Player joined (IP: " + player.getAddress().getHostString() + ")");

        if (player.isOp() && plugin.getUpdateManager().isUpdateAvailable()) {
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
                    plugin.getCombatLoggedPlayers().remove(playerId);
                });
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode().toString().contains("CREATIVE") || player.getGameMode().toString().contains("SPECTATOR")) return;

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            movementChecks.checkSpider(event, data);
            movementChecks.checkFlight(event, data);
            movementChecks.checkSpeed(event, data);
            movementChecks.checkPhase(event, data);

            if (data.isRiptiding && player.isOnGround()) data.isRiptiding = false;

            if (data.isWindBursting && player.isOnGround() && (System.currentTimeMillis() - data.lastBreezeBoostTime > 1000)) {
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
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType().name().contains("TRIDENT")) {
            if (item.containsEnchantment(org.bukkit.enchantments.Enchantment.RIPTIDE)) {
                PlayerData data = plugin.getPlayerData(event.getPlayer().getUniqueId());
                if (data != null) {
                    data.isRiptiding = true;
                    data.lastVelocityTime = System.currentTimeMillis();
                    data.flyViolations = 0;
                    data.speedViolations = 0;
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
            data.lastVelocityTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data != null) {
                long now = System.currentTimeMillis();
                data.lastVelocityTime = now;
                data.lastCombatActivityTime = now;

                if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                        event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                    data.lastBreezeBoostTime = now;
                    data.isWindBursting = true;
                    data.flyViolations = 0;
                }

                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (item != null && item.getType().name().contains("MACE")) {
                        data.isWindBursting = true;
                        data.lastVelocityTime = now;
                    }
                }
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
                        data.lastVelocityTime = System.currentTimeMillis();
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
            data.lastVelocityTime = System.currentTimeMillis();
            data.flyViolations = 0;
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
        if (!plugin.isCheckCombatEnabled()) return;
        if (plugin.getCurrentAntiCheatMode() != 1 && plugin.getCurrentAntiCheatMode() != 3) return;

        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        if (com.nolan.ava.util.BlockUtils.isHighMobilityItem(player)) {
            data.lastDamageTime = 0;
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
            PlayerData victimData = plugin.getPlayerData(victim.getUniqueId());
            if (victimData != null) {
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
                }

                if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                    combatChecks.checkAttackSpeed(attacker, attackerData);
                }
                attackerData.lastDamageTime = now;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    combatChecks.checkAttackSequence(attacker, attackerData);
                }, 1);
            }
        }

        if (event.getEntity() instanceof Player && event.getDamager() instanceof LivingEntity) {
            Player victim = (Player) event.getEntity();
            LivingEntity damager = (LivingEntity) event.getDamager();
            if (damager instanceof Player) {
                Player attacker = (Player) damager;
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

                plugin.getCombatLoggedPlayers().add(player.getUniqueId());
                plugin.killPlayer(player, plugin.getPvpLogReason());
            }
        }
        plugin.removePlayerData(player.getUniqueId());
        plugin.logToFile(player.getName(), "Player quit.");
    }
}
