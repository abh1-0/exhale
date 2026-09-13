/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.SongRow
import com.ozyern.exhale.desktop.ui.glass.LocalAppBackdrop
import com.ozyern.exhale.desktop.ui.glass.liquidGlass
import com.ozyern.exhale.desktop.ui.search.SearchModel

/**
 * Home's pinned top bar: a liquid glass search pill that widens when you use it and drops its
 * suggestions and results beneath, and the account button with its menu. Chrome — it floats over
 * Home and bends it.
 */
@Composable
fun HomeTopBar(
    search: SearchModel,
    player: DesktopPlayer,
    state: PlayerState,
    startInset: Dp,
    onOpenSettings: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalAppBackdrop.current ?: return
    var searchOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }

    fun closeAll() {
        searchOpen = false
        accountOpen = false
        focusManager.clearFocus()
    }

    val pillWidth by animateDpAsState(
        if (searchOpen || search.query.isNotEmpty()) 580.dp else 340.dp,
        spring(dampingRatio = 0.78f, stiffness = 380f),
        label = "search_width",
    )

    Box(modifier.fillMaxSize()) {
        // Click anywhere else to dismiss an open dropdown.
        if (searchOpen || accountOpen) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { closeAll() } })
        }

        Row(
            Modifier.fillMaxWidth().padding(start = startInset + 20.dp, end = 28.dp, top = 18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Row(
                    Modifier
                        .width(pillWidth)
                        .height(48.dp)
                        .liquidGlass(RoundedCornerShape(24.dp), backdrop, tintAlpha = 0.42f, blurRadius = 56.dp)
                        .padding(start = 18.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(NavIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (search.query.isEmpty()) {
                            Text("Search songs, artists…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        BasicTextField(
                            value = search.query,
                            onValueChange = {
                                search.updateQuery(it)
                                searchOpen = true
                                accountOpen = false
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus)
                                .onFocusChanged { if (it.isFocused) searchOpen = true }
                                .onPreviewKeyEvent {
                                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (it.key) {
                                        Key.Enter -> {
                                            search.search()
                                            searchOpen = true
                                            true
                                        }
                                        Key.Escape -> {
                                            closeAll()
                                            true
                                        }
                                        else -> false
                                    }
                                },
                        )
                    }
                    AnimatedVisibility(search.query.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).clickable {
                                search.clear()
                                focus.requestFocus()
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                AnimatedVisibility(
                    visible = searchOpen && search.query.isNotBlank(),
                    enter = expandVertically(spring(0.8f, 400f), expandFrom = Alignment.Top) + fadeIn(tween(160)),
                    exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
                ) {
                    SearchDropdown(search, player, state, pillWidth)
                }
            }

            Spacer(Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.End) {
                AccountButton(onClick = {
                    accountOpen = !accountOpen
                    searchOpen = false
                })
                AnimatedVisibility(
                    visible = accountOpen,
                    enter = scaleIn(spring(0.7f, 420f), initialScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)) + fadeIn(tween(140)),
                    exit = scaleOut(tween(140), targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)) + fadeOut(tween(120)),
                ) {
                    AccountMenu(
                        onOpenSettings = {
                            closeAll()
                            onOpenSettings()
                        },
                        onSignIn = {
                            closeAll()
                            onSignIn()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchDropdown(search: SearchModel, player: DesktopPlayer, state: PlayerState, width: Dp) {
    val backdrop = LocalAppBackdrop.current ?: return
    Column(
        Modifier
            .padding(top = 8.dp)
            .width(width)
            .heightIn(max = 500.dp)
            .liquidGlass(RoundedCornerShape(22.dp), backdrop, tintAlpha = 0.55f, blurRadius = 64.dp)
            .padding(8.dp),
    ) {
        when {
            search.searching -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            !search.showingResults && search.suggestions.isNotEmpty() -> search.suggestions.forEach { suggestion ->
                SuggestionRow(suggestion) { search.search(suggestion) }
            }
            search.showingResults && search.error != null -> Text(search.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            search.showingResults && search.results.isEmpty() -> Text("No songs found.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            search.showingResults -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(search.results, key = { i, song -> "$i:${song.id}" }) { index, song ->
                    SongRow(song, isActive = state.current?.id == song.id, onClick = { player.play(search.results, index) })
                }
            }
            else -> Text("Press Enter to search", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun SuggestionRow(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NavIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AccountButton(onClick: () -> Unit) {
    val backdrop = LocalAppBackdrop.current ?: return
    val account by AccountSession.account.collectAsState()
    Box(
        Modifier
            .size(48.dp)
            .liquidGlass(CircleShape, backdrop, tintAlpha = 0.36f, blurRadius = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val avatar = account?.avatar
        if (avatar != null) {
            AsyncImage(avatar, contentDescription = account?.name, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(CircleShape))
        } else {
            Icon(NavIcons.Artists, contentDescription = "Account", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AccountMenu(onOpenSettings: () -> Unit, onSignIn: () -> Unit) {
    val backdrop = LocalAppBackdrop.current ?: return
    val account by AccountSession.account.collectAsState()
    Column(
        Modifier
            .padding(top = 8.dp)
            .width(300.dp)
            .liquidGlass(RoundedCornerShape(22.dp), backdrop, tintAlpha = 0.55f, blurRadius = 64.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val current = account
        if (current != null || AccountSession.isSignedIn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    current?.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(current?.name ?: "Signed in", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    (current?.email ?: current?.handle)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            FilledTonalButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
            OutlinedButton(onClick = { AccountSession.signOut() }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        } else {
            Text("Not signed in", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sign in for your library and a Home built from your account instead of your location.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text("Sign in to YouTube Music") }
            FilledTonalButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
        }
    }
}
