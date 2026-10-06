<h1 align="center">
  NullDrop 🚀
</h1>

<p align="center">
  <b>Futuristic, touchless peer-to-peer file transfer for Android.</b><br>
  <i>AirDrop speeds, spatial UI, and gesture controls. No internet required.</i>
</p>

---

## ✨ Overview

**NullDrop** is a revolutionary Android application that completely reimagines how we share files locally. Instead of dealing with pairing codes, QR scanners, or complex menus, NullDrop uses on-device Machine Learning to allow you to "grab" and "throw" files to nearby devices using just your hand gestures.

By combining the **Google MediaPipe Hand Landmarker** with the **Google Nearby Connections API**, NullDrop achieves zero-latency discovery and high-bandwidth Wi-Fi Direct transfers—all wrapped in a stunning, GPU-accelerated Spatial UI.

## 🌟 Key Features

* 🖐️ **Touchless Gesture Control:** Make a fist (✊) to initiate a connection, and open your palm (🖐️) to beam the file.
* ⚡ **Ultra-Fast P2P Transfers:** Uses Wi-Fi Direct and BLE to transfer files at up to 50 Mbps. 100% offline.
* 🧊 **Spatial "Glassmorphism" UI:** Features Apple-style frosted glass overlays that dynamically de-blur as files transfer, powered by Android 12+ `RenderEffect`.
* 🔋 **Zero-Latency Discovery:** Devices advertise and discover silently in the background, making connections feel instantaneous.
* 📂 **Bulletproof File I/O:** Bypasses Android Scoped Storage limitations using highly reliable byte-stream copying directly to your public Gallery.

## 🛠️ Tech Stack

* **Language:** 100% Pure Java
* **Architecture:** MVC (Model-View-Controller)
* **Machine Learning:** Google MediaPipe Tasks Vision (Edge AI)
* **Camera System:** Android Jetpack CameraX
* **Networking:** Google Play Services Nearby Connections API (`P2P_POINT_TO_POINT`)
* **Graphics:** Android Core Graphics (`RenderEffect`, `ObjectAnimator`, Custom Canvas drawing)

## 🧠 How It Works (Under the Hood)

### 1. The Math (Gesture Recognition)
NullDrop doesn't use heavy physics engines. Instead, CameraX feeds frames to MediaPipe, which extracts 21 3D coordinates from your hand. We calculate the **Euclidean Distance** (`sqrt(dx² + dy²)`) between your wrist and your four fingertips. 
* Fingers close to the wrist = **GRAB**
* Fingers far from the wrist = **RELEASE**

*(A 200ms debounce algorithm prevents UI flickering from ML false-positives).*

### 2. The Networking (The Payload Trick)
To make the UI feel instant while a massive file transfers, NullDrop extracts a tiny, highly compressed thumbnail of your image. It packs this into a `BYTES` payload and sends it *before* the actual `FILE` payload. 

The receiver's phone gets the thumbnail in milliseconds, applies a heavy GPU blur to it, and displays the frosted glass UI. As the massive background file downloads, the blur radius mathematically decreases until the image is crystal clear.

## 🚀 Getting Started

### Prerequisites
* Android Studio (Giraffe or newer)
* Minimum SDK: Android 8.0 (API 26)
* Target SDK: Android 14 (API 34)
* A physical Android device (Nearby Connections P2P testing requires physical Wi-Fi/Bluetooth hardware; it will not work properly between two emulators).

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/NullDrop.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle files.
4. Build and run on a physical Android device.

## 🔐 Permissions Note
NullDrop requests granular Bluetooth and Wi-Fi permissions (Android 12/13+). It utilizes the `usesPermissionFlags="neverForLocation"` flag on Bluetooth to ensure maximum privacy, bypassing the OS location-tracking prompt since Bluetooth is strictly used for data transfer.

## 👨‍💻 Author
**Om Salunke**

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
