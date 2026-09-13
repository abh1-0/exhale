/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.DoubleByReference
import java.io.File
import kotlin.concurrent.thread

/**
 * The slice of the libmpv client API (mpv/client.h) the desktop player needs.
 *
 * libmpv is what ExoPlayer is on Android: it decodes both of YouTube's audio renditions
 * (Opus in WebM, AAC in MP4) straight off an HTTPS URL, so no Media3 port is needed.
 */
@Suppress("FunctionName")
private interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_wakeup(ctx: Pointer)
    fun mpv_error_string(error: Int): String
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_command(ctx: Pointer, args: Array<String>): Int
    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer
}

class Mpv(private val listener: Listener) : AutoCloseable {
    interface Listener {
        fun onFileLoaded() {}
        fun onEndOfFile() {}
        fun onError(message: String) {}
        fun onPositionChanged(seconds: Double) {}
        fun onDurationChanged(seconds: Double) {}
        fun onPauseChanged(paused: Boolean) {}
    }

    private val lib: LibMpv = loadLibrary()
    private val ctx: Pointer = lib.mpv_create() ?: error("mpv_create returned null")

    @Volatile private var running = true
    private val eventThread: Thread

    init {
        // Audio only: never open a video window, never shell out to yt-dlp — stream URLs
        // are resolved by innertube before they reach mpv.
        mapOf(
            "vid" to "no",
            "video" to "no",
            "audio-display" to "no",
            "ytdl" to "no",
            "idle" to "yes",
            "terminal" to "no",
            "input-default-bindings" to "no",
            "cache" to "yes",
            "demuxer-max-bytes" to "64MiB",
        ).forEach { (name, value) -> lib.mpv_set_option_string(ctx, name, value) }

        check(lib.mpv_initialize(ctx) >= 0) { "mpv_initialize failed" }

        lib.mpv_observe_property(ctx, 0, "time-pos", FORMAT_DOUBLE)
        lib.mpv_observe_property(ctx, 0, "duration", FORMAT_DOUBLE)
        lib.mpv_observe_property(ctx, 0, "pause", FORMAT_FLAG)

        eventThread = thread(name = "mpv-events", isDaemon = true) { eventLoop() }
    }

    fun load(url: String, headers: StreamHeaders) {
        lib.mpv_set_property_string(ctx, "user-agent", headers.userAgent)
        lib.mpv_set_property_string(ctx, "referrer", headers.referer.orEmpty())
        // http-header-fields is a comma-separated list, so only comma-free values go here.
        lib.mpv_set_property_string(ctx, "http-header-fields", headers.origin?.let { "Origin: $it" }.orEmpty())
        lib.mpv_set_property_string(ctx, "pause", "no")
        command("loadfile", url, "replace")
    }

    /** Flips mpv's own pause flag, whatever the UI last believed it was. */
    fun togglePause() {
        command("cycle", "pause")
    }

    fun setPaused(paused: Boolean) {
        lib.mpv_set_property_string(ctx, "pause", if (paused) "yes" else "no")
    }

    fun seekTo(seconds: Double) {
        command("seek", seconds.toString(), "absolute")
    }

    fun setVolume(percent: Int) {
        lib.mpv_set_property_string(ctx, "volume", percent.coerceIn(0, 100).toString())
    }

    fun stop() {
        command("stop")
    }

    private fun command(vararg args: String) {
        val result = lib.mpv_command(ctx, arrayOf(*args))
        if (result < 0) listener.onError("mpv ${args.first()}: ${lib.mpv_error_string(result)}")
    }

    private fun eventLoop() {
        while (running) {
            val event = lib.mpv_wait_event(ctx, -1.0)
            when (event.getInt(0)) {
                EVENT_SHUTDOWN -> return
                EVENT_FILE_LOADED -> listener.onFileLoaded()
                EVENT_END_FILE -> {
                    // mpv_event_end_file { int reason; int error; ... }
                    val data = event.getPointer(16) ?: continue
                    when (data.getInt(0)) {
                        END_REASON_EOF -> listener.onEndOfFile()
                        END_REASON_ERROR -> listener.onError("Playback failed: ${lib.mpv_error_string(data.getInt(4))}")
                    }
                }
                EVENT_PROPERTY_CHANGE -> {
                    // mpv_event_property { const char* name; mpv_format format; void* data; }
                    val property = event.getPointer(16) ?: continue
                    val format = property.getInt(8)
                    val value = property.getPointer(16) ?: continue
                    when (property.getPointer(0).getString(0)) {
                        "time-pos" -> if (format == FORMAT_DOUBLE) listener.onPositionChanged(value.getDouble(0))
                        "duration" -> if (format == FORMAT_DOUBLE) listener.onDurationChanged(value.getDouble(0))
                        "pause" -> if (format == FORMAT_FLAG) listener.onPauseChanged(value.getInt(0) != 0)
                    }
                }
            }
        }
    }

    fun position(): Double? {
        val out = DoubleByReference()
        return if (lib.mpv_get_property(ctx, "time-pos", FORMAT_DOUBLE, out.pointer) >= 0) out.value else null
    }

    override fun close() {
        running = false
        lib.mpv_wakeup(ctx)
        eventThread.join(1_000)
        lib.mpv_terminate_destroy(ctx)
    }

    private companion object {
        const val FORMAT_FLAG = 3
        const val FORMAT_DOUBLE = 5

        const val EVENT_SHUTDOWN = 1
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val EVENT_PROPERTY_CHANGE = 22

        const val END_REASON_EOF = 0
        const val END_REASON_ERROR = 4

        fun loadLibrary(): LibMpv {
            // Packaged app: Compose copies resources/windows into the install and points
            // compose.application.resources.dir at it. `gradlew :desktop:run` does the same
            // with a staging copy. The fallback covers running Main from the IDE.
            val searchPath = listOfNotNull(
                System.getProperty("compose.application.resources.dir"),
                File("desktop/resources/windows").absolutePath,
                File("resources/windows").absolutePath,
            ).joinToString(File.pathSeparator)
            System.setProperty("jna.library.path", searchPath)
            return Native.load("libmpv-2", LibMpv::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
        }
    }
}
