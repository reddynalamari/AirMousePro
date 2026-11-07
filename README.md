<div align="center">

# AirMouse Pro

### Use your Android phone as a Bluetooth mouse, trackpad, keyboard, gamepad and remote.
Nothing to install on the computer or TV: the phone pairs as an ordinary Bluetooth device.

[![Latest release](https://img.shields.io/github/v/release/reddynalamari/AirMousePro?style=for-the-badge&color=6C63FF&label=release)](https://github.com/reddynalamari/AirMousePro/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/reddynalamari/AirMousePro/total?style=for-the-badge&color=00C896)](https://github.com/reddynalamari/AirMousePro/releases)
![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
[![License](https://img.shields.io/github/license/reddynalamari/AirMousePro?style=for-the-badge)](LICENSE)

[**Download**](https://github.com/reddynalamari/AirMousePro/releases/latest) · [Features](#features) · [Quick start](#quick-start) · [Privacy and security](#privacy-and-security) · [Updates](#updates) · [Help](#troubleshooting)

</div>

---

## What it is

AirMouse Pro makes your phone appear as a standard **Bluetooth mouse and keyboard**. Any computer or TV that accepts one can be controlled, with no receiver, driver or companion app on the other side.

It is developed and tested on **Windows 11**. macOS, Linux, ChromeOS and Android TV should work because they accept Bluetooth input devices too, but they have not been tested yet.

## Features

| Area | What you get |
|---|---|
| **Devices** | Remembers the devices you have connected to and reconnects to the most recent one that is available whenever the app opens. |
| **Trackpad** | Move, tap to click, tap-and-drag, two-finger scroll in both directions, two-finger tap to right-click, three-finger tap to middle-click, pinch to zoom, and three-finger swipes for task view, desktop and app switching. |
| **Air mouse** | The cursor follows where the phone points. Tap anywhere to click, two fingers to right-click, press and hold to drag, swipe to scroll, or use the volume keys as buttons. The pointer holds still while you tap, so clicks land where you aimed. |
| **Keyboard** | On-screen keyboard with sticky Ctrl, Alt, Win and Shift. Layouts for US, UK, German, French and Spanish. Voice typing, typing your clipboard, and sharing text from any app to type it on the computer. |
| **Deck** | Your own shortcuts and macros, with ready-made ones for copy, paste, lock, screenshot and more. Can open a website or an app on the computer. |
| **Present** | A slide clicker with a timer, black and white screen, and a laser that follows the phone. |
| **Gamepad** | A Bluetooth gamepad with two sticks, D-pad, buttons, triggers and optional gyro aiming. A **Keys** switch turns the same controls into keyboard and mouse input (WASD, mouse look, trigger clicks) for games that ignore generic gamepads. |
| **Remote** | D-pad, media keys, volume and brightness. |
| **Quick menu** | A floating bubble that appears over your other apps while this app is in the background. Tap it for a rotary dial with your favourite actions, including volume and brightness knobs. Double-tap to open the app, drag it to the X to remove it. |
| **Guides** | A short animated setup guide on first launch, and after every update a tour that covers only what was added, improved or removed. Both can be replayed from Settings. |
| **Make it yours** | Dark, AMOLED, light or system theme, five accent colours, adjustable haptics, rotation that follows your phone's auto-rotate switch, and landscape layouts. |
| **Updates and backup** | Finds new versions, shows older ones, and backs up your settings and shortcuts to a file. |

## Screens

| Screen | What it is for |
|---|---|
| **Devices** | Connection status, recent and paired devices, and pairing help. |
| **Mouse** | Switch between the **Touchpad** and the **Air mouse**. |
| **Keys** | On-screen keyboard, text mode, voice typing, shortcut and function keys. |
| **Deck** | Switch between **Shortcuts**, **Present** and **Gamepad**. |
| **Remote** | D-pad, media, volume and brightness. |
| **Settings** | The gear icon at the top. Appearance, touchpad, air mouse, keyboard, connection, quick menu, guides, updates, backup and about. |

## Quick start

1. Download the APK from the [latest release](https://github.com/reddynalamari/AirMousePro/releases/latest) and open it. Android asks you to allow installing from your browser or file manager the first time.
2. Open the app and follow the short setup guide. It asks for the permissions it needs, once, and you can skip any step.
3. Tap **Make discoverable** on the Devices page.
4. On the computer or TV, open Bluetooth settings and add the phone. Accept the pairing code on both.
5. Tap the device in the app. From then on the app connects by itself when it opens.

**You need** Android 9 or newer and a phone whose Bluetooth supports the HID Device profile. Most do, but some manufacturers turn it off. The air mouse also needs a gyroscope.

## Known limits

- **No screen sharing.** Bluetooth input carries controls only, so the phone cannot show the computer's screen.
- **The gamepad is a generic one.** Games that only support Xbox controllers will not see it. Use the **Keys** switch on the gamepad page, or a wrapper such as Steam Input.
- **Rotation follows your phone.** With auto-rotate off, the app stays in portrait. The gamepad needs landscape, so turn the phone sideways or tap **Rotate to landscape** on that page.
- **Re-pair after some updates.** Windows remembers what a Bluetooth device can do. When a release changes that, its notes say so: remove the phone in Windows and pair again.
- **Some symbols may differ on non-US keyboard layouts.** Letters and digits are reliable.
- **Opening apps on Android TV is best effort.** Bluetooth cannot launch an app directly, so it searches for the name and opens the result.
- **iPhone is not supported.** iOS does not let apps act as a Bluetooth input device.

## Privacy and security

AirMouse Pro can type and click on the computer it is connected to, so it is built to do as little as possible beyond that.

**Your data**
- No accounts, no ads, no analytics and no tracking.
- Settings, shortcuts and recent devices stay on your phone.
- A backup is a plain file saved where you choose. Do not put passwords in shortcut text, because they would be stored and backed up as ordinary text.

**The internet**
- The only thing the app contacts is GitHub, over HTTPS, to look for a new release and to download an update you choose to install.
- Switch the automatic check off in **Settings > Updates**. The app then only connects when you tap **Check now** or open **Version history**.

**Updates**
- A downloaded update is checked against its published SHA-256 checksum before it is installed, and is refused if the checksum is missing or does not match.
- Android only accepts an update signed with the same key as the installed app, so a modified copy from elsewhere cannot replace it.
- Android always asks you to confirm an install. The app cannot install anything silently.

**Bluetooth**
- Pairing is normal Bluetooth pairing, confirmed on both devices, and the app only connects to devices you have already paired.
- Discoverable mode lasts five minutes and only starts when you tap the button.
- Automatic reconnection only tries a few recently used, already paired devices.
- While connected, the phone can send any keystroke to that computer, which is what a keyboard does. Keep your phone locked when you are away, and remove devices you no longer use from the phone's Bluetooth list.
- There is no app lock yet.

**Permissions**
- The app asks for permissions once, in the setup guide. It never asks again on its own. If you decline one, that feature stays off until you allow it yourself, in Settings or in the phone's app settings.

**Floating menu**
- It uses Android's "display over other apps" permission only to draw the bubble and dial, and only while this app is in the background. The app does not use accessibility services and cannot read what other apps show.

**Check a download**

Compare the file's checksum with the one published next to it on the release page.

```bash
sha256sum AirMousePro-v0.0.1.apk
```

On Windows:

```bash
certutil -hashfile AirMousePro-v0.0.1.apk SHA256
```

**Reporting a security problem**

Please do not post it in a public issue. Open the **Security** tab of this repository and choose **Report a vulnerability**, so it stays private until it is fixed.

### Permissions

| Permission | Why |
|---|---|
| Nearby devices (Bluetooth) | Pair with, connect to and appear as a Bluetooth device |
| Notifications | Show the "running" notification with its Stop button |
| Display over other apps | Draw the floating bubble and dial |
| Run in the background | Keep the app alive so Android does not drop the Bluetooth connection |
| Vibration | Haptic feedback and presentation timer cues |
| Internet | Check GitHub for updates and download one you choose |
| Install and uninstall apps | Hand an update you approved to Android's installer, and open the uninstall screen if you choose a manual rollback |

## Updates

The app checks for a new version when it opens, at most once every six hours, and asks before installing. You can also check by hand and browse every release in **Settings > Updates > Version history**.

**Going back to an older version.** Android does not allow installing an older version over a newer one. The simple way is:

1. **Settings > Backup > Back up**, and save the file.
2. Uninstall AirMouse Pro.
3. Install the older APK from the [Releases page](https://github.com/reddynalamari/AirMousePro/releases).
4. **Settings > Backup > Restore**, and pick the file.

## Troubleshooting

| Problem | Try this |
|---|---|
| The computer pairs but never connects | Remove the phone from the computer, forget the computer on the phone, then pair again while the app is open. |
| "Setup failed" on the Devices page | Tap **Refresh**. If it repeats, open **Settings > Developer options** and use **Re-register HID**. |
| Keys type the wrong characters | Set the computer's keyboard layout in **Settings > Keyboard**. |
| The game does not react to the gamepad | Turn on **Keys** on the gamepad page: it sends WASD, mouse and click input that nearly every game accepts. |
| The gamepad is not detected at all | On Windows press Win+R, run `joy.cpl`, and tap **Test** in the app. If the pad responds there, the game needs an Xbox-style controller. |
| An update will not install | The installed copy was signed with a different key. Back up in **Settings > Backup**, uninstall once, install the release APK, then restore. |
| Something else | Copy the log from **Settings > Developer options** and attach it to an issue. Remove any device names or Bluetooth addresses you want to keep private first. |

## Contributing

Want to help build it? See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Licensed under the [Apache License 2.0](LICENSE). Anyone, including companies, can use, modify and distribute AirMouse Pro free of charge. Redistributions and forks must keep the [NOTICE](NOTICE) file and the copyright and license text, which credit the original author, and must state any changes made.

Built by **Shashidhar Reddy**.
