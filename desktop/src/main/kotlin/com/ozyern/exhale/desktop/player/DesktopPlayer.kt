/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.innertube.models.YTItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerState(
    val queue: List<SongItem> = emptyList(),
    val index: Int = -1,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val streamInfo: String? = null,
    val error: String? = null,
    val volume: Int = 100,
) {
    val current: SongItem? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = index + 1 < queue.size
    val hasPrevious: Boolean get() = index > 0
}

/** Queue + transport on top of [Mpv]. [scope] must run on the UI dispatcher. */
class DesktopPlayer(private val scope: CoroutineScope) : AutoCloseable {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var queueJob: Job? = null

    private val mpv = Mpv(object : Mpv.Listener {
        // load() always unpauses, and mpv only reports pause when it *changes* — so a song that
        // starts while already unpaused never sends one. Without this the UI showed "paused" over
        // a playing song, and the button then asked mpv to unpause something already playing.
        override fun onFileLoaded() = _state.update { it.copy(isLoading = false, isPlaying = true) }
        override fun onEndOfFile() {
            scope.launch { next() }
        }
        override fun onError(message: String) =
            _state.update { it.copy(isLoading = false, isPlaying = false, error = message) }
        override fun onPositionChanged(seconds: Double) = _state.update { it.copy(positionSeconds = seconds) }
        override fun onDurationChanged(seconds: Double) = _state.update { it.copy(durationSeconds = seconds) }
        override fun onPauseChanged(paused: Boolean) =
            _state.update { it.copy(isPlaying = !paused && it.current != null) }
    })

    fun play(queue: List<SongItem>, index: Int) {
        val song = queue.getOrNull(index) ?: return
        loadJob?.cancel()
        mpv.stop()
        _state.value = PlayerState(
            queue = queue,
            index = index,
            isLoading = true,
            volume = _state.value.volume,
            durationSeconds = song.duration?.toDouble() ?: 0.0,
        )
        loadJob = scope.launch {
            StreamResolver.resolve(song.id)
                .onSuccess { stream ->
                    ensureActive()
                    _state.update { it.copy(streamInfo = stream.describe()) }
                    DesktopPrefs.recordPlayed(song)
                    mpv.load(stream.url, stream.headers)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: error::class.simpleName)
                    }
                }
        }
    }

    /**
     * Play anything a home shelf shows. A song starts at once and its radio fills the queue in
     * behind it; albums, playlists and artists start from the queue YouTube hands back.
     */
    fun playItem(item: YTItem) {
        val endpoint = when (item) {
            is SongItem -> item.endpoint ?: WatchEndpoint(videoId = item.id)
            is AlbumItem -> WatchEndpoint(playlistId = item.playlistId)
            is PlaylistItem -> item.playEndpoint ?: WatchEndpoint(playlistId = item.id.removePrefix("VL"))
            is ArtistItem -> item.radioEndpoint ?: item.shuffleEndpoint ?: item.playEndpoint ?: return
        }
        if (item is SongItem) play(listOf(item), 0)

        queueJob?.cancel()
        queueJob = scope.launch {
            withContext(Dispatchers.IO) { YouTube.next(endpoint) }
                .onSuccess { result ->
                    if (result.items.isEmpty()) return@onSuccess
                    if (item is SongItem) {
                        // Only if the user hasn't moved on while the radio was loading.
                        if (_state.value.current?.id != item.id) return@onSuccess
                        val index = result.items.indexOfFirst { it.id == item.id }
                        val queue = if (index >= 0) result.items else listOf(item) + result.items
                        _state.update { it.copy(queue = queue, index = index.coerceAtLeast(0)) }
                    } else {
                        play(result.items, result.currentIndex ?: 0)
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (item !is SongItem) _state.update { it.copy(error = error.message ?: "Couldn't start ${item.title}") }
                }
        }
    }

    /** An artist's station, for places that only know the artist's id. */
    fun playArtist(browseId: String) {
        queueJob?.cancel()
        queueJob = scope.launch {
            withContext(Dispatchers.IO) { YouTube.artist(browseId) }
                .onSuccess { page -> playItem(page.artist) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update { it.copy(error = error.message ?: "Couldn't start that artist") }
                }
        }
    }

    fun togglePlayPause() {
        val current = _state.value
        if (current.current == null || current.isLoading) return
        mpv.togglePause()
    }

    fun seekTo(seconds: Double) {
        if (_state.value.current == null) return
        mpv.seekTo(seconds)
        _state.update { it.copy(positionSeconds = seconds) }
    }

    fun next() {
        val current = _state.value
        if (current.hasNext) {
            play(current.queue, current.index + 1)
        } else {
            _state.update { it.copy(isPlaying = false, positionSeconds = 0.0) }
        }
    }

    fun previous() {
        val current = _state.value
        // Like every music player: past the first few seconds, "previous" restarts the song.
        if (current.positionSeconds > 3.0 || !current.hasPrevious) {
            seekTo(0.0)
        } else {
            play(current.queue, current.index - 1)
        }
    }

    private var volumeBeforeMute = 100

    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        mpv.setVolume(clamped)
        _state.update { it.copy(volume = clamped) }
    }

    fun toggleMute() {
        val current = _state.value.volume
        if (current > 0) {
            volumeBeforeMute = current
            setVolume(0)
        } else {
            setVolume(volumeBeforeMute.coerceAtLeast(10))
        }
    }

    override fun close() {
        loadJob?.cancel()
        mpv.close()
    }
}
