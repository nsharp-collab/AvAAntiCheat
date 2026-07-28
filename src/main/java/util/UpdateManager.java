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

package com.nolan.ava.util;

import com.nolan.ava.AvAAntiCheat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Checks GitHub for a newer release, optionally auto-downloads it into the
 * server's /update folder so it applies on next restart.
 */
public class UpdateManager {

    private static final String GITHUB_VERSION_URL = "https://raw.githubusercontent.com/nsharp-collab/AvAAntiCheat/refs/heads/main/version.txt";
    private static final String GITHUB_JAR_URL = "https://github.com/nsharp-collab/AvAAntiCheat/releases/latest/download/AvAAntiCheat.jar";
    private static final String GITHUB_CHANGELOG_URL = "https://raw.githubusercontent.com/nsharp-collab/AvAAntiCheat/refs/heads/main/changelog.txt";

    private final AvAAntiCheat plugin;
    private final String currentVersion;
    private final String acPrefix;

    private boolean autoUpdateEnabled;
    private boolean updateAvailable = false;
    private String latestVersion;
    private String changelog = "No changelog available.";

    public UpdateManager(AvAAntiCheat plugin, String currentVersion, String acPrefix, boolean autoUpdateEnabled) {
        this.plugin = plugin;
        this.currentVersion = currentVersion;
        this.latestVersion = currentVersion;
        this.acPrefix = acPrefix;
        this.autoUpdateEnabled = autoUpdateEnabled;
    }

    public void setAutoUpdateEnabled(boolean autoUpdateEnabled) {
        this.autoUpdateEnabled = autoUpdateEnabled;
    }

    public boolean isAutoUpdateEnabled() {
        return autoUpdateEnabled;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getChangelog() {
        return changelog;
    }

    public void checkVersionAndDownload() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                try (InputStream in = new URL(GITHUB_VERSION_URL).openStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    latestVersion = reader.readLine();
                }

                try (InputStream in = new URL(GITHUB_CHANGELOG_URL).openStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    StringBuilder changelogBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        changelogBuilder.append(line).append("\n");
                    }
                    if (changelogBuilder.length() > 0) {
                        changelog = changelogBuilder.toString();
                    }
                } catch (Exception ignored) {
                }

                if (latestVersion != null) {
                    if (isNewerVersion(currentVersion, latestVersion)) {
                        updateAvailable = true;
                        plugin.getLogger().info("A new version is available: " + latestVersion);
                        plugin.getLogger().info("--- CHANGELOG ---");
                        plugin.getLogger().info(changelog);
                        plugin.getLogger().info("-----------------");

                        if (autoUpdateEnabled) {
                            downloadUpdate();
                        }
                    } else {
                        plugin.getLogger().info("You are running the latest version (or a development build).");
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
            }
        });
    }

    boolean isNewerVersion(String current, String online) {
        try {
            String[] currentParts = current.split("\\.");
            String[] onlineParts = online.split("\\.");
            int length = Math.max(currentParts.length, onlineParts.length);

            for (int i = 0; i < length; i++) {
                int currentVal = i < currentParts.length ? leadingNumber(currentParts[i]) : 0;
                int onlineVal = i < onlineParts.length ? leadingNumber(onlineParts[i]) : 0;

                if (onlineVal > currentVal) return true;
                if (currentVal > onlineVal) return false;
            }
        } catch (Exception e) {
            return !current.equalsIgnoreCase(online);
        }
        return false;
    }

    private int leadingNumber(String segment) {
        int end = 0;
        while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
            end++;
        }
        if (end == 0) return 0;
        return Integer.parseInt(segment.substring(0, end));
    }

    private void downloadUpdate() {
        plugin.getLogger().info("Automatically pulling down the latest update...");

        File updateFolder = Bukkit.getServer().getUpdateFolderFile();
        if (!updateFolder.exists()) {
            updateFolder.mkdirs();
        }

        File targetFile = new File(updateFolder, plugin.getPluginFile().getName());

        try (InputStream in = new URL(GITHUB_JAR_URL).openStream()) {
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            plugin.getLogger().info("Update downloaded to " + targetFile.getPath());
            plugin.getLogger().info("The new version will automatically apply on the next server restart.");

            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(
                    acPrefix + ChatColor.GREEN + "Plugin update " + latestVersion + " is ready. Restart the server to apply.",
                    "ava.admin"));

        } catch (IOException e) {
            plugin.getLogger().severe("Man, auto-download failed: " + e.getMessage());
        }
    }
}