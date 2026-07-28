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

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Handles the plugin's per-session log file, log rotation, and the optional
 * live console debug echo.
 */
public class LogManager {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat FILE_NAME_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    private final JavaPlugin plugin;
    private final File logFolder;

    private boolean enableFileLogging;
    private int maxLogFiles;
    private boolean debugModeConsole = false;

    private File currentLogFile;

    public LogManager(JavaPlugin plugin, File logFolder, boolean enableFileLogging, int maxLogFiles) {
        this.plugin = plugin;
        this.logFolder = logFolder;
        this.enableFileLogging = enableFileLogging;
        this.maxLogFiles = maxLogFiles;
    }

    public void updateSettings(boolean enableFileLogging, int maxLogFiles) {
        this.enableFileLogging = enableFileLogging;
        this.maxLogFiles = maxLogFiles;
    }

    public void setupLoggingSession() {
        if (!logFolder.exists()) logFolder.mkdirs();

        String fileName = "log_" + FILE_NAME_FORMAT.format(new Date()) + ".txt";
        currentLogFile = new File(logFolder, fileName);

        try {
            currentLogFile.createNewFile();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create new log file: " + e.getMessage());
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::rotateLogs);
    }

    private void rotateLogs() {
        File[] files = logFolder.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files != null && files.length > maxLogFiles) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));

            int filesToDelete = files.length - maxLogFiles;
            for (int i = 0; i < filesToDelete; i++) {
                if (files[i].delete()) {
                    plugin.getLogger().info("Cleaned up an old log file: " + files[i].getName());
                }
            }
        }
    }

    public void toggleDebugConsole() {
        debugModeConsole = !debugModeConsole;
    }

    public boolean isDebugModeConsole() {
        return debugModeConsole;
    }

    public void logToFile(String source, String message) {
        if (debugModeConsole) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_GRAY + "[AvA-Debug | " + source + "] " + ChatColor.GRAY + message);
        }

        if (!enableFileLogging || currentLogFile == null) return;

        // Stop attempting async tasks when the plugin is already disabled.
        if (!plugin.isEnabled()) {
            writeLine(source, message);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeLine(source, message));
    }

    private void writeLine(String source, String message) {
        try (FileWriter fw = new FileWriter(currentLogFile, true)) {
            String timestamp = DATE_FORMAT.format(new Date());
            fw.write("[" + timestamp + "] [" + source + "] " + message + "\n");
        } catch (IOException e) {
            plugin.getLogger().severe("Uh oh, failed to write to log: " + e.getMessage());
        }
    }
}
