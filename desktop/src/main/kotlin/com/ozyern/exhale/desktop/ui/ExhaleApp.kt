/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.exhale.desktop.appIcon
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.data.ContentLocale
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.account.SignInWindow
import com.ozyern.exhale.desktop.ui.glass.LocalAppBackdrop
import com.ozyern.exhale.desktop.ui.glass.PageBackdropHost
import com.ozyern.exhale.desktop.ui.glass.liquidGlass
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.home.HomeFeed
import com.ozyern.exhale.desktop.ui.home.HomeScreen
import com.ozyern.exhale.desktop.ui.home.HomeTopBar
import com.ozyern.exhale.desktop.ui.library.ArtistsScreen
import com.ozyern.exhale.desktop.ui.library.NewScreen
import com.ozyern.exhale.desktop.ui.library.RadioScreen
import com.ozyern.exhale.desktop.ui.library.RecentlyPlayedScreen
import com.ozyern.exhale.desktop.ui.onboarding.OnboardingScreen
import com.ozyern.exhale.desktop.ui.player.FullPlayer
import com.ozyern.exhale.desktop.ui.player.ScrubBar
import com.ozyern.exhale.desktop.ui.search.SearchModel
import com.ozyern.exhale.desktop.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Destination(val label: String, val icon: () -> ImageVector) {
    Home("Home", { NavIcons.Home }),
    New("New", { NavIcons.New }),
    Radio("Radio", { NavIcons.Radio }),
    Recent("Recent", { NavIcons.Recent }),
    Artists("Artists", { NavIcons.Artists }),
    Settings("Settings", { NavIcons.Settings }),
}

private val RailReservedWidth = 104.dp
private val RailItemHeight = 56.dp
private val RailItemSpacing = 6.dp

@Composable
fun ExhaleApp(player: DesktopPlayer) {
    val scope = rememberCoroutineScope()
    val state by player.state.collectAsState()
    val artists by DesktopPrefs.artists.collectAsState()
    val history by DesktopPrefs.history.collectAsState()
    val home = remember { HomeFeed(scope) }
    val search = remember { SearchModel(scope) }
    var destination by remember { mutableStateOf(Destination.Home) }
    var onboarding by remember { mutableStateOf(!DesktopPrefs.onboardingCompleted) }
    var editingTaste by remember { mutableStateOf(false) }
    var fullPlayer by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    var signingIn by remember { mutableStateOf(false) }

    fun reloadHome(force: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) { ContentLocale.ensureVisitorData() }
            home.load(DesktopPrefs.artists.value, force)
        }
    }

    LaunchedEffect(Unit) {
        if (DesktopPrefs.onboardingCompleted) reloadHome(force = false)
        AccountSession.refreshAccountInfo()
    }

    if (signingIn) {
        SignInWindow(
            onClose = { signingIn = false },
            onSignedIn = {
                signingIn = false
                reloadHome(force = true)
            },
        )
    }

    if (onboarding || editingTaste) {
        OnboardingScreen(
            initialLanguages = DesktopPrefs.languages,
            initialArtists = artists,
            onFinish = { languages, picks ->
                DesktopPrefs.languages = languages
                DesktopPrefs.setArtists(picks)
                DesktopPrefs.onboardingCompleted = true
                ContentLocale.apply(languages)
                onboarding = false
                editingTaste = false
                destination = Destination.Home
                reloadHome(force = true)
            },
            onCancel = if (editingTaste) ({ editingTaste = false }) else null,
        )
        return
    }

    // The room is lit by what's playing, or by the first thing on Home before anything is.
    val ambientUrl = state.current?.thumbnail ?: home.topPicks.firstOrNull()?.image ?: history.firstOrNull()?.thumbnail
    val ambientColors by rememberArtworkColors(ambientUrl?.resizedThumbnail(120))

    val insets = ChromeInsets(start = RailReservedWidth, bottom = if (state.current != null) 104.dp else 24.dp)
    val appBackdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        // Everything the chrome floats over is recorded here, so the rail, top bar and mini player
        // bend real pixels. They are siblings drawn *after* this box, never inside it.
        Box(Modifier.fillMaxSize().layerBackdrop(appBackdrop)) {
            PageBackdropHost(
                modifier = Modifier.fillMaxSize(),
                background = {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    AmbientGlow(ambientColors, Modifier.fillMaxSize())
                },
            ) {
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        (fadeIn(tween(260, delayMillis = 60)) +
                            scaleIn(tween(360), initialScale = 0.975f) +
                            slideInVertically(spring(0.85f, 300f)) { 24 }) togetherWith
                            fadeOut(tween(140))
                    },
                    label = "destination",
                ) { current ->
                    when (current) {
                        Destination.Home -> HomeScreen(home, player, state, insets, onRetry = { reloadHome(force = true) })
                        Destination.New -> NewScreen(home, player, state, insets)
                        Destination.Radio -> RadioScreen(home, player, state, insets)
                        Destination.Recent -> RecentlyPlayedScreen(player, state, insets)
                        Destination.Artists -> ArtistsScreen(player, state, insets)
                        Destination.Settings -> SettingsScreen(
                            insets = insets,
                            onSignIn = { signingIn = true },
                            onEditTaste = { editingTaste = true },
                            onHomeInputsChanged = { reloadHome(force = true) },
                        )
                    }
                }
            }
        }

        CompositionLocalProvider(LocalAppBackdrop provides appBackdrop) {
            NavRail(
                selected = destination,
                onSelect = { destination = it },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
            )

            AnimatedVisibility(
                visible = state.current != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spring(0.7f, 260f)) { it * 2 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(220)) { it * 2 } + fadeOut(tween(160)),
            ) {
                MiniPlayer(
                    state = state,
                    player = player,
                    onOpen = {
                        lyricsOpen = false
                        fullPlayer = true
                    },
                    onOpenLyrics = {
                        lyricsOpen = true
                        fullPlayer = true
                    },
                    modifier = Modifier.padding(start = RailReservedWidth, end = 24.dp, bottom = 18.dp),
                )
            }

            AnimatedVisibility(
                visible = destination == Destination.Home,
                enter = fadeIn(tween(220)) + slideInVertically(spring(0.8f, 320f)) { -it / 12 },
                exit = fadeOut(tween(120)),
            ) {
                HomeTopBar(
                    search = search,
                    player = player,
                    state = state,
                    startInset = RailReservedWidth,
                    onOpenSettings = { destination = Destination.Settings },
                    onSignIn = { signingIn = true },
                )
            }
        }

        AnimatedVisibility(
            visible = fullPlayer && state.current != null,
            enter = fadeIn(tween(220)) + slideInVertically(spring(0.82f, 240f)) { it / 8 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(260)) { it / 8 },
        ) {
            FullPlayer(
                state = state,
                player = player,
                showLyrics = lyricsOpen,
                onToggleLyrics = { lyricsOpen = !lyricsOpen },
                onClose = { fullPlayer = false },
            )
        }
    }
}

/**
 * The floating glass capsule. The selection is one pill that slides between items on a spring —
 * like the Android dock's lens — rather than each item lighting up on its own.
 */
@Composable
private fun NavRail(selected: Destination, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier) {
    val backdrop = LocalAppBackdrop.current ?: return
    val index = Destination.entries.indexOf(selected)
    val pillOffset by animateDpAsState((RailItemHeight + RailItemSpacing) * index, spring(dampingRatio = 0.66f, stiffness = 380f), label = "rail_pill")
    // The pill stretches while it travels and settles back to size when it lands.
    val travel = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(index) {
        travel.snapTo(1f)
        travel.animateTo(0f, spring(0.5f, 260f))
    }

    Column(
        modifier = modifier
            .width(72.dp)
            .liquidGlass(RoundedCornerShape(36.dp), backdrop, tintAlpha = 0.36f, blurRadius = 56.dp)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(appIcon, contentDescription = "Exhale", modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Box {
            Box(
                Modifier
                    .offset(x = 10.dp, y = pillOffset)
                    .size(width = 52.dp, height = 34.dp)
                    .graphicsLayer {
                        scaleY = 1f + 0.22f * travel.value
                        scaleX = 1f - 0.08f * travel.value
                    }
                    .clip(RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(RailItemSpacing)) {
                Destination.entries.forEach { destination ->
                    RailItem(destination.label, destination.icon(), destination == selected, onClick = { onSelect(destination) })
                }
            }
        }
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val squish by animateFloatAsState(if (pressed) 0.86f else if (hovered) 1.06f else 1f, spring(0.6f, 520f), label = "rail_press")
    val tint by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "rail_tint",
    )

    Column(
        Modifier
            .width(72.dp)
            .height(RailItemHeight)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 34.dp)
                .graphicsLayer {
                    scaleX = squish
                    scaleY = squish
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Apple Music's floating mini player: transport left, now playing centre, extras right. */
@Composable
private fun MiniPlayer(
    state: PlayerState,
    player: DesktopPlayer,
    onOpen: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.current ?: return
    val backdrop = LocalAppBackdrop.current ?: return
    val duration = state.durationSeconds.coerceAtLeast(0.0)

    Row(
        modifier = modifier
            .widthIn(max = 900.dp)
            .fillMaxWidth()
            .height(68.dp)
            .liquidGlass(RoundedCornerShape(34.dp), backdrop, tintAlpha = 0.44f, blurRadius = 56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PressableIcon(onClick = player::previous, size = 38.dp) {
            Icon(TransportIcons.Previous, "Previous", modifier = Modifier.size(22.dp))
        }
        PressableIcon(onClick = player::togglePlayPause, size = 44.dp) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                PlayPauseIcon(state.isPlaying, MaterialTheme.colorScheme.onSurface, Modifier.size(28.dp))
            }
        }
        PressableIcon(onClick = player::next, size = 38.dp, enabled = state.hasNext) {
            Icon(TransportIcons.Next, "Next", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.hasNext) 1f else 0.4f), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))

        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A new song slides in over the old one instead of the text just changing.
            AnimatedContent(
                targetState = song,
                transitionSpec = {
                    (slideInVertically(spring(0.8f, 320f)) { it / 2 } + fadeIn(tween(220))) togetherWith
                        (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(140)))
                },
                contentKey = { it.id },
                modifier = Modifier.weight(1f),
                label = "mini_song",
            ) { current ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = current.thumbnail.resizedThumbnail(226),
                        contentDescription = "Open player",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            state.error ?: listOfNotNull(current.artists.joinToString { it.name }.ifBlank { null }, current.album?.name).joinToString(" — "),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ScrubBar(
                            position = state.positionSeconds,
                            duration = duration,
                            onSeek = player::seekTo,
                            enabled = !state.isLoading,
                            trackColor = Color.White.copy(alpha = 0.14f),
                            fillColor = Color.White.copy(alpha = 0.75f),
                            restHeight = 3.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(10.dp))
        IconButton(onClick = onOpenLyrics, modifier = Modifier.size(36.dp)) {
            Icon(TransportIcons.Lyrics, "Lyrics", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = player::toggleMute, modifier = Modifier.size(36.dp)) {
            Icon(
                if (state.volume == 0) TransportIcons.VolumeOff else TransportIcons.VolumeUp,
                "Mute",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Slider(
            value = state.volume / 100f,
            onValueChange = { player.setVolume((it * 100).toInt()) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.8f),
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
            modifier = Modifier.width(96.dp).height(24.dp),
        )
    }
}
