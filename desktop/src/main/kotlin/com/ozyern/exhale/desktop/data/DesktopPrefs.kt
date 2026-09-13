/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.Album
import com.ozyern.exhale.innertube.models.Artist
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YouTubeLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties

data class PreferredArtist(val id: String, val name: String, val thumbnail: String?)

/**
 * The desktop app's settings, in `%APPDATA%\Exhale\settings.properties`. Small enough that every
 * write rewrites the file.
 */
object DesktopPrefs {
    private const val RECORD = ''
    private const val FIELD = ''
    private const val LIST = ''
    private const val PAIR = ''

    private val file: File = File(
        System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config"),
        "Exhale/settings.properties",
    )
    private val props = Properties().apply {
        runCatching { if (file.exists()) file.inputStream().use(::load) }
    }

    var onboardingCompleted: Boolean
        get() = props.getProperty("onboarding_completed") == "true"
        set(value) = put("onboarding_completed", value.toString())

    /** In the order the user picked them; the first one decides the region. */
    var languages: List<String>
        get() = props.getProperty("languages").orEmpty().split(',').filter { it.isNotBlank() }
        set(value) = put("languages", value.joinToString(","))

    private val _artists = MutableStateFlow(readArtists())
    val artists: StateFlow<List<PreferredArtist>> = _artists.asStateFlow()

    fun setArtists(value: List<PreferredArtist>) {
        putRecords("artists", value.map { listOf(it.id, it.name, it.thumbnail.orEmpty()) })
        _artists.value = value
    }

    var visitorData: String?
        get() = props.getProperty("visitor_data")
        set(value) = put("visitor_data", value)

    /** The `hl|gl` the stored visitorData was minted under. */
    var visitorDataLocale: String?
        get() = props.getProperty("visitor_data_locale")
        set(value) = put("visitor_data_locale", value)

    private val _history = MutableStateFlow(readHistory())
    val history: StateFlow<List<SongItem>> = _history.asStateFlow()

    fun recordPlayed(song: SongItem) {
        if (pauseHistory) return
        val updated = (listOf(song) + _history.value.filterNot { it.id == song.id }).take(40)
        putRecords(
            "history",
            updated.map { s ->
                listOf(
                    s.id,
                    s.title,
                    s.artists.joinToString(LIST.toString()) { "${it.name}$PAIR${it.id.orEmpty()}" },
                    s.thumbnail,
                    s.duration?.toString().orEmpty(),
                    s.album?.name.orEmpty(),
                    s.album?.id.orEmpty(),
                )
            },
        )
        _history.value = updated
    }

    fun clearHistory() {
        put("history", null)
        _history.value = emptyList()
    }

    // Account — the YouTube Music session captured by the sign-in window. See AccountSession.
    var cookie: String?
        get() = props.getProperty("yt_cookie")
        set(value) = put("yt_cookie", value)
    var dataSyncId: String?
        get() = props.getProperty("yt_data_sync_id")
        set(value) = put("yt_data_sync_id", value)
    var accountName: String?
        get() = props.getProperty("account_name")
        set(value) = put("account_name", value)
    var accountEmail: String?
        get() = props.getProperty("account_email")
        set(value) = put("account_email", value)
    var accountHandle: String?
        get() = props.getProperty("account_handle")
        set(value) = put("account_handle", value)
    var accountAvatar: String?
        get() = props.getProperty("account_avatar")
        set(value) = put("account_avatar", value)
    var useAccountForHome: Boolean
        get() = props.getProperty("use_account_for_home") != "false"
        set(value) = put("use_account_for_home", value.toString())

    // Content
    var hideExplicit: Boolean
        get() = props.getProperty("hide_explicit") == "true"
        set(value) = put("hide_explicit", value.toString())
    var hideVideos: Boolean
        get() = props.getProperty("hide_videos") == "true"
        set(value) = put("hide_videos", value.toString())

    // Playback — MAX / HIGH / LOW and AUTO / OPUS / AAC, same choices as the Android app.
    var audioQuality: String
        get() = props.getProperty("audio_quality") ?: "MAX"
        set(value) = put("audio_quality", value)
    var audioCodec: String
        get() = props.getProperty("audio_codec") ?: "AUTO"
        set(value) = put("audio_codec", value)

    // Lyrics
    fun isLyricsProviderEnabled(name: String): Boolean = props.getProperty("lyrics_provider_$name") != "false"
    fun setLyricsProviderEnabled(name: String, enabled: Boolean) = put("lyrics_provider_$name", enabled.toString())

    // Privacy
    var pauseHistory: Boolean
        get() = props.getProperty("pause_history") == "true"
        set(value) = put("pause_history", value.toString())

    private fun readArtists(): List<PreferredArtist> =
        records("artists").mapNotNull { f ->
            if (f.size < 2) null else PreferredArtist(f[0], f[1], f.getOrNull(2)?.ifBlank { null })
        }

    private fun readHistory(): List<SongItem> =
        records("history").mapNotNull { f ->
            if (f.size < 4) return@mapNotNull null
            SongItem(
                id = f[0],
                title = f[1],
                artists = f[2].split(LIST).filter { it.isNotEmpty() }.map {
                    Artist(name = it.substringBefore(PAIR), id = it.substringAfter(PAIR, "").ifBlank { null })
                },
                thumbnail = f[3],
                duration = f.getOrNull(4)?.toIntOrNull(),
                album = f.getOrNull(5)?.takeIf { it.isNotBlank() }?.let { name ->
                    f.getOrNull(6)?.takeIf { it.isNotBlank() }?.let { Album(name, it) }
                },
            )
        }

    private fun records(key: String): List<List<String>> =
        props.getProperty(key).orEmpty().split(RECORD).filter { it.isNotEmpty() }.map { it.split(FIELD) }

    private fun putRecords(key: String, rows: List<List<String>>) =
        put(key, rows.joinToString(RECORD.toString()) { it.joinToString(FIELD.toString()) })

    @Synchronized
    private fun put(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        runCatching {
            file.parentFile.mkdirs()
            file.outputStream().use { props.store(it, "Exhale desktop") }
        }
    }
}

/**
 * What YouTube is told about the listener. Without this, `hl`/`gl` came from the JVM default and
 * the visitor id was minted with nothing else to go on — so YouTube localised Home by IP.
 * Same rules as the Android app's `applyStrictLocale` + `remintVisitorDataIfLocaleChanged`.
 */
object ContentLocale {
    fun apply(languages: List<String>) {
        val lang = languages.firstOrNull() ?: "en"
        YouTube.locale = YouTubeLocale(gl = anchorRegionFor(lang), hl = lang)
    }

    /** Reuses the stored visitor id only if it was minted under the current locale. */
    suspend fun ensureVisitorData() {
        val signature = "${YouTube.locale.hl}|${YouTube.locale.gl}"
        val saved = DesktopPrefs.visitorData
        // Signed in, the visitor id is bound to the session — reminting it would break the login.
        if (saved != null && (DesktopPrefs.visitorDataLocale == signature || DesktopPrefs.cookie != null)) {
            YouTube.visitorData = saved
            return
        }
        // An old id would introduce the new locale with the old identity.
        YouTube.visitorData = null
        YouTube.visitorData().onSuccess {
            YouTube.visitorData = it
            DesktopPrefs.visitorData = it
            DesktopPrefs.visitorDataLocale = signature
        }
    }

    /** Canonical region per language — overrides the IP-derived one. Same table as the Android app. */
    fun anchorRegionFor(lang: String): String = when (lang.lowercase().substringBefore("-")) {
        "en" -> "US"
        "es" -> "ES"
        "fr" -> "FR"
        "de" -> "DE"
        "it" -> "IT"
        "pt" -> "BR"
        "ru" -> "RU"
        "ja" -> "JP"
        "ko" -> "KR"
        "zh" -> "TW"
        "hi", "bn", "pa", "gu", "ta", "te", "kn", "ml", "mr" -> "IN"
        "ar" -> "SA"
        "tr" -> "TR"
        "nl" -> "NL"
        "pl" -> "PL"
        "sv" -> "SE"
        "no", "nb" -> "NO"
        "da" -> "DK"
        "fi" -> "FI"
        "id" -> "ID"
        "vi" -> "VN"
        "th" -> "TH"
        "uk" -> "UA"
        "el" -> "GR"
        "he", "iw" -> "IL"
        "cs" -> "CZ"
        "hu" -> "HU"
        "ro" -> "RO"
        else -> "US"
    }
}
