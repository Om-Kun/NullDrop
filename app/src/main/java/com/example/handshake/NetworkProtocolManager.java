package com.example.handshake;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.google.android.gms.nearby.ConnectionInfo;
import com.google.android.gms.nearby.Connections;
import com.google.android.gms.nearby.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.Payload;
import com.google.android.gms.nearby.PayloadCallback;
import com.google.android.gms.nearby.PayloadTransferUpdate;
import com.google.android.gms.nearby.Strategy;
import com.google.android.gms.nearby.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.ConnectionsStatusCodes;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the Nearby Connections protocol for P2P file transfers.
 * Handles advertising, discovery, connection establishment, and data transfer.
 * Uses ExecutorService for background work and Handler for main thread updates.
 */
public class NetworkProtocolManager {
    private static final String TAG = "NetworkProtocol";

    // Nearby Connections constants
    private static final String SERVICE_ID = "com.example.handshake.p2p";
    private static final Strategy STRATEGY = Strategy.P2P_STAR; // Star topology for file sharing
    private static final int AUTO_CONNECT_TIMEOUT_MS = 15000; // 15 seconds

    // UI update constants
    private static final long RSSI_UPDATE_INTERVAL_MS = 1000; // Update RSSI every second

    // Context and handlers
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // State tracking
    private boolean isAdvertising = false;
    private boolean isDiscovering = false;
    private String myEndpointName;
    private final Map<String, DiscoveredEndpointInfo> discoveredEndpoints = Collections.synchronizedMap(new HashMap<>());
    private String connectedEndpointId = null;
    private String selectedPayloadName = null;

    // Callbacks for UI updates
    private final StatusUpdateListener statusListener;
    private final ConnectionStatusListener connectionListener;
    private final TransferProgressListener transferListener;

    /**
     * Listener for status updates to be shown in UI.
     */
    public interface StatusUpdateListener {
        void onStatusUpdate(String status);
    }

    /**
     * Listener for connection status changes.
     */
    public interface ConnectionStatusListener {
        void onConnected(String endpointName);
        void onDisconnected();
        void onConnectionFailed(int statusCode);
    }

    /**
     * Listener for file transfer progress.
     */
    public interface TransferProgressListener {
        void onTransferStarted(String fileName, long totalBytes);
        void onTransferProgress(String fileName, long bytesTransferred, long totalBytes);
        void onTransferCompleted(String fileName);
        void onTransferFailed(String fileName, int errorCode);
    }

    /**
     * Constructor.
     *
     * @param context Application context
     * @param statusListener Listener for status updates
     * @param connectionListener Listener for connection status
     * @param transferListener Listener for transfer progress
     */
    public NetworkProtocolManager(@NonNull Context context,
                                  @NonNull StatusUpdateListener statusListener,
                                  @NonNull ConnectionStatusListener connectionListener,
                                  @NonNull TransferProgressListener transferListener) {
        this.context = context.getApplicationContext();
        this.statusListener = statusListener;
        this.connectionListener = connectionListener;
        this.transferListener = transferListener;
        this.myEndpointName = "Handshake_" + System.currentTimeMillis();
    }

    /**
     * Starts advertising our device for discovery.
     */
    public void startAdvertising() {
        if (isAdvertising) {
            return;
        }

        executorService.execute(() -> {
            try {
                Connections.getStatusCodes(context).addOnSuccessListener(statusCode -> {
                    if (statusCode == ConnectionsStatusCodes.STATUS_OK) {
                        Connections.get(context).startAdvertising(
                                myEndpointName,
                                SERVICE_ID,
                                connectionLifecycleCallback,
                                STRATEGY)
                                .addOnSuccessListener(() -> {
                                    isAdvertising = true;
                                    postStatusUpdate("Advertising as: " + myEndpointName);
                                    Log.d(TAG, "Started advertising successfully");
                                })
                                .addOnFailureListener(e -> {
                                    postStatusUpdate("Failed to start advertising: " + e.getMessage());
                                    Log.e(TAG, "Failed to start advertising", e);
                                });
                    } else {
                        postStatusUpdate("Nearby Connections not available: " + statusCode);
                        Log.e(TAG, "Nearby Connections not available: " + statusCode);
                    }
                });
            } catch (SecurityException e) {
                postStatusUpdate("Missing Nearby Connections permissions");
                Log.e(TAG, "Missing permissions for Nearby Connections", e);
            }
        });
    }

    /**
     * Stops advertising our device.
     */
    public void stopAdvertising() {
        if (!isAdvertising) {
            return;
        }

        executorService.execute(() -> {
            try {
                Connections.get(context).stopAdvertising()
                        .addOnSuccessListener(() -> {
                            isAdvertising = false;
                            postStatusUpdate("Stopped advertising");
                            Log.d(TAG, "Stopped advertising");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to stop advertising", e);
                        });
            } catch (SecurityException e) {
                Log.e(TAG, "Missing permissions to stop advertising", e);
            }
        });
    }

    /**
     * Starts discovering nearby devices.
     */
    public void startDiscovery() {
        if (isDiscovering) {
            return;
        }

        executorService.execute(() -> {
            try {
                Connections.get(context).startDiscovery(
                        SERVICE_ID,
                        endpointDiscoveryCallback,
                        STRATEGY)
                        .addOnSuccessListener(() -> {
                            isDiscovering = true;
                            postStatusUpdate("Discovering nearby devices...");
                            Log.d(TAG, "Started discovery");
                        })
                        .addOnFailureListener(e -> {
                            postStatusUpdate("Failed to start discovery: " + e.getMessage());
                            Log.e(TAG, "Failed to start discovery", e);
                        });
            } catch (SecurityException e) {
                postStatusUpdate("Missing Nearby Connections permissions");
                Log.e(TAG, "Missing permissions for Nearby Connections", e);
            }
        });
    }

    /**
     * Stops discovering nearby devices.
     */
    public void stopDiscovery() {
        if (!isDiscovering) {
            return;
        }

        executorService.execute(() -> {
            try {
                Connections.get(context).stopDiscovery()
                        .addOnSuccessListener(() -> {
                            isDiscovering = false;
                            discoveredEndpoints.clear();
                            postStatusUpdate("Stopped discovery");
                            Log.d(TAG, "Stopped discovery");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to stop discovery", e);
                        });
            } catch (SecurityException e) {
                Log.e(TAG, "Missing permissions to stop discovery", e);
            }
        });
    }

    /**
     * Requests connection to the endpoint with the best RSSI.
     */
    public void connectToBestEndpoint() {
        if (connectedEndpointId != null) {
            postStatusUpdate("Already connected");
            return;
        }

        if (discoveredEndpoints.isEmpty()) {
            postStatusUpdate("No devices discovered yet");
            return;
        }

        // Find endpoint with best RSSI (highest value)
        String bestEndpointId = null;
        int bestRSSI = Integer.MIN_VALUE;

        synchronized (discoveredEndpoints) {
            for (Map.Entry<String, DiscoveredEndpointInfo> entry : discoveredEndpoints.entrySet()) {
                DiscoveredEndpointInfo info = entry.getValue();
                if (info.getRssi() > bestRSSI) {
                    bestRSSI = info.getRssi();
                    bestEndpointId = entry.getKey();
                }
            }
        }

        if (bestEndpointId != null) {
            requestConnection(bestEndpointId);
        } else {
            postStatusUpdate("No suitable device found");
        }
    }

    /**
     * Requests connection to a specific endpoint.
     *
     * @param endpointId The endpoint ID to connect to
     */
    private void requestConnection(@NonNull String endpointId) {
        executorService.execute(() -> {
            try {
                Connections.get(context).requestConnection(
                        myEndpointName,
                        endpointId,
                        connectionLifecycleCallback)
                        .addOnSuccessListener(() -> {
                            postStatusUpdate("Requesting connection...");
                            Log.d(TAG, "Connection request sent to: " + endpointId);
                        })
                        .addOnFailureListener(e -> {
                            postStatusUpdate("Failed to request connection: " + e.getMessage());
                            Log.e(TAG, "Failed to request connection", e);
                        });
            } catch (SecurityException e) {
                postStatusUpdate("Missing Nearby Connections permissions");
                Log.e(TAG, "Missing permissions for Nearby Connections", e);
            }
        });
    }

    /**
     * Sends a file to the connected endpoint.
     *
     * @param filePath Path to the file to send
     */
    public void sendFile(@NonNull String filePath) {
        if (connectedEndpointId == null) {
            postStatusUpdate("Not connected to any device");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            postStatusUpdate("File not found: " + file.getName());
            return;
        }

        selectedPayloadName = file.getName();
        executorService.execute(() -> {
            try {
                Payload payload = Payload.fromFile(file);
                Connections.get(context).sendPayload(connectedEndpointId, payload)
                        .addOnSuccessListener(payloadId -> {
                            postStatusUpdate("Sending: " + file.getName());
                            Log.d(TAG, "Started sending file: " + file.getName());
                        })
                        .addOnFailureListener(e -> {
                            postStatusUpdate("Failed to send file: " + e.getMessage());
                            transferListener.onTransferFailed(file.getName(), -1);
                            Log.e(TAG, "Failed to send file", e);
                        });
            } catch (SecurityException e) {
                postStatusUpdate("Missing Nearby Connections permissions");
                Log.e(TAG, "Missing permissions for Nearby Connections", e);
            }
        });
    }

    /**
     * Disconnects from the current endpoint.
     */
    public void disconnect() {
        if (connectedEndpointId == null) {
            return;
        }

        executorService.execute(() -> {
            try {
                Connections.get(context).disconnectFromEndpoint(connectedEndpointId)
                        .addOnSuccessListener(() -> {
                            postStatusUpdate("Disconnected");
                            connectedEndpointId = null;
                            connectionListener.onDisconnected();
                            Log.d(TAG, "Disconnected from endpoint");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to disconnect", e);
                        });
            } catch (SecurityException e) {
                Log.e(TAG, "Missing permissions to disconnect", e);
            }
        });
    }

    /**
     * Returns the name of our endpoint.
     */
    public String getMyEndpointName() {
        return myEndpointName;
    }

    /**
     * Returns true if currently connected to another device.
     */
    public boolean isConnected() {
        return connectedEndpointId != null;
    }

    /**
     * Returns the number of discovered devices.
     */
    public int getDiscoveredDeviceCount() {
        synchronized (discoveredEndpoints) {
            return discoveredEndpoints.size();
        }
    }

    /**
     * Returns list of discovered device names.
     */
    public List<String> getDiscoveredDeviceNames() {
        synchronized (discoveredEndpoints) {
            List<String> names = new ArrayList<>();
            for (DiscoveredEndpointInfo info : discoveredEndpoints.values()) {
                names.add(info.getEndpointName());
            }
            return names;
        }
    }

    /**
     * Posts a status update to the main thread.
     */
    private void postStatusUpdate(final String status) {
        mainHandler.post(() -> statusListener.onStatusUpdate(status));
    }

    // Nearby Connections Callbacks

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId,
                                          @NonNull ConnectionInfo connectionInfo) {
            // Auto-accept the connection
            Connections.get(context).acceptConnection(endpointId, payloadCallback);
            Log.d(TAG, "Connection initiated with: " + endpointId);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId,
                                       @NonNull ConnectionResult connectionResult) {
            int status = connectionResult.getStatus().getStatusCode();
            if (status == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpointId = endpointId;
                postStatusUpdate("Connected to: " + connectionResult.getEndpointName());
                connectionListener.onConnected(connectionResult.getEndpointName());
                Log.d(TAG, "Connection successful: " + endpointId);
            } else {
                postStatusUpdate("Connection failed: " + status);
                connectionListener.onConnectionFailed(status);
                Log.e(TAG, "Connection failed: " + endpointId + ", status: " + status);
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            if (connectedEndpointId != null && connectedEndpointId.equals(endpointId)) {
                postStatusUpdate("Disconnected");
                connectedEndpointId = null;
                connectionListener.onDisconnected();
                Log.d(TAG, "Disconnected: " + endpointId);
            }
        }
    };

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId,
                                    @NonNull DiscoveredEndpointInfo discoveredEndpointInfo) {
            // Ignore our own endpoint
            if (!discoveredEndpointInfo.getEndpointName().equals(myEndpointName)) {
                discoveredEndpoints.put(endpointId, discoveredEndpointInfo);
                Log.d(TAG, "Endpoint found: " + discoveredEndpointInfo.getEndpointName() +
                        ", RSSI: " + discoveredEndpointInfo.getRssi());
            }
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            DiscoveredEndpointInfo info = discoveredEndpoints.remove(endpointId);
            if (info != null) {
                Log.d(TAG, "Endpoint lost: " + info.getEndpointName());
            }
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId,
                                      @NonNull Payload payload) {
            // We're primarily sending files, not receiving in this basic implementation
            // But we could handle incoming files here if needed
            Log.d(TAG, "Payload received: " + payload.getType());
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId,
                                            @NonNull PayloadTransferUpdate update) {
            // Handle transfer progress for sent files
            if (selectedPayloadName != null && update.getPayload().getName().equals(selectedPayloadName)) {
                long bytesTransferred = update.getBytesTransferred();
                long totalBytes = update.getTotalBytes();
                int status = update.getStatus();

                switch (status) {
                    case PayloadTransferUpdate.Status.SUCCESS:
                        postStatusUpdate("Transfer completed: " + selectedPayloadName);
                        transferListener.onTransferCompleted(selectedPayloadName);
                        selectedPayloadName = null;
                        break;
                    case PayloadTransferUpdate.Status.FAILURE:
                        postStatusUpdate("Transfer failed: " + selectedPayloadName);
                        transferListener.onTransferFailed(selectedPayloadName, update.getErrorCode());
                        selectedPayloadName = null;
                        break;
                    case PayloadTransferUpdate.Status.CANCELLED:
                        postStatusUpdate("Transfer cancelled: " + selectedPayloadName);
                        selectedPayloadName = null;
                        break;
                    default:
                        // IN_PROGRESS or UNKNOWN
                        if (totalBytes > 0) {
                            transferListener.onTransferProgress(selectedPayloadName, bytesTransferred, totalBytes);
                        }
                        break;
                }
            }
        }
    };

    /**
     * Shuts down the manager and releases resources.
     * Call this from onDestroy() of the activity.
     */
    public void shutdown() {
        stopAdvertising();
        stopDiscovery();
        disconnect();
        executorService.shutdown();
    }
}