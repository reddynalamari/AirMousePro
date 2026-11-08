package com.airmousepro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airmousepro.hid.HidController

/** Shortcuts, presentation remote and gamepad share one tab to keep the navigation short. */
@Composable
fun DeckScreen(hid: HidController) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            Box(Modifier.weight(1f)) {
                Segmented(listOf("Shortcuts", "Present", "Gamepad"), mode, { mode = it }, Modifier.widthIn(max = 420.dp))
            }
        }
        Box(Modifier.weight(1f)) {
            when (mode) {
                0 -> ShortcutsPane(hid)
                1 -> PresentScreen(hid)
                else -> GamepadScreen(hid)
            }
        }
    }
}
