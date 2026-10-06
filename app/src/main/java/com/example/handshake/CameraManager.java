package com.example.handshake;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.Instant;

/**
 * Manages CameraX lifecycle and provides frame analysis at a throttled rate (15fps).
 * Handles camera permission checks, binding/unbinding, and frame analysis.
 * Uses ExecutorService for background work and ensures main thread is not blocked.
 */
public class CameraManager {
    private static final String TAG = "CameraManager";

    // Target frame analysis rate: 15 fps -> ~66.67ms per frame
    private static final long MIN_FRAME_INTERVAL_MS = 66L;
    private static final long MAX_FRAME_INTERVAL_MS = 67L; // Allow small variance

    // Camera and use cases
    private ProcessCameraProvider cameraProvider;
    private Preview previewUseCase;
    private ImageAnalysis analysisUseCase;
    private final PreviewView previewView;

    // Analysis throttling
    private Instant lastAnalysisTime;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();

    // Callback for analyzed frames (to be implemented by MainActivity)
    public interface FrameAnalysisListener {
        void onFrameAnalyzed(@NonNull ImageProxy image);
    }

    private final FrameAnalysisListener frameAnalysisListener;
    private final Context context;

    /**
     * Constructor.
     *
     * @param context Application context
     * @param previewView PreviewView to display camera feed
     * @param frameAnalysisListener Listener to receive frames for analysis
     */
    public CameraManager(@NonNull Context context,
                         @NonNull PreviewView previewView,
                         @NonNull FrameAnalysisListener frameAnalysisListener) {
        this.context = context.getApplicationContext();
        this.previewView = previewView;
        this.frameAnalysisListener = frameAnalysisListener;
        this.lastAnalysisTime = Instant.now();
    }

    /**
     * Checks if camera permission is granted.
     *
     * @return true if permission granted, false otherwise
     */
    public boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Requests camera permission. Should be called from an Activity.
     * Note: This method assumes the caller will handle the permission result.
     * For simplicity, we just check and log here; the Activity should manage the request.
     */
    public void requestCameraPermission() {
        // Permission requesting should be handled by the Activity.
        // We just log if permission is missing.
        if (!hasCameraPermission()) {
            Log.w(TAG, "Camera permission not granted. Request it in Activity.");
        }
    }

    /**
     * Binds camera use cases to the lifecycle owner.
     * Should be called after ensuring camera permission is granted.
     *
     * @param lifecycleOwner LifecycleOwner (typically the Activity)
     */
    public void bindCamera(@NonNull LifecycleOwner lifecycleOwner) {
        if (!hasCameraPermission()) {
            Log.e(TAG, "Cannot bind camera: permission not granted");
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindAllUseCases(cameraProvider);
                Log.d(TAG, "Camera bound successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to bind camera use cases", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    /**
     * Unbinds all camera use cases and releases the camera.
     */
    public void unbindCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
            Log.d(TAG, "Camera unbound");
        }
    }

    /**
     * Shuts down the executor service. Call this from onDestroy().
     */
    public void shutdown() {
        unbindCamera();
        analysisExecutor.shutdown();
    }

    /**
     * Internal method to bind preview and analysis use cases.
     */
    private void bindAllUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        // Preview use case
        previewUseCase = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(Surface.ROTATION_0)
                .build();
        previewUseCase.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image analysis use case
        analysisUseCase = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        // Set analyzer with throttling
        analysisUseCase.setAnalyzer(analysisExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                // Throttle to 15fps: only process if enough time has passed
                Instant now = Instant.now();
                long elapsedMillis = java.time.Duration.between(lastAnalysisTime, now).toMillis();
                if (elapsedMillis >= MIN_FRAME_INTERVAL_MS) {
                    lastAnalysisTime = now;
                    // Pass the image to the listener (do not close here; listener must close)
                    frameAnalysisListener.onFrameAnalyzed(image);
                } else {
                    // Drop the frame to maintain rate
                    image.close();
                }
            }
        });

        // Bind to lifecycle
        cameraProvider.bindToLifecycle(
                ((androidx.lifecycle.LifecycleOwner) context),
                CameraSelector.DEFAULT_BACK_CAMERA,
                previewUseCase,
                analysisUseCase);
    }
}