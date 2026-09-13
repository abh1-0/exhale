/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.data.ContentLanguageFilter
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.data.PreferredArtist
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.ItemCard
import com.ozyern.exhale.desktop.ui.Motion
import com.ozyern.exhale.desktop.ui.PlayDisc
import com.ozyern.exhale.desktop.ui.feedIntro
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.desktop.ui.rememberArtworkColors
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YTItem
import com.ozyern.exhale.innertube.models.filterExplicit
import com.ozyern.exhale.innertube.models.filterVideo
import com.ozyern.exhale.innertube.pages.HomePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Content settings: hide explicit items and music videos. */
private fun <T : YTItem> List<T>.clean(): List<T> =
    filterExplicit(DesktopPrefs.hideExplicit).filterVideo(DesktopPrefs.hideVideos)

/** Where the floating chrome sits, so content can clear it. */
data class ChromeInsets(val start: Dp, val bottom: Dp)

/** An Apple-style "Top Picks for You" card: a station built around one of your artists. */
data class TopPick(val eyebrow: String, val title: String, val image: String?, val item: YTItem)

data class Shelf(
    val key: String,
    val label: String?,
    val title: String,
    val items: List<YTItem>,
    val thumbnail: String? = null,
    val roundThumbnail: Boolean = false,
)

/**
 * Home, built around the listener's own picks first — stations and shelves from their artists,
 * artists like them, new releases for their region — and YouTube Music's feed after that.
 */
class HomeFeed(private val scope: CoroutineScope) {
    var topPicks by mutableStateOf<List<TopPick>>(emptyList())
        private set
    var tasteShelves by mutableStateOf<List<Shelf>>(emptyList())
        private set
    var similarArtists by mutableStateOf<List<ArtistItem>>(emptyList())
        private set
    var releases by mutableStateOf<List<AlbumItem>>(emptyList())
        private set
    var page by mutableStateOf<HomePage?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var loadJob: Job? = null

    val isEmpty: Boolean
        get() = topPicks.isEmpty() && tasteShelves.isEmpty() && releases.isEmpty() && page?.sections.isNullOrEmpty()

    fun load(artists: List<PreferredArtist>, force: Boolean = false) {
        if (isLoading && !force) return
        loadJob?.cancel()
        isLoading = true
        error = null
        loadJob = scope.launch {
            try {
                val (pages, home, newReleases) = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val artistPages = artists.take(6).map { artist ->
                            async { artist to YouTube.artist(artist.id).getOrNull() }
                        }
                        val homePage = async { YouTube.home().getOrNull() }
                        val albums = async { YouTube.newReleaseAlbums().getOrNull().orEmpty() }
                        Triple(artistPages.awaitAll(), homePage.await(), albums.await())
                    }
                }

                topPicks = pages.mapNotNull { (artist, artistPage) ->
                    val station = artistPage?.artist ?: return@mapNotNull null
                    TopPick(
                        eyebrow = "Featuring ${artist.name}",
                        title = "${artist.name} & Similar Artists Station",
                        image = station.thumbnail ?: artist.thumbnail,
                        item = station,
                    )
                }

                // A mix from your artists' own radios. Radio follows the seed artist, not the caller's
                // IP, so this is the one YouTube-built shelf the region leak can't reach.
                val mix = withContext(Dispatchers.IO) {
                    coroutineScope {
                        pages.take(3).mapNotNull { it.second?.artist?.radioEndpoint }
                            .map { endpoint -> async { YouTube.next(endpoint).getOrNull()?.items.orEmpty() } }
                            .awaitAll()
                            .flatten()
                    }
                }.distinctBy { it.id }.clean().shuffled().take(30)

                val artistShelves = pages.take(4).mapNotNull { (artist, artistPage) ->
                    val items = artistPage?.sections.orEmpty()
                        .flatMap { it.items }
                        .filter { it is SongItem || it is AlbumItem }
                        .distinctBy { it.id }
                        .clean()
                        .take(18)
                    if (items.isEmpty()) null
                    else Shelf("artist_${artist.id}", "Because you like", artist.name, items, artist.thumbnail, roundThumbnail = true)
                }
                tasteShelves = listOfNotNull(mix.takeIf { it.isNotEmpty() }?.let { Shelf("made_for_you", "Made for you", "Your Mix", it) }) +
                    artistShelves

                // Everything below comes from YouTube's region-aware endpoints, so it is checked
                // against the listener's languages rather than trusted.
                val languages = DesktopPrefs.languages.toSet()
                val pickedIds = artists.mapTo(HashSet()) { it.id }
                similarArtists = ContentLanguageFilter.filterItems(
                    pages.flatMap { (_, artistPage) ->
                        artistPage?.sections.orEmpty().flatMap { it.items }.filterIsInstance<ArtistItem>()
                    }.filterNot { it.id in pickedIds }.distinctBy { it.id },
                    languages,
                ).shuffled().take(18)

                releases = ContentLanguageFilter.filterItems(newReleases.clean(), languages)
                page = home?.let { homePage ->
                    val cleaned = homePage.sections.map { it.copy(items = it.items.clean()) }.filter { it.items.isNotEmpty() }
                    homePage.copy(sections = ContentLanguageFilter.filterHomeSections(cleaned, languages))
                }

                if (isEmpty) error = "Couldn't reach YouTube Music. Check your connection and try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load Home"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMore() {
        val current = page ?: return
        val continuation = current.continuation ?: return
        if (isLoadingMore) return
        isLoadingMore = true
        scope.launch {
            withContext(Dispatchers.IO) { YouTube.home(continuation = continuation) }
                .onSuccess { more ->
                    val kept = ContentLanguageFilter.filterHomeSections(
                        more.sections.map { it.copy(items = it.items.clean()) }.filter { it.items.isNotEmpty() },
                        DesktopPrefs.languages.toSet(),
                    )
                    page = current.copy(sections = current.sections + kept, continuation = more.continuation)
                }
            isLoadingMore = false
        }
    }
}

/** Room at the top of Home for the pinned search/account bar. */
private val TopBarClearance = 96.dp

@Composable
fun HomeScreen(
    feed: HomeFeed,
    player: DesktopPlayer,
    state: PlayerState,
    insets: ChromeInsets,
    onRetry: () -> Unit,
) {
    val history by DesktopPrefs.history.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && last >= total - 3) feed.loadMore() }
    }

    // One clock for the whole page's arrival; every shelf offsets itself on it by its position.
    val intro = remember { Animatable(0f) }
    LaunchedEffect(feed.isEmpty) {
        if (!feed.isEmpty && intro.value == 0f) {
            intro.animateTo(1f, tween(Motion.FeedIntroDurationMs, easing = LinearOutSlowInEasing))
        }
    }
    val introProgress: () -> Float = { intro.value }

    val ytSections = feed.page?.sections.orEmpty()
    // YouTube Music repeats browse ids within one feed; a repeated LazyColumn key throws.
    val ytKeys = remember(ytSections) {
        val seen = HashMap<String, Int>()
        ytSections.mapIndexed { index, section ->
            val base = section.endpoint?.browseId ?: "title_${section.title}_$index"
            val occurrence = (seen[base] ?: 0) + 1
            seen[base] = occurrence
            if (occurrence == 1) base else "${base}__$occurrence"
        }
    }
    val start = insets.start + 20.dp
    val rowPadding = PaddingValues(start = start, end = 40.dp)
    val isActive: (YTItem) -> Boolean = { item ->
        item.id == state.current?.id || (item is AlbumItem && item.id == state.current?.album?.id)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = TopBarClearance, bottom = insets.bottom + 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            var order = 0f
            fun nextIntro(): Modifier = Modifier.feedIntro(order.also { order += 1f }, introProgress)

            item(key = "large_title") {
                LargeTitle(
                    "Home",
                    Modifier
                        .padding(start = start, end = 40.dp, bottom = 4.dp)
                        .graphicsLayer {
                            // Drifts slower than the list, dissolves, and shrinks toward its
                            // bottom-left corner as it goes — Apple's large title, as on Android.
                            val height = size.height.coerceAtLeast(1f)
                            val scrolled = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else height
                            val t = (scrolled / height).coerceIn(0f, 1f)
                            alpha = 1f - t
                            translationY = scrolled * 0.35f
                            val shrink = 1f - 0.16f * t
                            scaleX = shrink
                            scaleY = shrink
                            transformOrigin = TransformOrigin(0f, 1f)
                        },
                )
            }

            if (feed.isLoading && feed.isEmpty) {
                items(count = 3, key = { "skeleton_$it" }) { ShelfPlaceholder(rowPadding, start) }
            }
            if (!feed.isLoading && feed.isEmpty && feed.error != null) {
                item(key = "empty") { EmptyState(feed.error!!, onRetry, Modifier.padding(start = start)) }
            }

            if (feed.topPicks.isNotEmpty()) {
                val intro = nextIntro()
                header("top_picks", start, intro) { SectionHeader(null, "Top Picks for You") }
                item(key = "top_picks_row") {
                    LazyRow(intro, contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        itemsIndexed(feed.topPicks, key = { i, pick -> "$i:${pick.item.id}" }) { index, pick ->
                            // The one shelf that cascades card by card.
                            TopPickCard(
                                pick,
                                isActive = false,
                                onClick = { player.playItem(pick.item) },
                                modifier = Modifier.feedIntro(index * 0.34f, introProgress),
                            )
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                val intro = nextIntro()
                header("recent", start, intro) { SectionHeader(null, "Recently Played") }
                shelfRow("recent_row", history.take(24), rowPadding, intro, isActive) { player.playItem(it) }
            }

            feed.tasteShelves.forEach { shelf ->
                val intro = nextIntro()
                header(shelf.key, start, intro) { SectionHeader(shelf.label, shelf.title, shelf.thumbnail, shelf.roundThumbnail) }
                shelfRow("${shelf.key}_row", shelf.items, rowPadding, intro, isActive) { player.playItem(it) }
            }

            if (feed.similarArtists.isNotEmpty()) {
                val intro = nextIntro()
                header("similar", start, intro) { SectionHeader(null, "Artists You Might Like") }
                shelfRow("similar_row", feed.similarArtists, rowPadding, intro, isActive) { player.playItem(it) }
            }

            if (feed.releases.isNotEmpty()) {
                val intro = nextIntro()
                header("releases", start, intro) { SectionHeader(null, "New Releases") }
                shelfRow("releases_row", feed.releases.take(24), rowPadding, intro, isActive) { player.playItem(it) }
            }

            ytSections.forEachIndexed { index, section ->
                val key = "yt_${ytKeys[index]}"
                val intro = nextIntro()
                header(key, start, intro) {
                    SectionHeader(section.label, section.title, section.thumbnail, section.endpoint?.browseId?.startsWith("UC") == true)
                }
                shelfRow("${key}_row", section.items, rowPadding, intro, isActive) { player.playItem(it) }
            }

            if (feed.isLoadingMore) item(key = "loading_more") { ShelfPlaceholder(rowPadding, start) }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = TopBarClearance, bottom = insets.bottom, end = 6.dp),
        )
    }
}

private fun LazyListScope.header(key: String, start: Dp, intro: Modifier, content: @Composable () -> Unit) {
    item(key = "${key}_header") {
        Box(intro.padding(start = start, end = 40.dp, top = 30.dp, bottom = 14.dp)) { content() }
    }
}

private fun LazyListScope.shelfRow(
    key: String,
    items: List<YTItem>,
    rowPadding: PaddingValues,
    intro: Modifier,
    isActive: (YTItem) -> Boolean,
    onClick: (YTItem) -> Unit,
) {
    item(key = key) {
        LazyRow(intro, contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            itemsIndexed(items, key = { i, item -> "$i:${item.id}" }) { _, item ->
                ItemCard(item, isActive(item), onClick = { onClick(item) })
            }
        }
    }
}

/** A large display title, tracked in tight; the eyebrow is optional (Apple's Home has none). */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, eyebrow: String? = null) {
    Column(modifier.fillMaxWidth()) {
        if (eyebrow != null) {
            Text(eyebrow, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.displaySmall.copy(letterSpacing = (-0.03).em), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SectionHeader(label: String?, title: String, thumbnail: String? = null, roundThumbnail: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        thumbnail?.let { url ->
            AsyncImage(
                model = url.resizedThumbnail(120),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(if (roundThumbnail) CircleShape else RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column {
            label?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(title, style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-0.01).em))
        }
    }
}

/** Apple's Top Picks card: artwork on top, a panel tinted from the artwork carrying the caption. */
@Composable
fun TopPickCard(pick: TopPick, isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors by rememberArtworkColors(pick.image?.resizedThumbnail(120))
    val panel by animateColorAsState(
        lerp(colors.firstOrNull() ?: MaterialTheme.colorScheme.surfaceContainerHigh, Color.Black, 0.35f),
        animationSpec = tween(600),
        label = "top_pick_panel",
    )
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.95f
            hovered -> 1.03f
            else -> 1f
        },
        spring(0.62f, 420f),
        label = "top_pick_scale",
    )
    // The artwork drifts inside its frame on hover — a slow parallax push.
    val push by animateFloatAsState(if (hovered) 1.08f else 1f, spring(0.9f, 120f), label = "top_pick_push")
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier
            .width(236.dp)
            .height(320.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (hovered) 28.dp else 12.dp, shape, clip = false)
            .clip(shape)
            .background(panel)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(236.dp).clip(RoundedCornerShape(0.dp))) {
            AsyncImage(
                model = pick.image?.resizedThumbnail(544),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = push
                        scaleY = push
                    }
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (hovered || isActive) PlayDisc(playing = isActive, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(pick.eyebrow, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(pick.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ShelfPlaceholder(rowPadding: PaddingValues, start: Dp) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeleton_alpha",
    )
    Column(Modifier.graphicsLayer { alpha = pulse }.padding(top = 30.dp)) {
        Box(Modifier.padding(start = start, bottom = 14.dp).width(220.dp).height(26.dp).glassPlate(RoundedCornerShape(10.dp)))
        LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(18.dp), userScrollEnabled = false) {
            items(count = 8) {
                Column {
                    Box(Modifier.size(168.dp).glassPlate(RoundedCornerShape(18.dp)))
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(120.dp).height(14.dp).glassPlate(RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Home didn't load", style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        FilledTonalButton(onClick = onRetry) { Text("Try again") }
    }
}
