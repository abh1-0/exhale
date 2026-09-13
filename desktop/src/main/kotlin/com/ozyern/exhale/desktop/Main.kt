/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.data.ContentLocale
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.ui.ExhaleApp
import com.ozyern.exhale.desktop.ui.ExhaleTheme
import com.ozyern.exhale.desktop.ui.account.SignInBrowser
import org.jetbrains.skia.Image

/** Title bar + taskbar icon. The installer's icon is icons/exhale.ico, set in build.gradle.kts. */
internal val appIcon: BitmapPainter by lazy {
    val bytes = object {}.javaClass.getResourceAsStream("/exhale.png")!!.use { it.readAllBytes() }
    BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}

fun main() {
    // The listener's languages decide hl/gl before any request leaves — not the JVM default,
    // which let YouTube localise purely by IP. Before onboarding this is en/US.
    ContentLocale.apply(DesktopPrefs.languages)
    AccountSession.applySaved()

    application {
        val scope = rememberCoroutineScope()
        val player = remember { runCatching { DesktopPlayer(scope) } }
        DisposableEffect(player) {
            onDispose { player.getOrNull()?.close() }
        }

        Window(
            onCloseRequest = {
                SignInBrowser.shutdown()
                exitApplication()
            },
            title = "Exhale",
            icon = appIcon,
            state = rememberWindowState(width = 1320.dp, height = 860.dp),
        ) {
            window.minimumSize = java.awt.Dimension(960, 640)
            ExhaleTheme {
                // Surface rather than a background modifier: it also sets LocalContentColor,
                // without which any Text that doesn't pass a colour renders black.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    player.fold(
                        onSuccess = { ExhaleApp(it) },
                        onFailure = { error -> PlayerUnavailable(error) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerUnavailable(error: Throwable) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "Couldn't start the audio engine: ${error.message}\n\n" +
                "libmpv-2.dll is missing — run desktop/scripts/fetch-libmpv.ps1.",
            color = MaterialTheme.colorScheme.error,
        )
    }
}
