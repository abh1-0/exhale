/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.ozyern.exhale.desktop.appIcon
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.ui.ExhaleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.EnumProgress
import me.friwi.jcefmaven.IProgressHandler
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.network.CefCookieManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Google's sign-in, continuing to YouTube Music — the same destination the Android login lands on. */
private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"

/**
 * Chromium, once per process. jcefmaven downloads the natives (~100 MB) into
 * `%APPDATA%\Exhale\jcef` the first time anyone signs in, so the app download stays small.
 */
object SignInBrowser {
    private val installDir = File(
        System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config"),
        "Exhale/jcef",
    )

    @Volatile private var app: CefApp? = null

    fun ensure(onProgress: (EnumProgress, Float) -> Unit): CefApp {
        app?.let { return it }
        synchronized(this) {
            app?.let { return it }
            val built = CefAppBuilder().apply {
                setInstallDir(installDir)
                setProgressHandler(IProgressHandler { state, percent -> onProgress(state, percent) })
                // Google refuses sign-in from browsers that announce themselves as embedded.
                cefSettings.user_agent =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
            }.build()
            AccountSession.clearBrowserCookies = { CefCookieManager.getGlobalManager()?.deleteCookies("", "") }
            app = built
            return built
        }
    }

    fun shutdown() {
        runCatching { app?.dispose() }
        app = null
    }
}

private sealed interface SignInStatus {
    data class Preparing(val step: EnumProgress?, val percent: Float) : SignInStatus
    data object Ready : SignInStatus
    data object Finishing : SignInStatus
    data class Failed(val message: String) : SignInStatus
}

/** Every youtube.com cookie, HttpOnly ones included, as one Cookie header. */
private fun collectYouTubeCookies(onDone: (String) -> Unit) {
    val manager = CefCookieManager.getGlobalManager() ?: return
    val parts = LinkedHashMap<String, String>()
    manager.visitAllCookies { cookie, count, total, _ ->
        if (cookie.domain.trimStart('.').endsWith("youtube.com")) parts[cookie.name] = cookie.value
        if (count == total - 1) onDone(parts.entries.joinToString("; ") { "${it.key}=${it.value}" })
        true
    }
}

/**
 * The Android LoginScreen, on Chromium: sign in with Google, and once a youtube.com session cookie
 * (SAPISID) exists, hand the cookie, DATASYNC_ID and VISITOR_DATA to [AccountSession].
 */
@Composable
fun SignInWindow(onClose: () -> Unit, onSignedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<SignInStatus>(SignInStatus.Preparing(null, -1f)) }
    var browser by remember { mutableStateOf<CefBrowser?>(null) }
    var client by remember { mutableStateOf<CefClient?>(null) }

    LaunchedEffect(Unit) {
        val app = try {
            withContext(Dispatchers.IO) {
                SignInBrowser.ensure { step, percent -> scope.launch { status = SignInStatus.Preparing(step, percent) } }
            }
        } catch (e: Exception) {
            status = SignInStatus.Failed(e.message ?: "Couldn't start the sign-in browser")
            return@LaunchedEffect
        }

        val newClient = app.createClient()
        // Written from CEF's thread, read from ours.
        val dataSyncId = AtomicReference<String?>(null)
        val visitorData = AtomicReference<String?>(null)
        val captured = AtomicBoolean(false)

        val router = CefMessageRouter.create()
        router.addHandler(
            object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    queryId: Long,
                    request: String?,
                    persistent: Boolean,
                    callback: CefQueryCallback?,
                ): Boolean {
                    val value = request ?: return false
                    when {
                        value.startsWith("dsid:") -> value.removePrefix("dsid:").takeIf { it.isNotBlank() }?.let { dataSyncId.set(it) }
                        value.startsWith("vd:") -> value.removePrefix("vd:").takeIf { it.isNotBlank() }?.let { visitorData.set(it) }
                        else -> return false
                    }
                    callback?.success("")
                    return true
                }
            },
            true,
        )
        newClient.addMessageRouter(router)

        newClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                    if (!frame.isMain) return
                    val url = browser.url ?: return
                    if (!url.contains("youtube.com")) return

                    browser.executeJavaScript(
                        """
                        try {
                          var c = window.ytcfg;
                          var get = function (k) { return (c && c.get && c.get(k)) || ''; };
                          window.cefQuery({ request: 'dsid:' + get('DATASYNC_ID'), onSuccess: function () {}, onFailure: function () {} });
                          window.cefQuery({ request: 'vd:' + get('VISITOR_DATA'), onSuccess: function () {}, onFailure: function () {} });
                        } catch (e) {}
                        """.trimIndent(),
                        url,
                        0,
                    )

                    collectYouTubeCookies { cookie ->
                        if ("SAPISID" !in cookie || !captured.compareAndSet(false, true)) return@collectYouTubeCookies
                        scope.launch {
                            status = SignInStatus.Finishing
                            // Give the page's ytcfg answers a moment to arrive.
                            delay(800)
                            AccountSession.completeSignIn(cookie, dataSyncId.get(), visitorData.get())
                            onSignedIn()
                        }
                    }
                }
            },
        )

        browser = newClient.createBrowser(LOGIN_URL, false, false)
        client = newClient
        status = SignInStatus.Ready
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { browser?.close(true) }
            runCatching { client?.dispose() }
        }
    }

    Window(
        onCloseRequest = onClose,
        title = "Sign in to YouTube Music",
        icon = appIcon,
        state = rememberWindowState(width = 520.dp, height = 760.dp),
    ) {
        ExhaleTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (val current = status) {
                    is SignInStatus.Preparing -> CenteredMessage {
                        Text("Getting the sign-in browser ready", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "First time only — about 100 MB.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (current.step == EnumProgress.DOWNLOADING && current.percent >= 0f) {
                            LinearProgressIndicator(progress = { current.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        current.step?.let {
                            Text(it.name.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    SignInStatus.Ready -> browser?.let { b ->
                        SwingPanel(factory = { b.uiComponent }, modifier = Modifier.fillMaxSize())
                    }
                    SignInStatus.Finishing -> CenteredMessage {
                        Text("Signed in — finishing up…", style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is SignInStatus.Failed -> CenteredMessage {
                        Text("Sign-in isn't available", style = MaterialTheme.typography.titleMedium)
                        Text(current.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Button(onClick = onClose) { Text("Close") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}
