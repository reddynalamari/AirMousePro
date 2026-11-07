package com.airmousepro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NavigateBefore
import androidx.compose.material.icons.rounded.NavigateNext
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.airmousepro.appSettings
import com.airmousepro.hid.Consumer
import com.airmousepro.hid.HidController
import com.airmousepro.hid.Key

/** D-pad plus media and volume keys, for TVs, presentations and media players. */
@Composable
fun RemoteScreen(hid: HidController) {
    if (isLandscape()) {
        // Navigation on the left, media on the right: both reachable with a thumb on each side.
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) { NavPad(hid) }
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) { MediaPad(hid) }
        }
    } else {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NavPad(hid)
            Spacer(Modifier.height(8.dp))
            MediaPad(hid)
        }
    }
}

@Composable
private fun NavPad(hid: HidController) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Pad(Icons.AutoMirrored.Rounded.ArrowBack, "Back") { hid.consumer(Consumer.BACK) }
        Pad(Icons.Rounded.Home, "Home") { hid.consumer(Consumer.HOME) }
        Pad(Icons.Rounded.Menu, "Menu") { hid.consumer(Consumer.MENU) }
    }
    Pad(Icons.Rounded.KeyboardArrowUp, "Up") { hid.keyCombo(0, Key.UP) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Pad(Icons.Rounded.KeyboardArrowLeft, "Left") { hid.keyCombo(0, Key.LEFT) }
        Button(onClick = { hid.keyCombo(0, Key.ENTER) }, modifier = Modifier.size(84.dp)) { Text("OK") }
        Pad(Icons.Rounded.KeyboardArrowRight, "Right") { hid.keyCombo(0, Key.RIGHT) }
    }
    Pad(Icons.Rounded.KeyboardArrowDown, "Down") { hid.keyCombo(0, Key.DOWN) }
}

@Composable
private fun MediaPad(hid: HidController) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Pad(Icons.Rounded.SkipPrevious, "Previous") { hid.consumer(Consumer.PREV) }
        Pad(Icons.Rounded.PlayArrow, "Play or pause") { hid.consumer(Consumer.PLAY_PAUSE) }
        Pad(Icons.Rounded.SkipNext, "Next") { hid.consumer(Consumer.NEXT) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Pad(Icons.Rounded.VolumeDown, "Volume down") { hid.consumer(Consumer.VOL_DOWN) }
        Pad(Icons.Rounded.VolumeOff, "Mute") { hid.consumer(Consumer.MUTE) }
        Pad(Icons.Rounded.VolumeUp, "Volume up") { hid.consumer(Consumer.VOL_UP) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Pad(Icons.Rounded.NavigateBefore, "Previous slide") { hid.keyCombo(0, Key.PAGE_UP) }
        Pad(Icons.Rounded.NavigateNext, "Next slide") { hid.keyCombo(0, Key.PAGE_DOWN) }
    }
}

@Composable
private fun Pad(icon: ImageVector, description: String, onClick: () -> Unit) {
    val settings = LocalContext.current.appSettings
    val view = LocalView.current
    FilledTonalIconButton(
        onClick = {
            Haptics.tap(view, settings)
            onClick()
        },
        modifier = Modifier.size(64.dp),
    ) { Icon(icon, contentDescription = description, modifier = Modifier.size(28.dp)) }
}
