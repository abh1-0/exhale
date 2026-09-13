/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** End to end: search → innertube player → NewPipe decipher → libmpv actually advancing. */
class PlaybackSmokeTest {
    @Test
    fun syncedLyricsForKnownSong() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        val song = com.ozyern.exhale.innertube.models.SongItem(
            id = "kIft-LUHHVA",
            title = "Espresso",
            artists = listOf(com.ozyern.exhale.innertube.models.Artist("Sabrina Carpenter", null)),
            duration = 176,
            thumbnail = "",
        )
        val lyrics = com.ozyern.exhale.desktop.lyrics.LyricsRepository.lyricsFor(song, 176)
        val synced = lyrics as? com.ozyern.exhale.desktop.lyrics.Lyrics.Synced
        val firstSung = synced?.lines?.firstOrNull { it.text.isNotBlank() }
        println("lyrics: ${lyrics::class.simpleName} ${synced?.source}, ${synced?.lines?.size} lines, wordLevel=${synced?.wordLevel}")
        println("first line: ${firstSung?.text} -> ${firstSung?.words?.joinToString(" | ") { "${it.text.trim()}@${it.startMs}-${it.endMs}" }}")
        assertTrue("expected synced lyrics, got $lyrics", synced != null)
        // Every sung line must carry word timings — real or synthesized — for the karaoke fill.
        assertTrue("a sung line has no word timings", synced!!.lines.filter { it.text.isNotBlank() }.all { it.words.isNotEmpty() })
    }

    @Test
    fun searchResolveAndPlay() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()

        val song = YouTube.search("Sabrina Carpenter Espresso", YouTube.SearchFilter.FILTER_SONG)
            .getOrThrow().items.filterIsInstance<SongItem>().first()
        println("song: ${song.title} (${song.id})")

        val stream = StreamResolver.resolve(song.id).getOrThrow()
        println("stream: ${stream.describe()}")

        val loaded = CountDownLatch(1)
        val position = AtomicReference(0.0)
        val error = AtomicReference<String?>(null)

        Mpv(object : Mpv.Listener {
            override fun onFileLoaded() = loaded.countDown()
            override fun onPositionChanged(seconds: Double) = position.set(seconds)
            override fun onError(message: String) {
                error.set(message)
                loaded.countDown()
            }
        }).use { mpv ->
            mpv.setVolume(0)
            mpv.load(stream.url, stream.headers)
            assertTrue("file never loaded", loaded.await(30, TimeUnit.SECONDS))
            assertNull(error.get())
            Thread.sleep(5_000)
            println("position after 5s: ${position.get()}")
            assertTrue("playback did not advance", position.get() > 2.0)
        }
    }
}
