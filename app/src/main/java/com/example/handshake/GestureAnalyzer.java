package com.example.handshake;

import com.google.mediapipe.framework.formats.NormalizedLandmark;
import java.util.List;

/**
 * Analyzes hand landmarks from MediaPipe to detect grab and release gestures.
 * Uses Euclidean distance between fingertips (landmarks 8,12,16,20) and palm base (landmark 0).
 * Implements hysteresis to prevent rapid toggling between states.
 */
public class GestureAnalyzer {
    // Thresholds for normalized coordinates (0-1 range from MediaPipe)
    private static final float GRAB_THRESHOLD = 0.08f;   // Distance below this -> grab
    private static final float RELEASE_THRESHOLD = 0.12f; // Distance above this -> release (higher than grab for hysteresis)

    private boolean isGrabbing = false;

    /**
     * Possible gesture states.
     */
    public enum GestureState {
        IDLE,   // No meaningful gesture
        GRAB,   // Fingertips close to palm (pinching)
        RELEASE // Fingertips moved away from palm (releasing pinch)
    }

    /**
     * Analyzes the current hand landmarks to determine gesture state.
     *
     * @param landmarks List of 21 hand landmarks from MediaPipe HandLandmarker
     * @return Current gesture state
     */
    public GestureState analyze(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() < 21) {
            return GestureState.IDLE;
        }

        // Get palm base (landmark 0)
        NormalizedLandmark palmBase = landmarks.get(0);
        double palmBaseX = palmBase.getX();
        double palmBaseY = palmBase.getY();
        double palmBaseZ = palmBase.getZ();

        // Calculate average distance of four fingertips to palm base
        double totalDistance = 0.0;
        int[] fingertipIndices = {8, 12, 16, 20}; // Index, middle, ring, pinky tips
        for (int index : fingertipIndices) {
            NormalizedLandmark fingertip = landmarks.get(index);
            double dx = fingertip.getX() - palmBaseX;
            double dy = fingertip.getY() - palmBaseY;
            double dz = fingertip.getZ() - palmBaseZ;
            double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);
            totalDistance += distance;
        }
        double avgDistance = totalDistance / fingertipIndices.length;

        // Apply hysteresis based on current state
        if (isGrabbing) {
            // Currently grabbing - require higher threshold to release
            if (avgDistance > RELEASE_THRESHOLD) {
                isGrabbing = false;
                return GestureState.RELEASE;
            } else {
                return GestureState.GRAB;
            }
        } else {
            // Currently not grabbing - check if we should grab
            if (avgDistance < GRAB_THRESHOLD) {
                isGrabbing = true;
                return GestureState.GRAB;
            } else {
                return GestureState.IDLE;
            }
        }
    }

    /**
     * @return True if currently in grab state
     */
    public boolean isGrabbing() {
        return isGrabbing;
    }

    /**
     * Resets the analyzer to idle state.
     */
    public void reset() {
        isGrabbing = false;
    }
}