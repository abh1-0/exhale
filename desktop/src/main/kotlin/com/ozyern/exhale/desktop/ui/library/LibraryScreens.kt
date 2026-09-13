/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.ItemCard
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.home.HomeFeed
import com.ozyern.exhale.desktop.ui.home.LargeTitle
import com.ozyern.exhale.desktop.ui.home.SectionHeader
import com.ozyern.exhale.desktop.ui.home.TopPickCard
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.YTItem

/** A titled, adaptive grid of cards — the shape of every library page. */
@Composable
private fun GridPage(
    title: String,
    eyebrow: String?,
    items: List<YTItem>,
    state: PlayerState,
    insets: ChromeInsets,
    emptyText: String,
    loading: Boolean = false,
    onItemClick: (YTItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(184.dp),
        contentPadding = PaddingValues(start = insets.start + 20.dp, end = 40.dp, top = 36.dp, bottom = insets.bottom + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { LargeTitle(title, eyebrow = eyebrow) }
        if (items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp)) {
                    if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterStart))
                    else Text(emptyText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(items, key = { i, item -> "$i:${item.id}" }) { _, item ->
            val active = item.id == state.current?.id || (item is AlbumItem && item.id == state.current?.album?.id)
            ItemCard(item, active, onClick = { onItemClick(item) }, artSize = 184.dp)
        }
    }
}

@Composable
fun NewScreen(feed: HomeFeed, player: DesktopPlayer, state: PlayerState, insets: ChromeInsets) {
    GridPage(
        title = "New",
        eyebrow = "New releases in your region",
        items = feed.releases,
        state = state,
        insets = insets,
        emptyText = "Nothing new yet.",
        loading = feed.isLoading,
        onItemClick = player::playItem,
    )
}

@Composable
fun RecentlyPlayedScreen(player: DesktopPlayer, state: PlayerState, insets: ChromeInsets) {
    val history by DesktopPrefs.history.collectAsState()
    GridPage(
        title = "Recently Played",
        eyebrow = "Library",
        items = history,
        state = state,
        insets = insets,
        emptyText = "Songs you play show up here.",
        onItemClick = player::playItem,
    )
}

@Composable
fun ArtistsScreen(player: DesktopPlayer, state: PlayerState, insets: ChromeInsets) {
    val artists by DesktopPrefs.artists.collectAsState()
    GridPage(
        title = "Artists",
        eyebrow = "Your picks",
        items = artists.map { ArtistItem(id = it.id, title = it.name, thumbnail = it.thumbnail, shuffleEndpoint = null, radioEndpoint = null) },
        state = state,
        insets = insets,
        emptyText = "Pick artists in Your Music Taste.",
        onItemClick = { player.playArtist(it.id) },
    )
}

/** Stations: one per artist you picked, then artists like them. */
@Composable
fun RadioScreen(feed: HomeFeed, player: DesktopPlayer, state: PlayerState, insets: ChromeInsets) {
    val start = insets.start + 20.dp
    val rowPadding = PaddingValues(start = start, end = 40.dp)
    LazyColumn(
        contentPadding = PaddingValues(top = 36.dp, bottom = insets.bottom + 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { LargeTitle("Radio", Modifier.padding(start = start, end = 40.dp)) }
        if (feed.topPicks.isEmpty() && feed.isLoading) {
            item { CircularProgressIndicator(Modifier.padding(start = start, top = 32.dp)) }
        }
        if (feed.topPicks.isNotEmpty()) {
            item { Box(Modifier.padding(start = start, top = 30.dp, bottom = 14.dp)) { SectionHeader(null, "Your Stations") } }
            item {
                LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    itemsIndexed(feed.topPicks, key = { i, pick -> "$i:${pick.item.id}" }) { _, pick ->
                        TopPickCard(pick, isActive = false, onClick = { player.playItem(pick.item) })
                    }
                }
            }
        }
        if (feed.similarArtists.isNotEmpty()) {
            item { Box(Modifier.padding(start = start, top = 30.dp, bottom = 14.dp)) { SectionHeader("Stations", "Artists Like Yours") } }
            item {
                LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    itemsIndexed(feed.similarArtists, key = { i, artist -> "$i:${artist.id}" }) { _, artist ->
                        ItemCard(artist, isActive = false, onClick = { player.playItem(artist) })
                    }
                }
            }
        }
    }
}
