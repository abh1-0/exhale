/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.lyrics

import com.ozyern.exhale.betterlyrics.BetterLyrics
import com.ozyern.exhale.betterlyrics.TTMLParser
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.kugou.KuGou
import com.ozyern.exhale.lrclib.LrcLib
import com.ozyern.exhale.simpmusic.SimpMusicLyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class LyricWord(val text: String, val startMs: Long, val endMs: Long, val isBackground: Boolean = false)

/**
 * One line. [words] is always filled for synced lyrics: real per-word timings when the provider
 * has them ([wordSynced]), otherwise spread across the line by character count — the same
 * synthesis the Android app does, so every synced song animates word by word.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val wordSynced: Boolean = false,
)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>, val source: String, val wordLevel: Boolean) : Lyrics
    data class Plain(val lines: List<String>, val source: String) : Lyrics
    data object NotFound : Lyrics
}

/**
 * Lyrics for a song from the Android app's providers. BetterLyrics (TTML, word-timed) and
 * SimpMusic (rich sync) lead because they carry real word timings; LRCLIB and KuGou follow.
 */
object LyricsRepository {
    val providerNames = listOf("BetterLyrics", "SimpMusic", "LRCLIB", "KuGou")

    private val cache = object : LinkedHashMap<String, Lyrics>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>) = size > 50
    }

    /** After a provider is switched on or off, so a cached "not found" doesn't stick. */
    fun clearCache() = synchronized(cache) { cache.clear() }

    suspend fun lyricsFor(song: SongItem, fallbackDurationSeconds: Int): Lyrics = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[song.id] }?.let { return@withContext it }

        val title = song.title
        val artist = song.artists.joinToString { it.name }
        val duration = song.duration ?: fallbackDurationSeconds
        val providers = listOf<Pair<String, suspend () -> Result<String>>>(
            "BetterLyrics" to { BetterLyrics.getLyrics(title = title, artist = artist, album = null, durationSeconds = duration) },
            "SimpMusic" to {
                runCatching {
                    val tracks = SimpMusicLyrics.getLyricsByVideoId(song.id)
                    val best = if (duration > 0 && tracks.size > 1) tracks.minByOrNull { abs((it.duration ?: 0) - duration) } else tracks.firstOrNull()
                    best?.richSyncLyrics?.takeIf { it.isNotBlank() } ?: best?.syncedLyrics ?: best?.plainLyrics
                        ?: error("Lyrics unavailable")
                }
            },
            "LRCLIB" to { LrcLib.getLyrics(title, artist, duration, song.album?.name) },
            "KuGou" to { KuGou.getLyrics(title, artist, duration) },
        )

        var plain: Lyrics.Plain? = null
        for ((name, fetch) in providers.filter { DesktopPrefs.isLyricsProviderEnabled(it.first) }) {
            val raw = try {
                fetch().getOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }?.takeIf { it.isNotBlank() } ?: continue

            val lines = LyricsParser.parse(raw)
            if (lines.count { it.text.isNotBlank() } >= 2) {
                val result = Lyrics.Synced(lines, name, wordLevel = lines.any { it.wordSynced })
                synchronized(cache) { cache[song.id] = result }
                return@withContext result
            }
            if (plain == null && !raw.trimStart().startsWith("<")) {
                val text = raw.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("[") }
                if (text.size >= 2) plain = Lyrics.Plain(text, name)
            }
        }
        (plain ?: Lyrics.NotFound).also { synchronized(cache) { cache[song.id] = it } }
    }
}

object LyricsParser {
    private val lineTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTag = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

    fun parse(raw: String): List<LyricLine> {
        val trimmed = raw.trim()
        val isTtml = trimmed.startsWith("<") && (trimmed.contains("<tt", ignoreCase = true) || trimmed.contains("ns/ttml"))
        val lines = if (isTtml) parseTtml(trimmed) else parseLrc(trimmed)
        return withWordTimings(lines.sortedBy { it.timeMs })
    }

    /** Index of the line being sung at [positionMs], or -1 before the first line. */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long, leadMs: Long = 250): Int {
        val target = positionMs + leadMs
        var low = 0
        var high = lines.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= target) low = mid + 1 else high = mid - 1
        }
        return high
    }

    private fun parseTtml(ttml: String): List<LyricLine> = runCatching {
        TTMLParser.parseTTML(ttml).map { line ->
            val words = line.words.filter { it.text.isNotEmpty() }.map {
                LyricWord(it.text, (it.startTime * 1000).toLong(), (it.endTime * 1000).toLong(), it.isBackground)
            }
            LyricLine((line.startTime * 1000).toLong(), line.text, words, wordSynced = words.isNotEmpty())
        }
    }.getOrDefault(emptyList())

    private fun stampMs(min: String, sec: String, frac: String): Long {
        val fraction = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + fraction
    }

    /** LRC, including enhanced LRC whose `<mm:ss.xx>` tags time each word. */
    private fun parseLrc(raw: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim()
            var cursor = 0
            val stamps = ArrayList<Long>()
            while (true) {
                val match = lineTag.matchAt(line, cursor) ?: break
                val (min, sec, frac) = match.destructured
                stamps += stampMs(min, sec, frac)
                cursor = match.range.last + 1
            }
            if (stamps.isEmpty()) continue
            val rest = line.substring(cursor)

            val tags = wordTag.findAll(rest).toList()
            val words = if (tags.isEmpty()) emptyList() else tags.mapIndexedNotNull { i, tag ->
                val (min, sec, frac) = tag.destructured
                val textEnd = tags.getOrNull(i + 1)?.range?.first ?: rest.length
                val text = rest.substring(tag.range.last + 1, textEnd)
                if (text.isBlank() && text.isNotEmpty().not()) return@mapIndexedNotNull null
                val next = tags.getOrNull(i + 1)?.destructured?.let { (m, s, f) -> stampMs(m, s, f) }
                LyricWord(text, stampMs(min, sec, frac), next ?: -1L)
            }.filter { it.text.isNotEmpty() }
            val text = wordTag.replace(rest, "").replace(Regex("\\s+"), " ").trim()
            stamps.forEach { out += LyricLine(it, text, words, wordSynced = words.isNotEmpty()) }
        }
        return out
    }

    private fun isCjk(c: Char): Boolean =
        c.code in 0x3040..0x30FF || c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF || c.code in 0xAC00..0xD7AF

    /** Closes open-ended word timings, and spreads line-only lyrics across their words by length. */
    private fun withWordTimings(lines: List<LyricLine>): List<LyricLine> = lines.mapIndexed { index, line ->
        val nextTime = lines.getOrNull(index + 1)?.timeMs ?: (line.timeMs + 5_000)
        if (line.words.isNotEmpty()) {
            val fixed = line.words.mapIndexed { i, word ->
                if (word.endMs > word.startMs) word
                else word.copy(endMs = line.words.getOrNull(i + 1)?.startMs?.takeIf { it > word.startMs } ?: minOf(nextTime, word.startMs + 1_200))
            }
            return@mapIndexed line.copy(words = fixed)
        }
        if (line.text.isBlank()) return@mapIndexed line

        // Capped so an instrumental gap after the line doesn't stretch its last word over it.
        val lineDuration = (nextTime - line.timeMs).coerceIn(500, 10_000)
        val cjk = line.text.any(::isCjk)
        val tokens: List<String> = if (cjk) {
            buildList {
                val latin = StringBuilder()
                line.text.forEach { c ->
                    when {
                        isCjk(c) -> {
                            if (latin.isNotEmpty()) add(latin.toString()).also { latin.clear() }
                            add(c.toString())
                        }
                        c.isWhitespace() -> {
                            if (latin.isNotEmpty()) add(latin.toString()).also { latin.clear() }
                            if (isNotEmpty()) this[lastIndex] = last() + c
                        }
                        else -> latin.append(c)
                    }
                }
                if (latin.isNotEmpty()) add(latin.toString())
            }
        } else {
            line.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        }
        if (tokens.isEmpty()) return@mapIndexed line

        val totalChars = tokens.sumOf { it.trim().length }.coerceAtLeast(1)
        var offset = 0.0
        val words = tokens.mapIndexed { i, token ->
            val duration = lineDuration * token.trim().length.toDouble() / totalChars
            val start = line.timeMs + offset.toLong()
            offset += duration
            val text = if (!cjk && i < tokens.lastIndex) "$token " else token
            LyricWord(text, start, line.timeMs + offset.toLong())
        }
        line.copy(words = words)
    }
}
