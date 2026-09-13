/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.lyrics.LyricsRepository
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.home.LargeTitle

private val LanguageNames = mapOf(
    "en" to "English", "hi" to "Hindi", "es" to "Spanish", "ko" to "Korean", "ja" to "Japanese",
    "pt" to "Portuguese", "fr" to "French", "de" to "German", "ar" to "Arabic", "it" to "Italian",
    "ru" to "Russian", "tr" to "Turkish", "id" to "Indonesian", "th" to "Thai", "zh" to "Chinese", "pl" to "Polish",
)

/**
 * The desktop subset of the Android app's settings — Account, Content, Player & audio, Lyrics,
 * Privacy, About — as glass groups on one scrolling page.
 */
@Composable
fun SettingsScreen(
    insets: ChromeInsets,
    onSignIn: () -> Unit,
    onEditTaste: () -> Unit,
    onHomeInputsChanged: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = insets.start + 20.dp, end = 40.dp, top = 36.dp, bottom = insets.bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { LargeTitle("Settings") }
        item { AccountGroup(onSignIn, onHomeInputsChanged) }
        item { ContentGroup(onEditTaste, onHomeInputsChanged) }
        item { PlaybackGroup() }
        item { LyricsGroup() }
        item { PrivacyGroup() }
        item { AboutGroup() }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        Column(Modifier.fillMaxWidth().glassPlate(RoundedCornerShape(22.dp)).padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.let {
            Spacer(Modifier.width(16.dp))
            it()
        }
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Color.White.copy(alpha = 0.06f))

@Composable
private fun SwitchRow(title: String, subtitle: String?, initial: Boolean, onChange: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(initial) }
    val toggle = { value: Boolean ->
        checked = value
        onChange(value)
    }
    SettingRow(title, subtitle, onClick = { toggle(!checked) }) { Switch(checked = checked, onCheckedChange = toggle) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(title: String, subtitle: String?, options: List<Pair<String, String>>, initial: String, onChange: (String) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    SettingRow(title, subtitle) {
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = {
                        selected = value
                        onChange(value)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun AccountGroup(onSignIn: () -> Unit, onHomeInputsChanged: () -> Unit) {
    val account by AccountSession.account.collectAsState()
    SettingsGroup("Account") {
        val current = account
        if (current == null && !AccountSession.isSignedIn) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                    Icon(NavIcons.Artists, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sign in to YouTube Music", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your library, likes and a Home built from your account instead of your location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Button(onClick = onSignIn) { Text("Sign in") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = current?.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(current?.name ?: "Signed in", style = MaterialTheme.typography.titleMedium)
                    listOfNotNull(current?.handle, current?.email).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedButton(onClick = {
                    AccountSession.signOut()
                    onHomeInputsChanged()
                }) { Text("Sign out") }
            }
            RowDivider()
            SwitchRow(
                title = "Use my account for Home",
                subtitle = "Recommendations from your YouTube Music history instead of your region.",
                initial = DesktopPrefs.useAccountForHome,
            ) {
                AccountSession.setUseAccountForHome(it)
                onHomeInputsChanged()
            }
        }
    }
}

@Composable
private fun ContentGroup(onEditTaste: () -> Unit, onHomeInputsChanged: () -> Unit) {
    SettingsGroup("Content") {
        val languages = DesktopPrefs.languages.joinToString { LanguageNames[it] ?: it }.ifBlank { "Not set" }
        SettingRow(
            title = "Your Music Taste",
            subtitle = "$languages · ${DesktopPrefs.artists.value.size} artists",
            onClick = onEditTaste,
        ) { TextButton(onClick = onEditTaste) { Text("Edit") } }
        RowDivider()
        SwitchRow("Hide explicit content", null, DesktopPrefs.hideExplicit) {
            DesktopPrefs.hideExplicit = it
            onHomeInputsChanged()
        }
        RowDivider()
        SwitchRow("Hide music videos", "Keep shelves to audio releases.", DesktopPrefs.hideVideos) {
            DesktopPrefs.hideVideos = it
            onHomeInputsChanged()
        }
    }
}

@Composable
private fun PlaybackGroup() {
    SettingsGroup("Player & audio") {
        ChoiceRow(
            title = "Audio quality",
            subtitle = "Applies from the next song.",
            options = listOf("MAX" to "Max", "HIGH" to "High", "LOW" to "Low"),
            initial = DesktopPrefs.audioQuality,
        ) { DesktopPrefs.audioQuality = it }
        RowDivider()
        ChoiceRow(
            title = "Codec",
            subtitle = "Preferred when the stream offers both.",
            options = listOf("AUTO" to "Auto", "OPUS" to "Opus", "AAC" to "AAC"),
            initial = DesktopPrefs.audioCodec,
        ) { DesktopPrefs.audioCodec = it }
    }
}

@Composable
private fun LyricsGroup() {
    SettingsGroup("Lyrics") {
        LyricsRepository.providerNames.forEachIndexed { index, name ->
            if (index > 0) RowDivider()
            SwitchRow(name, if (index == 0) "Tried in this order; synced lyrics win." else null, DesktopPrefs.isLyricsProviderEnabled(name)) {
                DesktopPrefs.setLyricsProviderEnabled(name, it)
                LyricsRepository.clearCache()
            }
        }
    }
}

@Composable
private fun PrivacyGroup() {
    var cleared by remember { mutableStateOf(false) }
    SettingsGroup("Privacy") {
        SwitchRow("Pause listen history", "Songs you play won't be added to Recently Played.", DesktopPrefs.pauseHistory) {
            DesktopPrefs.pauseHistory = it
        }
        RowDivider()
        SettingRow("Clear listen history", if (cleared) "Cleared." else null) {
            TextButton(onClick = {
                DesktopPrefs.clearHistory()
                cleared = true
            }) { Text("Clear") }
        }
    }
}

@Composable
private fun AboutGroup() {
    SettingsGroup("About") {
        SettingRow("Exhale for Windows", "Version 1.0.0")
        RowDivider()
        SettingRow("Audio engine", "libmpv")
        RowDivider()
        SettingRow("Licence", "GPL-3.0 — ozyern (github.com/ozyern)")
    }
}
