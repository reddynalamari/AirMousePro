<div align="center">

# 🖱️ AirMouse Pro

### Turn your Android phone into a Bluetooth mouse, trackpad, keyboard and remote.
**No receiver. No desktop app. No drivers.** Your phone *is* the device.

[![Latest release](https://img.shields.io/github/v/release/USER/AirMousePro?style=for-the-badge&color=6C63FF&label=release)](https://github.com/USER/AirMousePro/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/USER/AirMousePro/total?style=for-the-badge&color=00C896)](https://github.com/USER/AirMousePro/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/USER/AirMousePro/release.yml?style=for-the-badge&label=build)](https://github.com/USER/AirMousePro/actions)
![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)

[**⬇ Download APK**](https://github.com/USER/AirMousePro/releases/latest) · [Features](#-features) · [Quick start](#-quick-start) · [Build](#-build-from-source) · [Releases](#-automatic-releases)

</div>

---

## ✨ Features

| | |
|---|---|
| 🎯 **Air mouse** | Hold a button and point the phone like a laser pointer. Gyroscope-driven at ~125 Hz with dead-zone tremor filtering and an adjustable sensitivity slider. |
| 🖐️ **Laptop-style trackpad** | One-finger move, tap to click, tap-and-drag, two-finger scroll in both axes, pinch to zoom, two-finger right-click, three-finger swipes for Task View / desktop / app switch. |
| ⌨️ **Full keyboard** | Type on your phone and it appears on the connected device, including modifier combos. |
| 📺 **Remote control** | D-pad, OK, media and volume keys, tuned for Android TV and Smart TVs. |
| ⚡ **Shortcuts & macros** | Build your own multi-step macros (key combos, text, media keys, clicks, waits). Ships with Copy, Paste, Desktop, Lock, Snip and Mic-mute. Templates for "open website" / "open app" on every OS. |
| 🤝 **Gestures** | Shake twice to toggle the air mouse, twist your wrist for Alt+←/→ back and forward. |
| 🔋 **Background service** | Foreground service keeps the connection alive when the screen is off. |
| 🩺 **Built-in diagnostics** | Live HID log you can copy or share, plus one-tap *Re-register HID* when a host misbehaves. |

> AirMouse Pro registers itself as a standard **Bluetooth HID device** (the same profile a hardware mouse or keyboard uses), so anything that accepts a Bluetooth mouse works with it: Windows, macOS, Linux, ChromeOS, iPadOS, Android TV and more.

## 📸 Screenshots

<div align="center">

| Connect | Trackpad | Air mouse | Remote |
|:--:|:--:|:--:|:--:|
| <img src="docs/screenshots/connect.png" width="180"> | <img src="docs/screenshots/trackpad.png" width="180"> | <img src="docs/screenshots/air.png" width="180"> | <img src="docs/screenshots/remote.png" width="180"> |

</div>

<sub>Drop PNGs with these names into `docs/screenshots/` and they appear here.</sub>

## 🚀 Quick start

1. **Install** the latest APK from the [Releases page](https://github.com/USER/AirMousePro/releases/latest) (allow *Install unknown apps* when asked).
2. Open the app and grant the **Bluetooth** and **Notification** permissions.
3. Tap **Make discoverable**.
4. On your laptop / TV, open Bluetooth settings and **add the phone**. Accept the pairing code on both.
5. Back in the app, tap your device under **Paired devices → Connect**. Done.

### Requirements

- Android **9 (API 28)** or newer
- A phone whose Bluetooth stack supports the **HID Device profile** (most do; if registration fails, use *Settings → Re-register HID*)
- Gyroscope for the air mouse (optional, trackpad/keyboard/remote work without it)

## 🧱 How it works

```
 Phone sensors / touch ──► AirMouseEngine / TrackpadGestures ──► HidController ──► Bluetooth HID ──► Host
        (gyro, shake)            (gesture + motion maths)        (reports @ ≤140 Hz)
```

- `hid/`: HID report descriptor, key map and Bluetooth HID Device controller
- `input/`: gyroscope air-mouse engine and multi-touch trackpad gestures
- `data/`: settings and the macro/shortcut store
- `ui/`: Jetpack Compose screens (Connect, Mouse, Touchpad, Keyboard, Remote, Settings)
- `service/`: foreground service that keeps HID alive

## 🛠 Build from source

Requires JDK 17 and Android SDK 34.

```bash
gradle :app:assembleDebug      # debug APK
gradle :app:assembleRelease    # minified release APK
```

Output lands in `app/build/outputs/apk/`. Or just open the folder in **Android Studio** and press Run.

## 📦 Automatic releases

Every push to `main` that changes app code is built and published as a new GitHub Release, with no manual steps.

| Trigger | Result |
|---|---|
| Push to `main` | New patch release: `v0.0.1` → `v0.0.2` → … with the APK and `SHA256SUMS.txt` attached and auto-generated release notes |
| Actions → **Release** → *Run workflow* | Pick `minor` or `major` to bump that part instead |
| Pull request / other branches | CI builds a debug APK (uploaded as a workflow artifact) |
| README / `docs/` only changes | Ignored, no release |

The release workflow passes the tag as `versionName` and the run number as `versionCode`, so each APK is a valid upgrade of the last.

### One-time setup: signing key (recommended)

Android only installs an update over an existing app if both APKs are signed with the **same key**. Without a key, CI falls back to a throwaway debug key and each release would need an uninstall first.

```bash
keytool -genkeypair -v -keystore airmouse.jks -alias airmouse -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 airmouse.jks    # copy the output
```

Add these under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the base64 output above |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `airmouse` |
| `KEY_PASSWORD` | key password |

Keep `airmouse.jks` safe and **never commit it** (it is already in `.gitignore`).

## 🔐 Permissions

| Permission | Why |
|---|---|
| `BLUETOOTH_CONNECT` / `BLUETOOTH_ADVERTISE` | Pair with and appear as a Bluetooth HID device |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Keep the connection alive in the background |
| `POST_NOTIFICATIONS` | Show the connection notification |

No internet permission. Nothing leaves your phone except Bluetooth HID reports to the device you chose.

## 🤝 Contributing

Issues and pull requests are welcome. Please make sure `gradle :app:assembleDebug` passes; CI runs it on every PR.

## 👤 Author

Built by **Shashidhar Reddy**.

---

<div align="center"><sub>If AirMouse Pro saved you a walk to the laptop, drop a ⭐</sub></div>
