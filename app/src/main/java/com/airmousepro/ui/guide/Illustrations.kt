package com.airmousepro.ui.guide

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Green = Color(0xFF30D158)

/** A 0..1 value that loops forever. Reading it inside draw or offset blocks keeps the animation off the composition. */
@Composable
private fun loop(millis: Int, label: String): androidx.compose.runtime.State<Float> =
    rememberInfiniteTransition(label = label).animateFloat(0f, 1f, infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart), label = label)

/** Icons orbit a phone while rings spread out from it. */
@Composable
fun WelcomeHero() {
    val p by loop(5200, "welcome")
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val icons = listOf(Icons.Rounded.Mouse, Icons.Rounded.Keyboard, Icons.Rounded.SportsEsports, Icons.Rounded.SettingsRemote)

    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 3) {
                val q = (p * 2f + i / 3f) % 1f
                drawCircle(accent.copy(alpha = (1f - q) * 0.35f), radius = 44.dp.toPx() + q * 66.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
        }
        Box(
            Modifier.size(62.dp, 108.dp).clip(RoundedCornerShape(18.dp)).background(accent),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Mouse, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        icons.forEachIndexed { i, icon ->
            Box(
                Modifier
                    .offset {
                        val a = (p * 2 * PI) + i * PI / 2
                        IntOffset((cos(a) * 92.dp.toPx()).roundToInt(), (sin(a) * 92.dp.toPx()).roundToInt())
                    }
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(surface)
                    .border(1.dp, outline.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp)) }
        }
    }
}

/** A pulsing badge that turns green with a check when the permission is granted. */
@Composable
fun PermissionHero(granted: Boolean) {
    val p by loop(2200, "permission")
    val accent = MaterialTheme.colorScheme.primary
    val color = if (granted) Green else accent
    val scale by animateFloatAsState(if (granted) 1.08f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "permScale")

    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val q = p
            drawCircle(color.copy(alpha = (1f - q) * 0.3f), radius = 52.dp.toPx() + q * 54.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
        Box(
            Modifier.size(110.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = granted, animationSpec = tween(300), label = "permIcon") { g ->
                Icon(if (g) Icons.Rounded.Check else Icons.Rounded.BluetoothSearching, contentDescription = null, tint = color, modifier = Modifier.size(54.dp))
            }
        }
    }
}

/** A phone and a computer with dots travelling between them. The line turns solid green once connected. */
@Composable
fun PairHero(connected: Boolean) {
    val p by loop(1800, "pair")
    val accent = MaterialTheme.colorScheme.primary
    val color = if (connected) Green else accent
    Row(Modifier.fillMaxWidth().height(230.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        EndpointBadge(Icons.Rounded.PhoneAndroid)
        Canvas(Modifier.weight(1f).height(40.dp)) {
            val y = size.height / 2
            if (connected) {
                drawLine(color, Offset(8.dp.toPx(), y), Offset(size.width - 8.dp.toPx(), y), strokeWidth = 4.dp.toPx())
            } else {
                for (i in 0 until 5) {
                    val q = (p + i / 5f) % 1f
                    drawCircle(color.copy(alpha = sin(q * PI).toFloat()), 4.5.dp.toPx(), Offset(q * size.width, y))
                }
            }
        }
        EndpointBadge(Icons.Rounded.Laptop)
    }
}

@Composable
private fun EndpointBadge(icon: ImageVector) {
    Box(
        Modifier.size(70.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
}

/** A pad that loops through the four main gestures with moving fingertips, naming each one underneath. */
@Composable
fun GestureHero() {
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val dot = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val labels = listOf("Tap" to "Click", "Two fingers" to "Scroll", "Pinch" to "Zoom", "Three fingers" to "Switch apps")
    var step by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1900, easing = FastOutSlowInEasing))
            delay(500)
            step = (step + 1) % labels.size
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(264.dp, 172.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(surface)
                .drawBehind {
                    val gap = 22.dp.toPx()
                    var y = gap / 2
                    while (y < size.height) {
                        var x = gap / 2
                        while (x < size.width) {
                            drawCircle(dot, 1.2.dp.toPx(), Offset(x, y))
                            x += gap
                        }
                        y += gap
                    }
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val p = progress.value
                    val r = 15.dp.toPx()
                    fun finger(x: Float, y: Float) {
                        drawCircle(accent.copy(alpha = 0.25f), r * 1.8f, Offset(x, y))
                        drawCircle(accent, r, Offset(x, y))
                    }
                    when (step) {
                        0 -> {
                            val press = if (p < 0.35f) p / 0.35f else 1f
                            drawCircle(accent, r * (1.25f - 0.25f * press), Offset(cx, cy))
                            if (p > 0.35f) {
                                val q = (p - 0.35f) / 0.65f
                                drawCircle(accent.copy(alpha = (1f - q) * 0.55f), r + q * 46.dp.toPx(), Offset(cx, cy), style = Stroke(3.dp.toPx()))
                            }
                        }
                        1 -> {
                            val dy = (p * 2f - 1f) * 40.dp.toPx()
                            finger(cx - 24.dp.toPx(), cy + dy)
                            finger(cx + 24.dp.toPx(), cy + dy)
                        }
                        2 -> {
                            val spread = 16.dp.toPx() + p * 52.dp.toPx()
                            finger(cx - spread * 0.85f, cy + spread * 0.5f)
                            finger(cx + spread * 0.85f, cy - spread * 0.5f)
                        }
                        else -> {
                            val dx = (0.5f - p) * 150.dp.toPx()
                            finger(cx + dx - 40.dp.toPx(), cy)
                            finger(cx + dx, cy)
                            finger(cx + dx + 40.dp.toPx(), cy)
                        }
                    }
                },
        )
        Box(Modifier.height(14.dp))
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "gestureLabel",
        ) { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(labels[s].first, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("  →  ${labels[s].second}", fontSize = 17.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** A phone tilting side to side while a cursor on the screen above it follows. */
@Composable
fun AirHero() {
    val transition = rememberInfiniteTransition(label = "air")
    val angle by transition.animateFloat(-20f, 20f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "angle")
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface

    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.align(Alignment.TopCenter).size(210.dp, 96.dp).clip(RoundedCornerShape(20.dp)).background(surface),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .offset { IntOffset((((angle + 20f) / 40f - 0.5f) * 150.dp.toPx()).roundToInt(), 0) }
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .size(58.dp, 104.dp)
                .graphicsLayer { rotationZ = angle; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
                .clip(RoundedCornerShape(16.dp))
                .background(accent),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Mouse, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp)) }
    }
}

/** The bubble sits on the screen edge, then fans its actions open and closes again. */
@Composable
fun QuickMenuHero() {
    val p by loop(4200, "quick")
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val icons = listOf(Icons.Rounded.VolumeUp, Icons.Rounded.LightMode, Icons.Rounded.Keyboard, Icons.Rounded.PlayArrow)

    // 0 closed, 1 open: opens between 20% and 40% of the loop, holds, closes from 75%.
    fun openness(v: Float) = when {
        v < 0.2f -> 0f
        v < 0.4f -> (v - 0.2f) / 0.2f
        v < 0.75f -> 1f
        else -> 1f - (v - 0.75f) / 0.25f
    }

    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(150.dp, 200.dp).clip(RoundedCornerShape(26.dp)).background(surface).border(1.dp, outline.copy(alpha = 0.4f), RoundedCornerShape(26.dp)))
        icons.forEachIndexed { i, icon ->
            val angle = Math.toRadians(180.0 + (i - 1.5) * 34.0)
            Box(
                Modifier
                    .offset {
                        val o = openness(p)
                        IntOffset(
                            (68.dp.toPx() + cos(angle) * 84.dp.toPx() * o).roundToInt(),
                            (sin(angle) * 84.dp.toPx() * o).roundToInt(),
                        )
                    }
                    .graphicsLayer { val o = openness(p); alpha = o; scaleX = 0.6f + 0.4f * o; scaleY = 0.6f + 0.4f * o }
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(surface)
                    .border(1.dp, outline.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp)) }
        }
        Box(
            Modifier.offset(x = 68.dp).size(50.dp).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Mouse, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp)) }
    }
}

/** A check that springs in over an expanding ring. */
@Composable
fun DoneHero() {
    val p by loop(2400, "done")
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(if (shown) 1f else 0.2f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "doneScale")

    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Green.copy(alpha = (1f - p) * 0.35f), radius = 54.dp.toPx() + p * 56.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
        Box(
            Modifier.size(108.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(Green),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(58.dp)) }
    }
}

/** For the "what's new" pages: a coloured badge that pops in, with a ring that keeps pulsing. */
@Composable
fun BadgeHero(icon: ImageVector, color: Color) {
    val p by loop(2600, "badge")
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(if (shown) 1f else 0.5f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "badgeScale")

    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color.copy(alpha = (1f - p) * 0.35f), radius = 58.dp.toPx() + p * 48.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
        Box(
            Modifier.size(124.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = scale.coerceIn(0f, 1f) }.clip(CircleShape).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(58.dp)) }
    }
}
