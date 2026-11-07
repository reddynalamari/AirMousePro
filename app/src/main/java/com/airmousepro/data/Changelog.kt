package com.airmousepro.data

/**
 * Release notes shown inside the app after an update, so users see exactly what was added, improved or removed.
 *
 * To publish notes for a release: add entries below with `rev = CHANGELOG_REV + 1`, then raise [CHANGELOG_REV] to match.
 * A user who last saw revision N is shown every entry with a higher revision. New installs see the setup guide instead.
 */
const val CHANGELOG_REV = 2

enum class ChangeKind(val label: String) { New("NEW"), Improved("IMPROVED"), Removed("REMOVED") }

/** [icon] is a key understood by the what's-new screen, so this file needs no UI imports. */
data class ChangeEntry(val rev: Int, val kind: ChangeKind, val icon: String, val title: String, val body: String)

val CHANGELOG = listOf(
    ChangeEntry(
        1, ChangeKind.New, "bluetooth", "Reconnects by itself",
        "When the app opens it tries the devices you used before and connects to the first one that is available. The Devices page has a new Recent list.",
    ),
    ChangeEntry(
        1, ChangeKind.New, "brightness", "Brightness knob",
        "The quick menu dial now has a brightness knob beside volume. Drag up or down on the ring to change it.",
    ),
    ChangeEntry(
        1, ChangeKind.Improved, "bubble", "Quick menu stays out of the way",
        "The bubble now only appears while the app is in the background. Double-tap it to open the app, or drag it onto the X to remove it.",
    ),
    ChangeEntry(
        1, ChangeKind.Improved, "shield", "Safer updates",
        "A downloaded update is now refused unless it matches its published checksum.",
    ),
    ChangeEntry(
        1, ChangeKind.New, "gamepad", "Gamepad self-test",
        "A Test button and a live report counter help you check that the computer can see the gamepad.",
    ),
    ChangeEntry(
        1, ChangeKind.New, "guide", "Setup guide and What's new",
        "New installs get a short walkthrough, and after every update you see a guide like this one covering only what changed. Both can be replayed from Settings.",
    ),
    ChangeEntry(
        2, ChangeKind.New, "gamepad", "Keys mode for the gamepad",
        "Most PC games ignore a generic Bluetooth gamepad. Turn on Keys on the gamepad page and the same controls press keyboard keys and move the mouse instead: left stick is WASD, right stick is the mouse, the triggers click.",
    ),
    ChangeEntry(
        2, ChangeKind.Improved, "bubble", "Rotation now follows your phone",
        "With auto-rotate off, the app stays in portrait. The gamepad page no longer turns the screen by itself: it offers a Rotate to landscape button instead.",
    ),
    ChangeEntry(
        1, ChangeKind.Removed, "volume", "Volume and brightness percentage",
        "The estimated level was often wrong because computers never report their level back, so it has been removed. The knobs and buttons work as before.",
    ),
)

/** Entries the user has not seen yet, with additions first and removals last. */
fun unseenChanges(seenRev: Int): List<ChangeEntry> =
    CHANGELOG.filter { it.rev > seenRev }.sortedBy { it.kind.ordinal }
