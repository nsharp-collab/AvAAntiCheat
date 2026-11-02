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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Checks GitHub for a newer release, optionally auto-downloads it into the
 * server's /update folder so it applies on next restart. Downloads are only
 * installed when the local SHA-256 matches the digest published on GitHub.
 */
public class UpdateManager {

    private static final String GITHUB_VERSION_URL = "https://raw.githubusercontent.com/nsharp-collab/AvAAntiCheat/refs/heads/main/version.txt";
    private static final String GITHUB_JAR_URL = "https://github.com/nsharp-collab/AvAAntiCheat/releases/latest/download/AvAAntiCheat.jar";
    private static final String GITHUB_SHA256_URL = "https://github.com/nsharp-collab/AvAAntiCheat/releases/latest/download/AvAAntiCheat.jar.sha256";
    private static final String GITHUB_SHA256_FALLBACK_URL = "https://raw.githubusercontent.com/nsharp-collab/AvAAntiCheat/refs/heads/main/AvAAntiCheat.jar.sha256";
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
            String[] currentParts = normalizeVersion(current).split("\\.");
            String[] onlineParts = normalizeVersion(online).split("\\.");
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

    /**
     * Strip non-numeric prefixes like DEV- so DEV-1.9.5 compares as 1.9.5.
     */
    static String normalizeVersion(String version) {
        if (version == null) return "0";
        String v = version.trim();
        // Drop leading letters/hyphens until we hit a digit (handles DEV-1.9.5-SPLIT)
        int i = 0;
        while (i < v.length() && !Character.isDigit(v.charAt(i))) {
            i++;
        }
        if (i >= v.length()) return "0";
        return v.substring(i);
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

        String expectedSha;
        try {
            expectedSha = fetchExpectedSha256();
        } catch (IOException e) {
            plugin.getLogger().severe("Auto-update aborted: could not fetch SHA-256 from GitHub (" + e.getMessage() + ").");
            return;
        }
        if (expectedSha == null || expectedSha.isBlank()) {
            plugin.getLogger().severe("Auto-update aborted: empty SHA-256 from GitHub.");
            return;
        }

        File updateFolder = Bukkit.getServer().getUpdateFolderFile();
        if (!updateFolder.exists()) {
            updateFolder.mkdirs();
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("AvAAntiCheat-update-", ".jar");
            try (InputStream in = new URL(GITHUB_JAR_URL).openStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            String actualSha = sha256Hex(tempFile);
            if (!expectedSha.equalsIgnoreCase(actualSha)) {
                plugin.getLogger().severe("Auto-update aborted: SHA-256 mismatch!");
                plugin.getLogger().severe("  Expected: " + expectedSha);
                plugin.getLogger().severe("  Actual:   " + actualSha);
                Files.deleteIfExists(tempFile);
                return;
            }

            File targetFile = new File(updateFolder, plugin.getPluginFile().getName());
            Files.copy(tempFile, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(tempFile);

            plugin.getLogger().info("Update downloaded and checksum verified: " + targetFile.getPath());
            plugin.getLogger().info("The new version will automatically apply on the next server restart.");

            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(
                    acPrefix + ChatColor.GREEN + "Plugin update " + latestVersion + " is ready. Restart the server to apply.",
                    "ava.admin"));

        } catch (Exception e) {
            plugin.getLogger().severe("Auto-download failed: " + e.getMessage());
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String fetchExpectedSha256() throws IOException {
        IOException last = null;
        for (String url : new String[]{GITHUB_SHA256_URL, GITHUB_SHA256_FALLBACK_URL}) {
            try (InputStream in = new URL(url).openStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line = reader.readLine();
                if (line == null) continue;
                // Accept either bare hex or "hex  filename" (sha256sum format)
                String token = line.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
                if (token.matches("[0-9a-f]{64}")) {
                    return token;
                }
            } catch (IOException e) {
                last = e;
            }
        }
        if (last != null) throw last;
        throw new IOException("No usable SHA-256 digest found");
    }

    static String sha256Hex(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = digest.digest(bytes);
        return HexFormat.of().formatHex(hash);
    }
}
