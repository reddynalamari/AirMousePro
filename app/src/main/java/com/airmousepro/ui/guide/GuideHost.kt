package com.airmousepro.ui.guide

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.ui.isLandscape
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One page of a guide. [active] is true while the page is the one being shown, and drives its entrance animation. */
typealias GuidePage = @Composable (active: Boolean) -> Unit

/**
 * A full-screen, swipeable walkthrough: pages slide and fade as you drag, the dots stretch to show where you are,
 * and Back, Next and Skip are always within reach. Used for both the first-run setup and the "what's new" tour.
 */
@Composable
fun GuideHost(
    pages: List<GuidePage>,
    lastLabel: String,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    skipLabel: String = "Skip",
) {
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val current = state.currentPage
    val last = current == pages.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Absorbs touches on empty areas, so nothing underneath (the trackpad, say) reacts to them.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(vertical = 8.dp),
    ) {
        // Progress on the left, Skip on the right. Skip fades away on the last page.
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${current + 1} of ${pages.size}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(visible = !last, enter = fadeIn(), exit = fadeOut()) {
                TextButton(onClick = onSkip) { Text(skipLabel) }
            }
        }

        HorizontalPager(state = state, modifier = Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    // Pages shrink and fade as they leave, which gives the swipe a sense of depth.
                    val distance = abs((state.currentPage - page) + state.currentPageOffsetFraction).coerceIn(0f, 1f)
                    alpha = 1f - 0.75f * distance
                    scaleX = 1f - 0.08f * distance
                    scaleY = 1f - 0.08f * distance
                },
            ) { pages[page](page == state.currentPage) }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                // Qualified so it is not mistaken for the Row-scoped overload from the enclosing Row.
                androidx.compose.animation.AnimatedVisibility(visible = current > 0, enter = fadeIn(), exit = fadeOut()) {
                    TextButton(onClick = { scope.launch { state.animateScrollToPage(current - 1) } }) { Text("Back") }
                }
            }
            Dots(count = pages.size, current = current) { scope.launch { state.animateScrollToPage(it) } }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Button(onClick = { if (last) onFinish() else scope.launch { state.animateScrollToPage(current + 1) } }) {
                    AnimatedContent(
                        targetState = if (last) lastLabel else "Next",
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                        label = "primaryLabel",
                    ) { Text(it) }
                }
            }
        }
    }
}

/** Page indicator: the current dot stretches into a pill, and any dot can be tapped. */
@Composable
private fun Dots(count: Int, current: Int, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 22.dp else 7.dp, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "dotWidth")
            val color by animateColorAsState(if (selected) accent else idle, tween(220), label = "dotColor")
            Box(Modifier.height(7.dp).size(width, 7.dp).clip(CircleShape).background(color).clickable { onSelect(i) })
        }
    }
}

/**
 * Standard page: an animated illustration, a title and some text, plus optional extra content such as a button.
 * Portrait stacks them. Landscape puts the illustration beside the text. The text rises into place when the page opens.
 */
@Composable
fun GuidePageLayout(
    active: Boolean,
    hero: @Composable () -> Unit,
    title: String,
    body: String,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val landscape = isLandscape()
    val align = if (landscape) TextAlign.Start else TextAlign.Center
    val text = @Composable {
        Column(
            Modifier.widthIn(max = 460.dp),
            horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Reveal(active, 0) { Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = align, lineHeight = 34.sp) }
            Reveal(active, 90) {
                Text(body, fontSize = 16.sp, lineHeight = 23.sp, textAlign = align, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
            if (extra != null) {
                Reveal(active, 180) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally,
                    ) { extra() }
                }
            }
        }
    }

    if (landscape) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { hero() }
            Box(Modifier.weight(1f).verticalScroll(rememberScrollState()), contentAlignment = Alignment.CenterStart) { text() }
        }
    } else {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Centred when it fits, scrolls when the text is long or the font is large.
                Column(
                    Modifier.fillMaxWidth().heightIn(min = viewport).padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    hero()
                    Box(Modifier.height(20.dp))
                    text()
                }
            }
        }
    }
}

/** Fades and slides its content up shortly after the page becomes active. */
@Composable
private fun Reveal(active: Boolean, delayMs: Int, content: @Composable () -> Unit) {
    // Starting hidden and flipping on the next frame makes the very first page animate in too.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(active) { shown = active }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(380, delayMs)) + slideInVertically(tween(440, delayMs)) { it / 4 },
        exit = fadeOut(tween(120)),
    ) { content() }
}
