package com.airmousepro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.hid.HidController

/** What the phone is controlling. The index is what the app stores and what gestures and shortcuts switch on. */
val OsNames = listOf("Windows", "macOS", "Linux", "Android TV", "Android phone", "iPhone or iPad")
private val OsShort = listOf("Windows", "Mac", "Linux", "TV", "Android", "iPhone")
private val OsIcons = listOf(
    Icons.Rounded.DesktopWindows, Icons.Rounded.LaptopMac, Icons.Rounded.Computer,
    Icons.Rounded.Tv, Icons.Rounded.PhoneAndroid, Icons.Rounded.PhoneIphone,
)

/**
 * The small switch at the top of the app. It shows what the connected device was detected as and lets you correct it
 * in one tap. The choice is remembered for that device. [iconOnly] is for the slim landscape rail.
 */
@Composable
fun TargetPicker(hid: HidController, iconOnly: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val current = hid.target
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { open = true }
                .padding(horizontal = if (iconOnly) 8.dp else 12.dp, vertical = if (iconOnly) 8.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(OsIcons[current], contentDescription = "Controlling ${OsNames[current]}", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            if (!iconOnly) {
                Text(OsShort[current], modifier = Modifier.padding(start = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            OsNames.forEachIndexed { i, name ->
                DropdownMenuItem(
                    text = { Text(name, fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Icon(OsIcons[i], contentDescription = null, tint = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                    onClick = { hid.setTarget(i); open = false },
                )
            }
        }
    }
}
