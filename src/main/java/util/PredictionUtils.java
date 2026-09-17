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

public final class PredictionUtils {

    private PredictionUtils() {
    }

    public static double exponentialMovingAverage(double current, double sample, double alpha) {
        return current == 0.0 ? sample : (alpha * sample) + ((1.0 - alpha) * current);
    }

    public static double updateVariance(double currentVariance, double mean, double sample, double alpha) {
        double delta = sample - mean;
        double incremental = delta * delta;
        return currentVariance == 0.0 ? incremental : (alpha * incremental) + ((1.0 - alpha) * currentVariance);
    }

    public static double calculateStandardDeviation(double variance) {
        return Math.sqrt(Math.max(variance, 0.0));
    }

    public static double predictAirborneVertical(double lastDeltaY, double averageDeltaY) {
        double gravity = 0.08;
        double drag = 0.985;
        double expectedY = (lastDeltaY - gravity) * drag + 0.015;

        if (averageDeltaY > 0.0) {
            expectedY = expectedY * 0.78 + averageDeltaY * 0.22;
        }

        return Math.max(0.0, expectedY);
    }

    public static double predictGroundSpeed(double lastHorizontalDistance, double averageHorizontalSpeed, double variance) {
        double momentum = (lastHorizontalDistance * 0.76) + (averageHorizontalSpeed * 0.24);
        double stdev = calculateStandardDeviation(variance);
        double buffer = Math.max(0.04, stdev * 1.15 + 0.01);
        double stability = Math.max(lastHorizontalDistance, averageHorizontalSpeed);

        return Math.max(0.15, momentum + buffer + (stability * 0.04));
    }

    public static double predictAttackInterval(double averageInterval, double variance) {
        if (averageInterval <= 0.0) {
            return 0.16;
        }
        double stdev = calculateStandardDeviation(variance);
        double safetyMargin = Math.min(averageInterval * 0.22, stdev * 0.45);
        return Math.max(0.08, averageInterval - safetyMargin);
    }
}
