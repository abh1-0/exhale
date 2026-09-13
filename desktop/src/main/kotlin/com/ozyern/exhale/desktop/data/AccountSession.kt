/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import com.ozyern.exhale.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class YouTubeAccount(val name: String, val email: String?, val handle: String?, val avatar: String?)

/**
 * The signed-in YouTube Music session: what the sign-in window captured, persisted in
 * [DesktopPrefs] and pushed into innertube's globals. Signed in, Home comes from the account —
 * which is the one feed YouTube doesn't build from the caller's IP.
 */
object AccountSession {
    private val _account = MutableStateFlow(savedAccount())
    val account: StateFlow<YouTubeAccount?> = _account.asStateFlow()

    val isSignedIn: Boolean
        get() = DesktopPrefs.cookie?.contains("SAPISID") == true

    /** Set by the sign-in browser so signing out also forgets the browser's own cookies. */
    var clearBrowserCookies: (() -> Unit)? = null

    fun applySaved() {
        YouTube.cookie = DesktopPrefs.cookie
        YouTube.dataSyncId = DesktopPrefs.dataSyncId
        YouTube.useLoginForBrowse = isSignedIn && DesktopPrefs.useAccountForHome
    }

    suspend fun completeSignIn(cookie: String, dataSyncId: String?, visitorData: String?) {
        DesktopPrefs.cookie = cookie
        dataSyncId?.takeIf { it.isNotBlank() }?.let { DesktopPrefs.dataSyncId = it.substringBefore("||") }
        visitorData?.takeIf { it.isNotBlank() }?.let {
            // The page's visitor id belongs to this session; keep it rather than reminting.
            DesktopPrefs.visitorData = it
            DesktopPrefs.visitorDataLocale = "${YouTube.locale.hl}|${YouTube.locale.gl}"
            YouTube.visitorData = it
        }
        applySaved()
        refreshAccountInfo()
    }

    suspend fun refreshAccountInfo() {
        if (!isSignedIn) return
        withContext(Dispatchers.IO) { YouTube.accountInfo() }.onSuccess { info ->
            DesktopPrefs.accountName = info.name
            DesktopPrefs.accountEmail = info.email
            DesktopPrefs.accountHandle = info.channelHandle
            DesktopPrefs.accountAvatar = info.thumbnailUrl
            _account.value = YouTubeAccount(info.name, info.email, info.channelHandle, info.thumbnailUrl)
        }
    }

    fun setUseAccountForHome(enabled: Boolean) {
        DesktopPrefs.useAccountForHome = enabled
        applySaved()
    }

    fun signOut() {
        DesktopPrefs.cookie = null
        DesktopPrefs.dataSyncId = null
        DesktopPrefs.accountName = null
        DesktopPrefs.accountEmail = null
        DesktopPrefs.accountHandle = null
        DesktopPrefs.accountAvatar = null
        // The visitor id was bound to the session; the next request mints a fresh anonymous one.
        DesktopPrefs.visitorData = null
        YouTube.visitorData = null
        applySaved()
        _account.value = null
        clearBrowserCookies?.invoke()
    }

    private fun savedAccount(): YouTubeAccount? {
        if (DesktopPrefs.cookie?.contains("SAPISID") != true) return null
        val name = DesktopPrefs.accountName ?: return null
        return YouTubeAccount(name, DesktopPrefs.accountEmail, DesktopPrefs.accountHandle, DesktopPrefs.accountAvatar)
    }
}
