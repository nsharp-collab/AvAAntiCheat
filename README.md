# AvAAntiCheat: Simple & Effective Minecraft Anti-Cheat



AvAAntiCheat is a lightweight, easy-to-use **anti-cheat plugin** designed for Minecraft Java servers running on the **Bukkit/Spigot API** (aka, Bukkit, Spigot,Paper,Purpur). It helps maintain a fair and clean environment for players.

It is confirmed to work with any **Minecraft version 1.21.X**.

---

## Core Features / Detections

The anti-cheat actively monitors and logs several common unfair advantages:

* **Movement Hacks:** Flying, Wall Climbing, Speed Hacks, Spider, Phase detection
* **Combat Evasion:** Combat Logging
* **Unfair Automation:** Auto Clicking
* **Chat Abuse:** Chat Spamming
* **Exploits:** Select types of Packet Manipulation
* **Mod Banner:** Able to kick players from the game if they try to join with a banned mod (Banned mods can be set in the config file)
* **Hardware Tester:** Tests the hardware the Anti-Cheat is running on to determine if it can use complex heavy math checks or if it's only able to do lighter math checks
* **Only Kicks Players:** No bans are issued just incase of false positives
* **Bedrock Support:** Detects if you have Geyser on your server and if you do, it will use the Geyser API to better detect ping for Bedrock players
* **More detections are comming soon**

> **Note on Logging:** The system employs a violation counter. Detections are **logged** (e.g., first violation for minor flying) even if no immediate action is taken, allowing staff to track repeat offenders.

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
2.  Restart your server.
3.  Verify installation using the command `/ac status`.

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

---

## Support, Issues, and Contribution

* **Reporting Issues:** If you find a bug, a crash, or a bypass, please open a detailed **Issue Ticket** on GitHub or on Discord.
* **Contributing:** Guidelines for submitting feature suggestions and pull requests can be found in the separate **CONTRIBUTING.md** file.
* **Questions/Server Help?:** Join the Discord [Here](https://discord.gg/QDQ8Bp7EqC) if you have any questions or want any help with the Anti-Cheat 

## Privacy & Data Collection

AvA Anti-Cheat values transparency. Please be aware of the following:

* **bStats Metrics**: This plugin uses [bStats](https://bstats.org/) to collect anonymous usage data (such as server version and active Anti-Cheat mode) to help improve development.
* **Local Logging**: Detections and administrative actions are logged locally to `anticheat_log.txt` within the plugin folder. This data stays on your server and is not shared with the developer.
* **Full Details**: For a complete list of third-party dependencies and licensing information, please refer to our [Notice.md](./notice.md).
