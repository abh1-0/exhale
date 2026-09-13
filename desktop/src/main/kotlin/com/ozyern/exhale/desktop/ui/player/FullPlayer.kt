/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.lyrics.Lyrics
import com.ozyern.exhale.desktop.lyrics.LyricsRepository
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.AmbientGlow
import com.ozyern.exhale.desktop.ui.PlayPauseIcon
import com.ozyern.exhale.desktop.ui.PressableIcon
import com.ozyern.exhale.desktop.ui.TransportIcons
import com.ozyern.exhale.desktop.ui.formatTime
import com.ozyern.exhale.desktop.ui.rememberArtworkColors
import com.ozyern.exhale.desktop.ui.resizedThumbnail

/**
 * A thin Apple-style progress line: click anywhere to jump, drag to scrub. Thickens under the
 * pointer so it's easy to hit without being heavy at rest.
 */
@Composable
fun ScrubBar(
    position: Double,
    duration: Double,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackColor: Color = Color.White.copy(alpha = 0.2f),
    fillColor: Color = Color.White.copy(alpha = 0.9f),
    restHeight: Dp = 4.dp,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val barHeight by animateDpAsState(if (hovered || dragFraction != null) restHeight + 3.dp else restHeight, spring(0.6f, 500f), label = "scrub_height")
    val active = enabled && duration > 0
    val fraction = dragFraction ?: if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .height(16.dp)
            .hoverable(interaction)
            .pointerInput(active, duration) {
                if (!active) return@pointerInput
                detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f) * duration) }
            }
            .pointerInput(active, duration) {
                if (!active) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let { onSeek(it * duration) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(50)).background(trackColor))
        Box(Modifier.fillMaxWidth(fraction).height(barHeight).clip(RoundedCornerShape(50)).background(fillColor))
    }
}

/**
 * Apple Music's full-screen player: the cover blurred into the whole window, a large sharp copy
 * that springs in and settles back when paused, and — with lyrics on — word-by-word karaoke beside
 * it. Esc closes, Space plays/pauses, L toggles lyrics.
 */
@Composable
fun FullPlayer(
    state: PlayerState,
    player: DesktopPlayer,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    onClose: () -> Unit,
) {
    val song = state.current ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val colors by rememberArtworkColors(song.thumbnail.resizedThumbnail(120))
    val duration = state.durationSeconds.coerceAtLeast(0.0)
    val position = state.positionSeconds.coerceIn(0.0, duration.takeIf { it > 0 } ?: Double.MAX_VALUE)
    val smoothPosition = rememberSmoothPositionMs(state)

    val lyrics by produceState<Lyrics?>(initialValue = null, song.id) {
        value = null
        value = LyricsRepository.lyricsFor(song, state.durationSeconds.toInt())
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF121014))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.Escape -> { onClose(); true }
                    Key.Spacebar -> { player.togglePlayPause(); true }
                    Key.L -> { onToggleLyrics(); true }
                    else -> false
                }
            }
            // Swallow clicks so nothing underneath reacts.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Crossfade(targetState = song.thumbnail, animationSpec = tween(700), label = "backdrop") { thumbnail ->
            AsyncImage(
                model = thumbnail.resizedThumbnail(544),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.9f,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.35f
                        scaleY = 1.35f
                    }
                    .blur(110.dp, BlurredEdgeTreatment.Rectangle),
            )
        }
        AmbientGlow(colors, Modifier.fillMaxSize(), intensity = 0.7f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.62f)))))

        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(TransportIcons.Collapse, contentDescription = "Close player", tint = Color.White.copy(alpha = 0.8f))
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
            val artWide = minOf(maxWidth * 0.5f, maxHeight - 260.dp).coerceIn(180.dp, 520.dp)
            val artSplit = minOf(maxWidth * 0.34f, maxHeight - 260.dp).coerceIn(180.dp, 440.dp)
            val art by animateDpAsState(if (showLyrics) artSplit else artWide, spring(0.8f, 220f), label = "art_size")
            val lyricsWeight by animateFloatAsState(if (showLyrics) 1.15f else 0.0001f, spring(0.85f, 200f), label = "lyrics_weight")

            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    NowPlayingStack(state, player, art, showLyrics, onToggleLyrics, position, duration)
                }
                if (lyricsWeight > 0.01f) {
                    Spacer(Modifier.width(48.dp * (lyricsWeight / 1.15f)))
                    LyricsPanel(
                        lyrics = lyrics,
                        position = smoothPosition,
                        onSeek = player::seekTo,
                        modifier = Modifier
                            .weight(lyricsWeight)
                            .fillMaxHeight()
                            .graphicsLayer { alpha = (lyricsWeight / 1.15f).coerceIn(0f, 1f) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingStack(
    state: PlayerState,
    player: DesktopPlayer,
    art: Dp,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    position: Double,
    duration: Double,
) {
    val song = state.current ?: return
    // The cover springs up as the player opens, and sits back when the music stops — Apple's cue
    // that you are looking at a paused song.
    val appear = remember { Animatable(0.86f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 200f)) }
    val playing = state.isPlaying || state.isLoading
    val restScale by animateFloatAsState(if (playing) 1f else 0.84f, spring(0.55f, 240f), label = "art_rest")
    val shadow by animateDpAsState(if (playing) 44.dp else 14.dp, spring(0.8f, 240f), label = "art_shadow")

    Column(Modifier.width(maxOf(art, 400.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(art)
                .graphicsLayer {
                    scaleX = appear.value * restScale
                    scaleY = appear.value * restScale
                }
                .shadow(shadow, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Crossfade(targetState = song.thumbnail, animationSpec = tween(450), label = "artwork") { thumbnail ->
                AsyncImage(
                    model = thumbnail.resizedThumbnail(1200),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(song.artists.joinToString { it.name }.ifBlank { null }, song.album?.name).joinToString(" — "),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        ScrubBar(position, duration, player::seekTo, Modifier.fillMaxWidth(), enabled = !state.isLoading)
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(position), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.weight(1f))
            Text("-" + formatTime(duration - position), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = player::toggleMute) {
                Icon(if (state.volume == 0) TransportIcons.VolumeOff else TransportIcons.VolumeUp, "Mute", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            PressableIcon(onClick = player::previous, size = 52.dp) {
                Icon(TransportIcons.Previous, "Previous", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            PressableIcon(onClick = player::togglePlayPause, size = 64.dp) {
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(30.dp), color = Color.White, strokeWidth = 2.5.dp)
                } else {
                    PlayPauseIcon(state.isPlaying, Color.White, Modifier.size(44.dp))
                }
            }
            PressableIcon(onClick = player::next, size = 52.dp, enabled = state.hasNext) {
                Icon(TransportIcons.Next, "Next", tint = Color.White.copy(alpha = if (state.hasNext) 1f else 0.35f), modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleLyrics) {
                val lit by animateFloatAsState(if (showLyrics) 1f else 0f, label = "lyrics_button")
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.2f * lit)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(TransportIcons.Lyrics, "Lyrics", tint = Color.White.copy(alpha = 0.7f + 0.3f * lit), modifier = Modifier.size(20.dp))
                }
            }
        }
        Row(Modifier.width(300.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(TransportIcons.VolumeDown, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(16.dp))
            Slider(
                value = state.volume / 100f,
                onValueChange = { player.setVolume((it * 100).toInt()) },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White.copy(alpha = 0.85f), inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(24.dp),
            )
            Icon(TransportIcons.VolumeUp, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(16.dp))
        }
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
}
