/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.desktop.lyrics.LyricLine
import com.ozyern.exhale.desktop.lyrics.LyricWord
import com.ozyern.exhale.desktop.lyrics.Lyrics
import com.ozyern.exhale.desktop.lyrics.LyricsParser
import com.ozyern.exhale.desktop.player.PlayerState
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val LyricFontSize = 30f

/** How far ahead of the audio the highlight runs, to cover the time it takes to draw and be seen. */
private const val WordLeadMs = 120L

/**
 * Playback position at frame resolution. mpv reports time-pos in steps; between reports this
 * advances with the frame clock, so a word's fill sweeps smoothly instead of stepping.
 */
@Composable
fun rememberSmoothPositionMs(state: PlayerState): State<Long> {
    val position = remember { mutableLongStateOf((state.positionSeconds * 1000).toLong()) }
    val reported by rememberUpdatedState(state.positionSeconds)
    val running by rememberUpdatedState(state.isPlaying && !state.isLoading)
    LaunchedEffect(Unit) {
        var anchor = -1.0
        var anchorAt = 0L
        while (true) {
            withFrameMillis { now ->
                if (reported != anchor) {
                    anchor = reported
                    anchorAt = now
                }
                val elapsed = if (running) (now - anchorAt).coerceAtMost(600) else 0
                position.longValue = (reported * 1000).toLong() + elapsed
            }
            if (!running) delay(120)
        }
    }
    return position
}

/** Fades a list out at its top and bottom edges instead of cutting it off. */
internal fun Modifier.edgeFade(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.14f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun lyricStyle(fontSize: Float = LyricFontSize): TextStyle = MaterialTheme.typography.headlineMedium.copy(
    fontSize = fontSize.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeight = (fontSize * 1.35f).sp,
    letterSpacing = (-0.01).em,
)

@Composable
internal fun LyricsPanel(lyrics: Lyrics?, position: State<Long>, onSeek: (Double) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        when (lyrics) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.CenterStart).padding(start = 12.dp), color = Color.White)
            Lyrics.NotFound -> Text(
                "No lyrics for this song.",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
            )
            is Lyrics.Plain -> LazyColumn(
                contentPadding = PaddingValues(vertical = 120.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize().edgeFade(),
            ) {
                itemsIndexed(lyrics.lines) { _, line ->
                    Text(line, style = lyricStyle(), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(horizontal = 12.dp))
                }
                item { SourceLabel("${lyrics.source} · not synced") }
            }
            is Lyrics.Synced -> SyncedLyricsView(lyrics, position, onSeek)
        }
    }
}

@Composable
private fun SourceLabel(text: String) {
    Text("Lyrics from $text", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(start = 12.dp, top = 12.dp))
}

@Composable
private fun SyncedLyricsView(lyrics: Lyrics.Synced, position: State<Long>, onSeek: (Double) -> Unit) {
    val lines: List<LyricLine> = lyrics.lines
    // Recomposes only when the sung line changes, not every frame.
    val current by remember(lines) { derivedStateOf { LyricsParser.currentIndex(lines, position.value) } }
    val listState = rememberLazyListState()

    var autoScrolling by remember { mutableStateOf(false) }
    var userScrolledAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !autoScrolling) userScrolledAt = System.currentTimeMillis()
        }
    }
    LaunchedEffect(current, lyrics) {
        if (current < 0 || System.currentTimeMillis() - userScrolledAt < 3_500) return@LaunchedEffect
        autoScrolling = true
        try {
            val offset = -(listState.layoutInfo.viewportSize.height * 0.32f).toInt()
            listState.animateScrollToItem(current, offset)
        } finally {
            autoScrolling = false
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 60.dp, bottom = 360.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize().edgeFade(),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index:${line.timeMs}" }) { index, line ->
            val active = index == current
            val past = current >= 0 && index < current
            val distance = if (current < 0) index + 1 else abs(index - current)
            val lineAlpha by animateFloatAsState(
                when {
                    active -> 1f
                    distance == 1 -> 0.5f
                    else -> 0.3f
                },
                animationSpec = tween(320),
                label = "lyric_alpha",
            )
            val scale by animateFloatAsState(if (active) 1f else 0.94f, spring(dampingRatio = 0.7f, stiffness = 260f), label = "lyric_scale")
            val blur by animateDpAsState(if (distance >= 3) 2.dp else 0.dp, label = "lyric_blur")

            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = if (active) 1f else lineAlpha
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .blur(blur)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSeek(line.timeMs / 1000.0) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                when {
                    line.text.isBlank() -> BreathingDots(active)
                    active && line.words.isNotEmpty() -> WordLine(line.words, position.value + WordLeadMs)
                    else -> Text(line.text, style = lyricStyle(), color = Color.White.copy(alpha = if (past) 0.85f else 0.7f))
                }
            }
        }
        item { SourceLabel(if (lyrics.wordLevel) "${lyrics.source} · word synced" else lyrics.source) }
    }
}

/** Main words on one flowing line, background vocals on a smaller line under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordLine(words: List<LyricWord>, positionMs: Long) {
    val main = words.filter { !it.isBackground }
    val background = words.filter { it.isBackground }
    Column {
        if (main.isNotEmpty()) {
            FlowRow(Modifier.fillMaxWidth()) {
                main.forEach { word -> AnimatedWord(word, positionMs, LyricFontSize, isBackground = false) }
            }
        }
        if (background.isNotEmpty()) {
            if (main.isNotEmpty()) Spacer(Modifier.height(4.dp))
            FlowRow(Modifier.fillMaxWidth()) {
                background.forEach { word -> AnimatedWord(word, positionMs, LyricFontSize * 0.65f, isBackground = true) }
            }
        }
    }
}

/**
 * The Android app's AnimatedWordV2: a dim base word with a lit copy revealed by a soft-edged sweep
 * that tracks the word's own timing, plus a swell (lift, scale, glow) shaped like a sung note —
 * fast attack, held sustain, release — rather than a symmetric arch that peaks late.
 */
@Composable
private fun AnimatedWord(word: LyricWord, positionMs: Long, fontSize: Float, isBackground: Boolean) {
    val duration = (word.endMs - word.startMs).coerceAtLeast(1L)
    val complete = positionMs >= word.endMs
    val singing = positionMs in word.startMs until word.endMs
    val progress = when {
        complete -> 1f
        positionMs <= word.startMs -> 0f
        else -> ((positionMs - word.startMs).toFloat() / duration).coerceIn(0f, 1f)
    }

    val swell = when {
        progress < 0.18f -> progress / 0.18f
        progress < 0.75f -> 1f - 0.28f * ((progress - 0.18f) / 0.57f)
        else -> 0.72f * (1f - (progress - 0.75f) / 0.25f)
    }.coerceIn(0f, 1f)

    val wordScale = 1f + (if (isBackground) 0.018f else 0.032f) * swell
    val lift by animateFloatAsState(
        targetValue = if (singing) -5.5f * swell else 0f,
        animationSpec = tween(durationMillis = if (singing) 50 else 350, easing = FastOutSlowInEasing),
        label = "word_lift",
    )
    val glow by animateFloatAsState(
        targetValue = if (singing) swell else 0f,
        animationSpec = tween(durationMillis = if (singing) 60 else 200, easing = FastOutSlowInEasing),
        label = "word_glow",
    )

    // Unlit words on the sung line sit brighter than the rest of the page, so the line reads ahead.
    val restingAlpha = if (isBackground) 0.4f else 0.56f
    val style = lyricStyle(fontSize)

    Box(
        Modifier.graphicsLayer {
            translationY = lift * density
            scaleX = wordScale
            scaleY = wordScale
            // Grow up out of the baseline, the way a struck key rises.
            transformOrigin = TransformOrigin(0.5f, 1f)
        },
    ) {
        Text(word.text, style = style, color = Color.White.copy(alpha = restingAlpha))
        if (complete || singing) {
            Text(
                word.text,
                style = style.copy(
                    shadow = if (glow > 0f) Shadow(Color.White.copy(alpha = glow * 0.5f), Offset.Zero, (glow * 14f).coerceAtLeast(1f)) else null,
                ),
                color = Color.White.copy(alpha = if (isBackground) 0.75f else 1f),
                modifier = if (singing) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            // Feather scales with the word, so short and long words sweep alike.
                            val edge = (size.width * 0.22f).coerceIn(6.dp.toPx(), 22.dp.toPx())
                            val center = (size.width + edge * 2) * progress - edge
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Black,
                                    0.55f to Color.Black.copy(alpha = 0.45f),
                                    1f to Color.Transparent,
                                    startX = center - edge,
                                    endX = center + edge,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                } else {
                    Modifier
                },
            )
        }
    }
}

/** An instrumental break: three dots that breathe while it lasts. */
@Composable
private fun BreathingDots(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { i ->
            val pulse by transition.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700, delayMillis = i * 180, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "dot_$i",
            )
            val s = if (active) pulse else 0.8f
            Box(
                Modifier
                    .size(12.dp)
                    .graphicsLayer {
                        scaleX = s
                        scaleY = s
                        alpha = if (active) pulse else 0.5f
                    }
                    .background(Color.White, CircleShape),
            )
        }
    }
}
