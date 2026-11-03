# AvAAntiCheat: Simple & Effective Minecraft Anti-Cheat



AvAAntiCheat is a lightweight, easy-to-use **anti-cheat plugin** designed for Minecraft Java servers running on the **Bukkit/Spigot API** (aka Bukkit, Spigot, Paper, Purpur). It helps maintain a fair and clean environment for players.

It is confirmed to work with any **Minecraft version 1.21.X**. Core checks use the Bukkit API only, so installing optional integrations is not required for normal use.

---

## Core Features / Detections

The anti-cheat actively monitors and logs several common unfair advantages:

* **Movement Hacks:** Flying, hover/air-time, no-fall, wall climbing (spider), speed hacks, phase detection
* **Combat:** Combat logging (persisted across restarts), attack speed / autoclicker, attack sequence, reach, line-of-sight (hit-through-blocks), multi-hit / multi-aura signals
* **Scaffold:** Abnormal block place-rate detection
* **Chat Abuse:** Chat spamming
* **Mod Banner:** Kick players who join with a banned client channel/mod (banned mods are set in the config)
* **Hardware Tester:** Benchmarks the host and chooses heavy vs light math check profiles
* **Only Kicks Players:** No bans are issued, in case of false positives
* **Bedrock Support (optional):** If [Geyser](https://geysermc.org/) is installed, AvA uses the Geyser API for better Bedrock ping estimates
* **Experimental Packet Checks (optional):** If [PacketEvents](https://github.com/retrooper/packetevents) is installed, optional packet-level timer / onGround / swing-order probes can be enabled (see below)
* **Experimental Move Simulator:** Optional server-side move residual logging (default off, log-only)

> **Note on Logging:** The system employs a violation counter. Detections are **logged** (e.g., first violation for minor flying) even if no immediate action is taken, allowing staff to track repeat offenders.

---

## Optional Integrations

These are **softdepends**. AvAAntiCheat starts and runs on **all 1.21.x** without them.

### Geyser (Bedrock)

* Install **Geyser-Spigot** if you allow Bedrock players.
* When present, AvA uses Geyser for Bedrock player detection and ping estimation.
* When absent, Java ping/`Player#getPing()` is used as usual.

### PacketEvents (experimental packet checks)

Packet-level hooks can change between Minecraft patches, so they are **not** bundled into AvA. Keeping PacketEvents optional preserves **1.21.x** compatibility for the core anti-cheat.

* Install the **PacketEvents** plugin separately if you want to test packet checks.
* Leave `enabled-checks.packet: false` (default) unless you are testing.
* For now, only enable packet checks on **Minecraft 1.21.11**. On other 1.21.x builds (or without PacketEvents), hooks stay inactive and Bukkit checks still run.
* Keep `experimental.log-only: true` while testing so probes log residuals without punishing.
* `/ac reload` re-evaluates packet hook registration when you toggle the config.

---

## Installation

Installing the plugin is straightforward:

### 1. Download the Plugin File

Choose your preferred method to obtain the `.jar` file:

* **GitHub:** Download the latest compiled version from the **GitHub Releases** page.
* **Plugin Sites:** Get the `.jar` file from **Modrinth** (or other supported sites).
* **Manual Compilation (For Developers):**
    * Clone the repository: `git clone https://github.com/nsharp-collab/AvAAntiCheat.git`
    * Run the Maven command: `mvn clean package`
    * The compiled file will be in your local `/target` directory.

### 2. Server Setup

1.  Place the `AvAAntiCheat.jar` file into your server's `/plugins` folder.
2.  *(Optional)* Install **Geyser-Spigot** for Bedrock ping support and/or **PacketEvents** for experimental packet checks.
3.  Restart your server.
4.  Verify installation using the command `/ac status`.

---

## Usage & Commands

For a full list of commands, permissions, and configuration options, please refer to the project **Wiki**.

[https://github.com/nsharp-collab/AvAAntiCheat/wiki/Commands]

---

## Troubleshooting / Known Issues

### Incorrect Version Display

If the `/ac status` command or startup messages report an incorrect Minecraft version (e.g., "1.8.8" when the file name says "1.9"):

* **Reason:** This is typically a display error where the developer forgot to update the version string in the plugin's internal configuration.
* **Action:** Please create a **GitHub Issue** detailing the version mismatch. The functionality of the anti-cheat itself should remain correct for the listed supported version.

### Experimental packet checks inactive

If you set `enabled-checks.packet: true` but see a warning at startup/reload:

* Confirm **PacketEvents** is installed and enabled.
* Confirm the server is **1.21.11** (other 1.21.x versions keep packet hooks off on purpose).
* Core movement/combat/chat checks are unaffected either way.

---

## Support, Issues, and Contribution

* **Reporting Issues:** If you find a bug, a crash, or a bypass, please open a detailed **Issue Ticket** on GitHub or on Discord.
* **Contributing:** Guidelines for submitting feature suggestions and pull requests can be found in the separate **CONTRIBUTING.md** file.
* **Questions/Server Help?:** Join the Discord [Here](https://discord.gg/QDQ8Bp7EqC) if you have any questions or want any help with the Anti-Cheat 

## Privacy & Data Collection

AvA Anti-Cheat values transparency. Please be aware of the following:

* **bStats Metrics**: This plugin uses [bStats](https://bstats.org/) to collect anonymous usage data (such as server version and active Anti-Cheat mode) to help improve development.
* **Local Logging**: Detections and administrative actions are logged locally to `anticheat_log.txt` within the plugin folder. This data stays on your server and is not shared with the developer.
* **Full Details**: For a complete list of third-party dependencies and licensing information, please refer to our [NOTICE](./NOTICE).
