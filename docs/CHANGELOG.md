# Changelog
## DEV-[1.9.5]-MATH - 2026-03-17
AvA AntiCheat - Changelog
# THIS IS A TESTING BUILD THAT IS STILL IN TESTING, THIS IS NOT FINISHED AT ALL, BE WARNED WHEN USING IT
v1.9.5 - Advanced Math & Performance Update
-------------------------------------------
This version introduces dynamic hardware profiling and completely rewrites the core movement checks for high-performance servers.

Additions & Changes:
- Added Smart Hardware Profiling. The plugin now runs a short startup benchmark to test your CPU. Fast processors will automatically use the new strict vector math, while budget or ARM hosts will fall back to the classic lightweight checks to preserve TPS.
- Added the /ac perf <high|light|auto> command, allowing server owners to manually override the hardware profile.
- Integrated native Bedrock support via the Geyser API. Bedrock players automatically receive a slight leniency buffer on movement checks to account for packet translation jitter, reducing false positives.
- Rewrote the Speed check for high-performance mode to use Time-Delta calculations. It now measures the time elapsed between packets rather than just flat distance, preventing players from saving up lag to teleport.
- Added Vector Gravity Prediction to the Flight check. It now calculates the exact expected Y-velocity using Minecraft's internal gravity formula to catch hover and slow-fall cheats.
- Upgraded the Phase check with Vector Ray-Tracing to detect horizontal clipping (H-Clip) through walls, even if the start and end blocks are both air.
- Implemented a strict Terminal Velocity limit to instantly block vertical downward clipping (V-Clip).
- Cleaned up the Maven pom.xml dependencies to ensure the plugin stays lightweight without pulling in unnecessary Geyser UI libraries.
- Added customizable combat timer UI positions (ActionBar, BossBar, Subtitle).


## DEV-[1.9.4.6] - 2026-03-07

- **Build** THIS IS A DEV BUILD, EXPECT BUGS AND BE WARNED WHEN USING
- **Fix:** Added grace period after elytra unequip to prevent false speed kick violations. Players landing from flight now have 7 seconds (configurable) of exemption from speed checks to allow residual velocity to dissipate naturally.
- **Dev:** Implemented `glideEndTime` tracking in PlayerData to monitor when players stop gliding.
- **Dev:** Made elytra grace period duration configurable via `speed-check.grace-period-ms` in `config.yml`.

## [1.9.4] - 2026-02-19

- **New Feature:** Implemented phasing detection that prevents players from passing through walls via packet manipulation or enderpearl clipping. Players caught phasing are rubber-banded back to their previous location.
- **Dev:** Added debug mode (`/ac debug`) allowing server operators to stream live anti-cheat logs to console for real-time monitoring.
- **Code:** Refactored logging system to run asynchronously; file I/O no longer blocks the main thread.
- **Code:** Added config version checking with automatic backup of outdated `config.yml` files to prevent configuration loss during updates.
- **Dev:** Made `checkPhaseEnabled` configurable in `config.yml` to allow operators to customize phase detection.
- **Code:** Improved comment clarity throughout codebase for better maintainability.
- **Build:** Updated version references across `pom.xml`, `plugin.yml`, and source code to v1.9.4.

## [1.9.2] - 2025-12-27

- **Dev:** Added a lightweight version-check notification so server operators are informed when the plugin is out of date.
- **Fix:** Prevented false kicks caused by Breeze charge attacks and related interactions; improved wall-climbing detection to avoid incorrect flags.
 - **Code:** Implemented new wall-climbing detection logic to better differentiate legitimate climbing from exploit-like behavior, reducing false positives.
 - **Release:** This version is intended for regular use (not a DEV/TEST build).

## [1.9.1.1-DEV] - 2025-12-08
- **THIS IS A TEST VERSION ONLY** Do not use in an actual server unless you know the security risk's (THERE MAY BE BUGS)
- **Dev:** Added a configuration system (`config.yml`) so server admins can set the plugin startup mode, enable/disable file logging, and change per-cheat kick limits.
- **Dev:** Made violation limits configurable at runtime (`flight`, `chat-spam`, `sequence`, `attack-speed`).
- **Dev:** `logToFile` now respects `enable-logging` and the log file is only created when logging is enabled.
- **Test:** Performed local compile/fix cycle to replace hard-coded constants with config-backed fields.

## [1.9.1] - 2025-12-02

- **Fix:** Corrected a major bug where players using the Riptide enchantment were incorrectly flagged and kicked for flying.
- **Fix:** Resolved a compilation error in the `killPlayer` method caused by a typo in the log message.
- **Code:** Refactored Riptide detection. Logic was removed from `EntityDamageByEntityEvent` and implemented correctly using the `PlayerRiptideEvent`.
- **Build:** Successful build artifact: `target/AvAAntiCheat-1.9.1.jar`.

## [1.8.8] - 2025-11-12

- **Build:** Switched project Java target to `17` to match Spigot API bytecode version and container JDK.
- **Build:** Updated `/pom.xml` to set `<maven.compiler.source>` and `<maven.compiler.target>` to `17`.
- **Code:** Removed dependency on `TridentMeta` and replaced `instanceof TridentMeta` checks with `ItemMeta.hasEnchant(...)`.
- **Dev:** Set the container JDK to Java 17 using SDKMAN and verified `mvn clean package` succeeds.
- **Output:** Successful build artifact: `target/AvAAntiCheat-1.8.8.jar`.

Notes:
- If you want to target Java 21 instead, update your environment JDK to 21 and set the Maven compiler properties back to `21`.

# Changelog was generated by generative A.I