package com.example.handshake;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayList;
import java.util.List;

/**
 * Main activity for the Handshake app.
 * Ties together UI, CameraX, MediaPipe gesture detection, and Nearby Connections.
 * Implements permission handling, lifecycle management, and core logic flow.
 */
public class MainActivity extends AppCompatActivity
        implements GestureAnalyzer.FrameAnalysisListener,
                   NetworkProtocolManager.StatusUpdateListener,
                   NetworkProtocolManager.ConnectionStatusListener,
                   NetworkProtocolManager.TransferProgressListener {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    // UI elements
    private View glassCardContainer;
    private TextView instructionsText;
    private TextView statusText;
    private TextView capsuleText;
    private androidx.camera.view.PreviewView previewView;

    // Managers
    private CameraManager cameraManager;
    private GestureAnalyzer gestureAnalyzer;
    private NetworkProtocolManager networkManager;

    // State
    private boolean permissionsGranted = false;
    private boolean isAdvertising = false;
    private boolean isDiscoveryActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        previewView = findViewById(R.id.previewView);
        glassCardContainer = findViewById(R.id.glass_card_container);
        instructionsText = findViewById(R.id.instructionsText);
        statusText = findViewById(R.id.statusText);
        capsuleText = findViewById(R.id.capsuleText);

        // Initialize managers
        gestureAnalyzer = new GestureAnalyzer();
        networkManager = new NetworkProtocolManager(
                this,
                this, // status listener
                this, // connection listener
                this  // transfer listener
        );
        cameraManager = new CameraManager(
                this,
                previewView,
                this // frame analysis listener
        );

        // Check and request permissions
        checkAndRequestPermissions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If permissions are granted, start camera and advertising/discovery
        if (permissionsGranted) {
            startCamera();
            startAdvertisingAndDiscovery();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Stop camera and advertising/discovery when not visible
        stopCamera();
        stopAdvertisingAndDiscovery();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up resources
        cameraManager.shutdown();
        networkManager.shutdown();
    }

    /**
     * Checks for required permissions and requests them if needed.
     * For Android 12+, we need Bluetooth scan/advertise/connect, location, and camera.
     */
    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        // Location permission (required for Bluetooth scan on Android 12+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // For Android 12 (API 31) and above, we need specific Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            // For Android 11 and below, we need these legacy Bluetooth permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            permissionsGranted = true;
            Log.d(TAG, "All permissions already granted");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                permissionsGranted = true;
                Log.d(TAG, "Permissions granted");
                startCamera();
                startAdvertisingAndDiscovery();
            } else {
                permissionsGranted = false;
                Log.w(TAG, "Some permissions denied");
                Toast.makeText(this, "Permissions required for app functionality", Toast.LENGTH_LONG).show();
                // Optionally, we can disable features that require these permissions
            }
        }
    }

    /**
     * Starts the camera if permissions are granted.
     */
    private void startCamera() {
        if (permissionsGranted) {
            cameraManager.bindCamera(this);
        }
    }

    /**
     * Stops the camera and releases resources.
     */
    private void stopCamera() {
        cameraManager.unbindCamera();
    }

    /**
     * Starts advertising and discovery for Nearby Connections.
     */
    private void startAdvertisingAndDiscovery() {
        if (permissionsGranted) {
            networkManager.startAdvertising();
            networkManager.startDiscovery();
            isAdvertising = true;
            isDiscoveryActive = true;
        }
    }

    /**
     * Stops advertising and discovery.
     */
    private void stopAdvertisingAndDiscovery() {
        networkManager.stopAdvertising();
        networkManager.stopDiscovery();
        isAdvertising = false;
        isDiscoveryActive = false;
    }

    // ======================
    // Gesture Analysis Callback
    // ======================

    @Override
    public void onFrameAnalyzed(@NonNull androidx.camera.core.ImageProxy image) {
        // Convert ImageProxy to MediaPipe format and analyze gesture
        // For simplicity, we assume we have a helper to convert ImageProxy to NormalizedLandmark list.
        // In a real app, you would use MediaPipe HandLandmarker to get landmarks from the image.
        // Here, we simulate by calling a method that would be implemented with MediaPipe.
        // Since we are focusing on the structure, we'll leave the actual MediaPipe integration
        // as a placeholder and call our analyzer with a dummy list (which will return IDLE).

        // TODO: Implement actual MediaPipe HandLandmarker processing here
        // For now, we'll just close the image and return (no gesture detected in this stub)
        image.close();

        // In a real implementation, you would:
        // 1. Use MediaPipe HandLandmarker to detect hand landmarks from the image
        // 2. Pass the landmarks to gestureAnalyzer.analyze()
        // 3. Handle the gesture state changes

        // For the purpose of this code generation, we'll skip the actual MediaPipe integration
        // and assume the gestureAnalyzer is being fed data elsewhere (e.g., via a separate thread).
        // However, to demonstrate the flow, we'll add a simple periodic check for demonstration.
        // NOTE: This is not the actual gesture detection logic - just a placeholder to show where it would go.

        // We'll instead rely on a separate mechanism (like a timer) to simulate gesture events
        // for the sake of completing the example. In reality, you would integrate MediaPipe here.
    }

    // ======================
    // Network Status Listener
    // ======================

    @Override
    public void onStatusUpdate(String status) {
        runOnUiThread(() -> {
            statusText.setText(status);
            Log.d(TAG, "Status: " + status);
        });
    }

    @Override
    public void onConnected(String endpointName) {
        runOnUiThread(() -> {
            capsuleText.setText("Connected to: " + endpointName);
            // Update UI to indicate ready to send
            instructionsText.setText("Release gesture to send file");
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            capsuleText.setText("Disconnected");
            instructionsText.setText("Pinch fingers together to grab, release to send");
        });
    }

    @Override
    public void onConnectionFailed(int statusCode) {
        runOnUiThread(() -> {
            capsuleText.setText("Connection failed");
            Toast.makeText(this, "Connection failed: " + statusCode, Toast.LENGTH_SHORT).show();
        });
    }

    // ======================
    // Transfer Progress Listener
    // ======================

    @Override
    public void onTransferStarted(String fileName, long totalBytes) {
        runOnUiThread(() -> {
            statusText.setText("Sending: " + fileName);
        });
    }

    @Override
    public void onTransferProgress(String fileName, long bytesTransferred, long totalBytes) {
        int progress = (int) ((bytesTransferred * 100) / totalBytes);
        runOnUiThread(() -> {
            statusText.setText("Sending: " + fileName + " (" + progress + "%)");
        });
    }

    @Override
    public void onTransferCompleted(String fileName) {
        runOnUiThread(() -> {
            statusText.setText("Sent: " + fileName);
            Toast.makeText(this, "File sent successfully!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onTransferFailed(String fileName, int errorCode) {
        runOnUiThread(() -> {
            statusText.setText("Failed to send: " + fileName);
            Toast.makeText(this, "Transfer failed: " + errorCode, Toast.LENGTH_SHORT).show();
        });
    }

    // ======================
    // Gesture Handling Logic (Simplified for demonstration)
    // ======================

    /**
     * This method would be called from your MediaPipe processing pipeline
     * when a gesture is detected. For this example, we'll simulate it with a timer.
     * In a real app, you would integrate MediaPipe HandLandmarker and call
     * gestureAnalyzer.analyze(landmarks) on each frame, then act on the result.
     */
    private void simulateGestureDetection() {
        // This is just for demonstration - remove when integrating real MediaPipe
        new Thread(() -> {
            try {
                while (!Thread.interrupted()) {
                    Thread.sleep(2000); // Simulate gesture every 2 seconds for demo
                    runOnUiThread(() -> {
                        // Simulate a grab gesture
                        handleGestureState(GestureAnalyzer.GestureState.GRAB);
                        try {
                            Thread.sleep(1000); // Hold grab for 1 second
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        // Simulate a release gesture
                        handleGestureState(GestureAnalyzer.GestureState.RELEASE);
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Handles the gesture state changes and triggers appropriate actions.
     *
     * @param state The detected gesture state
     */
    private void handleGestureState(GestureAnalyzer.GestureState state) {
        switch (state) {
            case GRAB:
                // Pinch detected - start advertising/discovery if not already
                if (!isAdvertising || !isDiscoveryActive) {
                    startAdvertisingAndDiscovery();
                }
                runOnUiThread(() -> {
                    instructionsText.setText("Grab detected - preparing to send");
                    capsuleText.setText("Advertising...");
                });
                break;
            case RELEASE:
                // Pinch released - attempt to connect and send a file
                runOnUiThread(() -> {
                    instructionsText.setText("Release detected - connecting...");
                    capsuleText.setText("Finding best device...");
                });
                // In a real app, you would pick a file to send here.
                // For demo, we'll just attempt to connect to the best endpoint.
                networkManager.connectToBestEndpoint();
                // Note: Actual file sending would happen after connection is established
                // and would be triggered by a separate mechanism (e.g., after connection callback).
                break;
            case IDLE:
            default:
                // No gesture - do nothing
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start simulating gestures for demonstration (remove when integrating MediaPipe)
        if (permissionsGranted) {
            simulateGestureDetection();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop gesture simulation
        // In a real app, you would stop the MediaPipe processor here
    }
}