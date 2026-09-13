/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YTItem

/** YouTube serves thumbnails at whatever size the URL asks for; the feed asks for tiny ones. */
fun String.resizedThumbnail(px: Int): String =
    replace(Regex("=w\\d+-h\\d+"), "=w$px-h$px").replace(Regex("=s\\d+(?=[-&]|$)"), "=s$px")

fun YTItem.subtitle(): String? = when (this) {
    is SongItem -> artists.joinToString { it.name }
    is AlbumItem -> listOfNotNull(artists?.joinToString { it.name }, year?.toString()).joinToString(" · ")
    is PlaylistItem -> listOfNotNull(author?.name, songCountText).joinToString(" · ")
    is ArtistItem -> subscriberCountText ?: monthlyListenerCountText ?: "Artist"
}.takeIf { !it.isNullOrBlank() }

/** Press squish + hover lift shared by every card, as springs so an interrupted press never snaps. */
@Composable
private fun rememberCardMotion(interaction: MutableInteractionSource): Pair<Float, Boolean> {
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.96f
            hovered -> 1.02f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "card_scale",
    )
    return scale to hovered
}

/** The shelf card: square artwork (round for artists), title, subtitle. */
@Composable
fun ItemCard(
    item: YTItem,
    isActive: Boolean,
    onClick: () -> Unit,
    artSize: Dp = 168.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val (scale, hovered) = rememberCardMotion(interaction)
    val artShape = if (item is ArtistItem) CircleShape else RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .width(artSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(Modifier.size(artSize)) {
            AsyncImage(
                model = item.thumbnail?.resizedThumbnail(544),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(if (hovered) 18.dp else 8.dp, artShape, clip = false)
                    .clip(artShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (isActive) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, artShape) else Modifier,
                    ),
            )
            if (hovered || isActive) {
                PlayDisc(
                    playing = isActive,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.subtitle()?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The Quick picks hero: tall cover with the title set over a scrim, like the Android carousel. */
@Composable
fun HeroCard(song: SongItem, isActive: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val (scale, hovered) = rememberCardMotion(interaction)
    val shape = MaterialTheme.shapes.extraLarge

    Box(
        modifier = Modifier
            .width(250.dp)
            .height(290.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (hovered) 24.dp else 10.dp, shape, clip = false)
            .clip(shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        AsyncImage(
            model = song.thumbnail.resizedThumbnail(720),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.7f))),
            ),
        )
        if (isActive || hovered) {
            PlayDisc(playing = isActive, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun PlayDisc(playing: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .shadow(10.dp, CircleShape)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (playing) {
            EqualizerBars(MaterialTheme.colorScheme.onPrimary, Modifier.size(16.dp))
        } else {
            Icon(TransportIcons.Play, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Three bars bouncing out of step — "this is the one playing". */
@Composable
fun EqualizerBars(color: Color, modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "equalizer")
    val bars = listOf(520, 380, 610).mapIndexed { i, period ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(period, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "bar_$i",
        )
    }
    androidx.compose.foundation.Canvas(modifier) {
        val barWidth = size.width / 5f
        bars.forEachIndexed { i, height ->
            val barHeight = size.height * height.value
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(barWidth * (i * 2f), size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

/** A song row for lists (search results). The current song sits on a glass plate. */
@Composable
fun SongRow(song: SongItem, isActive: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    isActive -> Modifier.glassPlate(shape, base = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    hovered -> Modifier.glassPlate(shape)
                    else -> Modifier.clip(shape)
                },
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.thumbnail.resizedThumbnail(226),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                song.artists.joinToString { it.name },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        song.album?.let {
            Text(
                it.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.6f).padding(horizontal = 16.dp),
            )
        }
        song.duration?.let {
            Text(formatTime(it.toDouble()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun formatTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
