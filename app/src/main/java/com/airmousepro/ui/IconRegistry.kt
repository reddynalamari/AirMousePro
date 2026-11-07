package com.airmousepro.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons a shortcut can use, keyed by a stable string that is saved with it. */
object IconRegistry {
    val all: Map<String, ImageVector> = linkedMapOf(
        "bolt" to Icons.Rounded.Bolt,
        "star" to Icons.Rounded.Star,
        "web" to Icons.Rounded.Language,
        "apps" to Icons.Rounded.Apps,
        "rocket" to Icons.Rounded.RocketLaunch,
        "tv" to Icons.Rounded.Tv,
        "play" to Icons.Rounded.PlayArrow,
        "music" to Icons.Rounded.MusicNote,
        "video" to Icons.Rounded.Videocam,
        "volume" to Icons.Rounded.VolumeUp,
        "copy" to Icons.Rounded.ContentCopy,
        "paste" to Icons.Rounded.ContentPaste,
        "keyboard" to Icons.Rounded.Keyboard,
        "terminal" to Icons.Rounded.Terminal,
        "code" to Icons.Rounded.Code,
        "mic" to Icons.Rounded.Mic,
        "micoff" to Icons.Rounded.MicOff,
        "lock" to Icons.Rounded.Lock,
        "sleep" to Icons.Rounded.Bedtime,
        "power" to Icons.Rounded.PowerSettingsNew,
        "home" to Icons.Rounded.Home,
        "search" to Icons.Rounded.Search,
        "screenshot" to Icons.Rounded.Screenshot,
        "slides" to Icons.Rounded.Slideshow,
        "fullscreen" to Icons.Rounded.Fullscreen,
        "chat" to Icons.Rounded.Chat,
        "mail" to Icons.Rounded.Mail,
    )

    fun get(key: String): ImageVector = all[key] ?: Icons.Rounded.Bolt
}
