package com.airmousepro.data

/**
 * Release notes shown inside the app after an update, so users see exactly what was added, improved or removed.
 *
 * To publish notes for a release: add entries below with `rev = CHANGELOG_REV + 1`, then raise [CHANGELOG_REV] to match.
 * A user who last saw revision N is shown every entry with a higher revision. New installs see the setup guide instead.
 */
const val CHANGELOG_REV = 5

enum class ChangeKind(val label: String) { New("NEW"), Improved("IMPROVED"), Removed("REMOVED") }

/** [icon] is a key understood by the what's-new screen, so this file needs no UI imports. */
data class ChangeEntry(val rev: Int, val kind: ChangeKind, val icon: String, val title: String, val body: String)

val CHANGELOG = listOf(
    ChangeEntry(
        5, ChangeKind.New, "bubble", "TV remote mode",
        "When the connected device is a TV, the touchpad becomes a remote. Swipe to move the highlight, tap for OK, two fingers to go back, three for Home. Two fingers up or down changes volume.",
    ),
    ChangeEntry(
        5, ChangeKind.New, "bluetooth", "Knows what you are connected to",
        "The app now tells a Windows PC, Mac, Linux machine, Android TV, Android phone and iPhone apart when you connect. A small switch at the top shows the result and lets you correct it in one tap. Your choice is remembered for that device.",
    ),
    ChangeEntry(
        5, ChangeKind.Improved, "gamepad", "Works with phones and tablets",
        "Controlling an Android phone or an iPhone or iPad now uses Home and Back where they exist, and shortcuts that open a website or app use the right search for each. On an iPhone, turn on AssistiveTouch so it shows the pointer.",
    ),
    ChangeEntry(
        5, ChangeKind.Improved, "bubble", "Steadier air mouse and trackpad swipes",
        "The air mouse holds still when you do, follows quick sweeps without lag, and takes back the nudge your thumb causes just before a click. On the trackpad, two fingers left and right now reliably go back and forward instead of zooming.",
    ),
    ChangeEntry(
        5, ChangeKind.Improved, "gamepad", "Air mouse on a TV",
        "On a TV a quick flick of your wrist presses an arrow key instead of moving a pointer.",
    ),
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
        4, ChangeKind.New, "bubble", "Back and forward with two fingers",
        "Swipe two fingers quickly to the left on the touchpad to go back, and to the right to go forward. Slow sideways movement still scrolls. You can turn it off in Settings.",
    ),
    ChangeEntry(
        4, ChangeKind.Improved, "bluetooth", "Scanning finds your TV",
        "The Nearby devices scan now picks up devices that your phone's own Bluetooth screen finds, including ones whose name arrives a moment late.",
    ),
    ChangeEntry(
        4, ChangeKind.Improved, "bubble", "Much better in landscape",
        "Text that was invisible in landscape is readable again. The side rail now fits every tab, the Devices page uses two panes, Deck shows three rows of shortcuts, and the keyboard and remote fit without scrolling.",
    ),
    ChangeEntry(
        3, ChangeKind.New, "bluetooth", "Find and pair new devices",
        "The Devices page now has a Nearby devices list. Tap Scan, then tap a TV or computer to pair it without leaving the app. This also reaches TVs that hide the phone from their own accessory list.",
    ),
    ChangeEntry(
        3, ChangeKind.Improved, "bubble", "Smoother cursor",
        "Pointer movement is now sent from its own thread and merged into steady updates, which removes the lag and sticking you could feel. Touchpad acceleration now follows finger speed, so it stays even.",
    ),
    ChangeEntry(
        3, ChangeKind.Improved, "bluetooth", "Refresh no longer reconnects",
        "Refreshing the Devices page used to reconnect to your recent devices every time, which got in the way of pairing a new one. Now only opening the app does that.",
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
