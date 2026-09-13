/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The Android app's MotionTokens: tap physics and emphasized springs. */
object Motion {
    const val PressedScale = 0.92f
    val PressSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 520f)

    /** Home's arrival, from the Android HomeScreen. */
    const val FeedIntroDurationMs = 620
    const val FeedIntroStagger = 0.07f
    const val FeedIntroSlice = 0.55f
    val FeedIntroRise = 16.dp
    const val FeedIntroScale = 0.02f
}

/**
 * One shelf of the page arriving: fades up [Motion.FeedIntroRise] and grows the last two percent
 * into place, offset by its [order] on one shared clock — so the page lands as a page rather than
 * as things popping in. Draw phase only.
 */
fun Modifier.feedIntro(order: Float, progress: () -> Float): Modifier = graphicsLayer {
    val start = (order * Motion.FeedIntroStagger).coerceAtMost(1f - Motion.FeedIntroSlice)
    val raw = ((progress() - start) / Motion.FeedIntroSlice).coerceIn(0f, 1f)
    val eased = FastOutSlowInEasing.transform(raw)
    alpha = eased
    translationY = (1f - eased) * Motion.FeedIntroRise.toPx()
    val grown = 1f - Motion.FeedIntroScale * (1f - eased)
    scaleX = grown
    scaleY = grown
    transformOrigin = TransformOrigin(0.5f, 0f)
}

/** A round control that squishes on press and springs back, with no ripple — glass deforms, it doesn't flash. */
@Composable
fun PressableIcon(
    onClick: () -> Unit,
    size: Dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) Motion.PressedScale else 1f, Motion.PressSpring, label = "press")
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Play and pause morph into each other with a spin-and-scale instead of swapping. */
@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = {
            (scaleIn(spring(0.55f, 420f), initialScale = 0.4f) + fadeIn(tween(120))) togetherWith
                (scaleOut(tween(140), targetScale = 0.4f) + fadeOut(tween(120)))
        },
        label = "play_pause",
    ) { isPlaying ->
        Icon(if (isPlaying) TransportIcons.Pause else TransportIcons.Play, if (isPlaying) "Pause" else "Play", tint = tint, modifier = modifier)
    }
}
