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

import java.util.logging.Logger;

/**
 * Benchmarks the host machine on startup and picks between a more accurate
 * (HIGH_PERFORMANCE) or cheaper (OPTIMIZED_LIGHT) set of check math.
 * Can be overridden manually via /ac perf.
 */
public class HardwareManager {

    public static final String HIGH_PERFORMANCE = "HIGH_PERFORMANCE";
    public static final String OPTIMIZED_LIGHT = "OPTIMIZED_LIGHT";

    private final Logger logger;
    private String currentHardwareMode = OPTIMIZED_LIGHT;
    private boolean forced = false;

    public HardwareManager(Logger logger) {
        this.logger = logger;
    }

    public void detect() {
        if (forced) return;

        int usableThreads = Runtime.getRuntime().availableProcessors();

        long start = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            Math.sqrt(i);
        }
        long duration = System.nanoTime() - start;

        boolean fastCores = duration < 8_000_000;

        if (usableThreads >= 6 && fastCores) {
            currentHardwareMode = HIGH_PERFORMANCE;
        } else {
            currentHardwareMode = OPTIMIZED_LIGHT;
        }

        logger.info("AvA Hardware Profiler: Mode set to " + currentHardwareMode +
                " (Threads: " + usableThreads + ", Fast Cores: " + fastCores +
                ", Startup Time: " + (duration / 1000000.0) + "ms)");
    }

    public void forceMode(String mode) {
        this.currentHardwareMode = mode;
        this.forced = true;
    }

    public void resetToAuto() {
        this.forced = false;
        detect();
    }

    public boolean isForced() {
        return forced;
    }

    public boolean isHighPerformance() {
        return HIGH_PERFORMANCE.equals(currentHardwareMode);
    }

    public String getCurrentHardwareMode() {
        return currentHardwareMode;
    }
}
