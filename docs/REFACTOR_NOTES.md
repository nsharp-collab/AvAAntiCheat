# AvAAntiCheat refactor notes

## New layout

```
src/main/java/com/nolan/ava/
  AvAAntiCheat.java              main plugin class: onEnable/onDisable, config loading,
                                 combat-timer task, punish/kick/kill, shared getters
  data/PlayerData.java           per-player runtime state (was a private nested class)
  util/HardwareManager.java      hardware benchmark + HIGH_PERFORMANCE/OPTIMIZED_LIGHT mode
  util/LogManager.java           file logging, log rotation, console debug toggle
  util/UpdateManager.java        GitHub version check + auto-download (version-parsing bug fixed here)
  util/BlockUtils.java           static block/material classification helpers
  util/PingUtils.java            Bedrock detection + cross-platform ping estimate
  checks/MovementChecks.java     flight, speed, spider-climb, phase (speed tick-scaling bug fixed here)
  checks/CombatChecks.java       attack-sequence + attack-speed (autoclicker) checks
  checks/ChatCheck.java          chat spam detection
  listeners/AvAListener.java     every @EventHandler + the plugin-message (client brand) listener
  commands/ACCommandExecutor.java /ac

src/main/resources/plugin.yml    command + permission registration
src/main/resources/config.yml    defaults matching AvAAntiCheat#loadConfigValues
pom.xml                          Maven build (Spigot API, bStats, Geyser API as provided)
```
