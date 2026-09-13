/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.innertube.NewPipeUtils
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.YouTubeClient
import com.ozyern.exhale.innertube.models.response.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

data class StreamHeaders(
    val userAgent: String,
    val origin: String?,
    val referer: String?,
)

data class ResolvedStream(
    val url: String,
    val headers: StreamHeaders,
    val mimeType: String,
    val bitrate: Int,
    val clientName: String,
) {
    fun describe(): String {
        val codec = Regex("""codecs="([^"]+)"""").find(mimeType)?.groupValues?.get(1) ?: mimeType
        return "$codec · ${bitrate / 1000} kbps · $clientName"
    }
}

/**
 * Video id → a URL mpv can open.
 *
 * A trimmed-down port of the Android app's YTPlayerUtils: same client fallback idea, same
 * NewPipe deciphering, same range-probe validation — without the quality/codec preferences,
 * the MAX-mode probing and the login gate, which arrive with settings.
 */
object StreamResolver {
    // Same order as the Android app: its preferred ANDROID_VR first, then STREAM_FALLBACK_CLIENTS.
    private val clients: List<YouTubeClient> = listOf(
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.IOS,
        YouTubeClient.MOBILE,
        YouTubeClient.ANDROID_MUSIC,
        YouTubeClient.IOS_MUSIC,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_CREATOR,
        YouTubeClient.ANDROID_TESTSUITE,
        YouTubeClient.ANDROID_UNPLUGGED,
        YouTubeClient.IPADOS,
        YouTubeClient.VISIONOS,
        YouTubeClient.TVHTML5,
        YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        YouTubeClient.WEB,
        YouTubeClient.WEB_CREATOR,
        YouTubeClient.WEB_REMIX,
    )

    private val http = OkHttpClient()

    /**
     * Which client works is decided by the network (bot checks, IP reputation), not the song,
     * so the last one that played goes first. Otherwise every track pays for the same run of
     * failed requests before reaching it.
     */
    @Volatile private var lastWorkingClient: YouTubeClient? = null

    suspend fun resolve(videoId: String): Result<ResolvedStream> = withContext(Dispatchers.IO) {
        runCatching {
            val signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
            val isLoggedIn = YouTube.cookie != null
            // Why each client/format fell through, so a failure says more than "no stream".
            val failures = mutableListOf<String>()
            val ordered = lastWorkingClient?.let { listOf(it) + (clients - it) } ?: clients

            for (client in ordered) {
                if (client.loginRequired && !isLoggedIn) continue
                val name = client.clientName

                val response = YouTube.player(videoId, null, client, signatureTimestamp)
                    .getOrElse { failures += "$name: ${it.message}"; continue }
                if (response.playabilityStatus.status != "OK") {
                    failures += "$name: ${response.playabilityStatus.status} ${response.playabilityStatus.reason.orEmpty()}"
                    continue
                }

                val candidates = audioCandidates(response)
                if (candidates.isEmpty()) failures += "$name: no audio formats"

                val headers = headersFor(client)
                for (format in candidates.take(4)) {
                    val url = NewPipeUtils.getStreamUrl(format, videoId, client)
                        .getOrElse { failures += "$name/${format.itag}: ${it.message}"; continue }
                        .replace(Regex("cver=[^&]+"), "cver=${client.clientVersion}")
                    val status = probe(url, headers)
                    if (status in 200..399 || status == 416) {
                        lastWorkingClient = client
                        return@runCatching ResolvedStream(url, headers, format.mimeType, format.bitrate, name)
                    }
                    failures += "$name/${format.itag}: HTTP $status"
                }
            }

            error("No playable stream for $videoId — ${failures.joinToString("; ")}")
        }
    }

    /**
     * Direct URLs first (deciphering can fail), then the codec the listener picked, then bitrate —
     * under the quality ceiling if there is one. Same ceilings as the Android app.
     */
    private fun audioCandidates(response: PlayerResponse): List<PlayerResponse.StreamingData.Format> {
        val formats = response.streamingData?.adaptiveFormats.orEmpty()
            .filter { it.isAudio && it.bitrate > 0 }
            .filter { it.url != null || it.signatureCipher != null || it.cipher != null }
        val ceiling = when (DesktopPrefs.audioQuality) {
            "LOW" -> 70_000
            "HIGH" -> 256_000
            else -> null
        }
        val codec = DesktopPrefs.audioCodec
        val pool = ceiling?.let { cap -> formats.filter { it.bitrate <= cap }.ifEmpty { formats } } ?: formats
        return pool.sortedWith(
            compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                .thenByDescending {
                    when (codec) {
                        "OPUS" -> "opus" in it.mimeType
                        "AAC" -> "mp4a" in it.mimeType
                        else -> false
                    }
                }
                .thenByDescending { it.bitrate },
        )
    }

    /** Native app clients stream without Origin/Referer; web and TV clients are refused without them. */
    private fun headersFor(client: YouTubeClient): StreamHeaders {
        val name = client.clientName.uppercase(Locale.US)
        val webLike = name.startsWith("WEB") || name == "MWEB" || name.startsWith("TVHTML5")
        return StreamHeaders(
            userAgent = client.userAgent,
            origin = if (webLike) client.requestOrigin() else null,
            referer = if (webLike) client.requestReferer() else null,
        )
    }

    /** HTTP status of a one-byte range request, or -1 if it never got an answer. */
    private fun probe(url: String, headers: StreamHeaders): Int = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", headers.userAgent)
            .header("Range", "bytes=0-0")
            .apply {
                headers.origin?.let { header("Origin", it) }
                headers.referer?.let { header("Referer", it) }
            }
            .build()
        http.newCall(request).execute().use { it.code }
    }.getOrDefault(-1)
}
