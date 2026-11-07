# Contributing to AirMouse Pro

Thanks for helping. This page is for people working on the code. If you only want to use the app, see the [README](README.md).

## Before you start

- **Bugs and ideas:** open an issue. For a bug, say what you did, what you expected, and what happened. Include your phone model, Android version and the computer or TV you were controlling. The log from **Settings > Developer options > Copy all** helps a lot. Remove device names and Bluetooth addresses you want to keep private.
- **Security problems:** do not open a public issue. Use the **Security** tab and choose **Report a vulnerability**.
- **Larger changes:** open an issue first so we can agree on the approach before you spend time on it.

## Build

You need **JDK 17** and the **Android SDK 34**. The repository has no Gradle wrapper, so use Android Studio, or a local Gradle 8.13 or newer.

```bash
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

## Testing

Bluetooth HID cannot be emulated, so changes to connection, input or the HID report description need a real Android phone and a real computer or TV to pair with. Please say what you tested on in your pull request.

If you change the HID report description (`hid/HidDescriptor.kt`), say so in the pull request. Computers cache it, so users have to re-pair, and the release notes must mention that.

## Code layout

Under `app/src/main/java/com/airmousepro/`:

| Folder | Contents |
|---|---|
| `hid/` | HID report description, key maps and the Bluetooth HID controller |
| `input/` | Air-mouse engine and trackpad gesture engine |
| `data/` | Settings, shortcuts and macros, backup |
| `ui/` | Jetpack Compose screens and theme |
| `ui/guide/` | The setup guide, the what's-new tour and their animated pages |
| `overlay/` | Floating bubble, dial and mini touchpad |
| `service/` | Foreground service and Quick Settings tile |
| `update/` | Update checker, installer and result receiver |

## Pull requests

- Keep each pull request to one change, and match the style of the code around it.
- Make sure `gradle :app:assembleDebug` passes. CI runs it on every pull request.
- Do not commit keys, passwords, tokens, `.env` files, APKs or logs that show personal device names. Report anything you find that looks like a leaked secret privately, as a security problem.
- Update the README if you change something users will notice.
- **Release notes inside the app.** Users see a "What's new" tour after an update. For a user-visible change, add an entry to `data/Changelog.kt` with `rev = CHANGELOG_REV + 1`, then raise `CHANGELOG_REV` to match. Mark it New, Improved or Removed and keep the text short and in plain language. New installs see the setup guide instead, so they never see old entries.

Releases are published automatically from the main branch by the maintainers.

## License

By contributing you agree that your work is licensed under the [Apache License 2.0](LICENSE).
