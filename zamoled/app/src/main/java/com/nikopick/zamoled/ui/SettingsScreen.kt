package com.nikopick.zamoled.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import com.nikopick.zamoled.data.Trio
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.nikopick.zamoled.R
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nikopick.zamoled.data.Prefs
import com.nikopick.zamoled.data.Updates
import kotlin.math.roundToInt

private fun mb(bytes: Long): String = if (bytes <= 0L) "" else "%.1f MB".format(bytes / 1_048_576f)

/** Updates, the daily wallpaper, storage and a line about the app. */
@Composable
fun SettingsScreen(ota: OtaState, prefs: Prefs, onBack: () -> Unit, onDaily: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var cacheSize by remember { mutableLongStateOf(ThumbCache.diskSize()) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Column {
                MonoLabel("Zamoled")
                Text("Settings", style = MaterialTheme.typography.headlineLarge)
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---- Updates ----
        SectionHead("01", "Updates")
        Spacer(Modifier.height(10.dp))
        LeafCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        MonoLabel("Installed")
                        Text(Updates.installedVersion, style = MaterialTheme.typography.displaySmall)
                    }
                    val i = ota.info
                    if (ota.available && i != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            MonoLabel("New", color = MaterialTheme.colorScheme.primary)
                            Text(i.version, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                val status = when (ota.phase) {
                    OtaPhase.CHECKING -> "Checking for a new version…"
                    OtaPhase.UP_TO_DATE -> "You are on the latest version."
                    OtaPhase.AVAILABLE -> "A new version is ready${ota.info?.size?.let { if (it > 0) " (${mb(it)})" else "" }.orEmpty()}. Install updates the app in place and your favorites stay."
                    OtaPhase.ERROR -> ota.message ?: "Something went wrong."
                    OtaPhase.IDLE -> "Tap check to look for a new version."
                }
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val extra = ota.progress ?: if (ota.phase == OtaPhase.AVAILABLE) ota.message else null
                if (extra != null) Text(extra, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                if (ota.phase == OtaPhase.CHECKING) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (ota.available) {
                        SoftButton(onClick = { ota.download() }, enabled = !ota.installing) { Text(if (ota.installing) "Installing…" else "Install") }
                        TextButton(onClick = { ota.openReleasePage() }) { Text("Release page") }
                    } else {
                        SoftButton(onClick = { ota.check(manual = true) }, enabled = ota.phase != OtaPhase.CHECKING) {
                            Text(if (ota.phase == OtaPhase.ERROR) "Check again" else "Check for updates")
                        }
                    }
                }
                val notes = ota.info?.notes.orEmpty()
                if (ota.available && notes.isNotBlank()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                    MonoLabel("What's new")
                    Text(notes.take(700) + if (notes.length > 700) "…" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { ota.skipVersion(); ota.dismissed = true }) { Text("Skip this version") }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LeafCard(Modifier.fillMaxWidth(), shape = LeafSm) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Check automatically", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Looks for a new version on GitHub when the app opens and about twice a day, and sends one notification when there is one. Nothing is installed without you tapping the file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = prefs.autoUpdate,
                    onCheckedChange = {
                        prefs.changeAutoUpdate(it)
                        Updates.schedule(context, it)
                        if (it && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
            }
        }

        // ---- Daily wallpaper ----
        Spacer(Modifier.height(22.dp))
        SectionHead("02", "Daily wallpaper")
        Spacer(Modifier.height(10.dp))
        LeafCard(Modifier.fillMaxWidth().clickable { onDaily() }, shape = LeafSm) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(18.dp)) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = if (prefs.autoEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(if (prefs.autoEnabled) "On" else "Off", style = MaterialTheme.typography.titleMedium)
                    Text("A fresh wallpaper about once a day, set in the background.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MonoLabel("Edit", color = MaterialTheme.colorScheme.primary)
            }
        }

        // ---- Storage ----
        Spacer(Modifier.height(22.dp))
        SectionHead("03", "Storage")
        Spacer(Modifier.height(10.dp))
        LeafCard(Modifier.fillMaxWidth(), shape = LeafSm) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Preview cache", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (cacheSize > 0) "${mb(cacheSize)} of saved previews keep the app instant. They are drawn again when needed." else "Empty.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { ThumbCache.clearDisk(); cacheSize = ThumbCache.diskSize() }, enabled = cacheSize > 0) { Text("Clear") }
            }
        }

        // ---- About ----
        Spacer(Modifier.height(22.dp))
        SectionHead("04", "About")
        Spacer(Modifier.height(10.dp))
        Text(
            "Zamoled draws every wallpaper on your phone, so nothing is downloaded and every picture can be saved at your screen size, QHD+ or 4K. Pure black pixels are switched off on an AMOLED screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Spacer(Modifier.height(14.dp))
        LeafCard(Modifier.fillMaxWidth(), shape = LeafSm) {
            Column(Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.profile), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(CircleShape))
                Spacer(Modifier.height(16.dp))
                Text("Nikezvera", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Developer of Zamoled", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text("Version ${Updates.installedVersion}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AboutLink("Telegram", "@xnzvix", "https://t.me/xnzvix")
        Spacer(Modifier.height(12.dp))
        LogCard()
        Spacer(Modifier.height(12.dp))
        NoticesCard()
        Spacer(Modifier.height(12.dp))
        AboutLink("Source and releases", "GitHub", "https://github.com/VK-VfX/nikezverapps/releases")
        Spacer(Modifier.height(22.dp))
        SectionHead("05", "The trio")
        Spacer(Modifier.height(10.dp))
        val latest by produceState(emptyMap<String, String>()) { value = Trio.latestVersions() }
        val kept = remember { context.getSharedPreferences("zamoled", android.content.Context.MODE_PRIVATE).getStringSet("favorites", emptySet())?.size ?: 0 }
        val purrStat = remember { Trio.stat(context, "app.skypix.weather.catclub") }
        val mapStat = remember { Trio.stat(context, "app.mapsandcats.catclub") }
        LeafCard(Modifier.fillMaxWidth(), shape = LeafSm) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text("Cat Club", style = MaterialTheme.typography.titleMedium)
                val streak = remember { com.nikopick.zamoled.data.StreakStore.read(context).count }
                val shield = remember { com.nikopick.zamoled.data.StreakStore.shieldLine(context) }
                val parts = listOfNotNull(
                    "$streak day streak",
                    "$kept wallpapers kept",
                    purrStat?.let { "${it.value} ${it.label}" },
                    mapStat?.let { "${it.value} ${it.label}" },
                )
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (shield.isNotEmpty()) Text(shield, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "How it works: every app counts its own day streak, the same way, and each app shows the other two when installed. " +
                        "Install all three and a Streak Shield switches on: it saves your streak once if you miss up to 7 days, then recharges after 7 days in a row. " +
                        "Nothing is uploaded. Every 10 days of your Purrcast streak earns a special Cat Club wallpaper here: Purrcast tells you when one is ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SiblingApp("Purrcast", "Cat-themed weather and widgets", Trio.PURRCAST, latest["purrcast"],
            "https://github.com/VK-VfX/nikezverapps/tree/main/purrcast/dist")
        Spacer(Modifier.height(12.dp))
        SiblingApp("Maps and Cats", "Maps, navigation and cats to catch", Trio.MAPS, latest["mapsandcats"],
            "https://github.com/VK-VfX/nikezverapps/releases")
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun AboutLink(title: String, sub: String, url: String) {
    val context = LocalContext.current
    LeafCard(Modifier.fillMaxWidth().clickable {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }, shape = LeafSm) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A sibling app: opens it when installed (and says when a newer version is out), otherwise its download page. */
@Composable
private fun SiblingApp(name: String, sub: String, pkg: String, latest: String?, url: String) {
    val context = LocalContext.current
    val installed = context.packageManager.getLaunchIntentForPackage(pkg) != null
    val have = if (installed) Trio.installedVersion(context, pkg) else null
    val outdated = have != null && latest != null && Trio.isNewer(latest, have)
    LeafCard(Modifier.fillMaxWidth().clickable {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }, shape = LeafSm) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (have != null) {
                    Text(
                        if (outdated) "v$have · v$latest is out" else "v$have · up to date",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (outdated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(if (!installed) "Get" else if (outdated) "Update" else "Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The log catcher: share or clear the diagnostic log (kept on the phone, never sent anywhere by itself). */
@Composable
private fun LogCard() {
    val context = LocalContext.current
    LeafCard(Modifier.fillMaxWidth(), shape = LeafSm) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Log for bug reports", style = MaterialTheme.typography.titleMedium)
                Text("Stays on this phone until you share it. No places or searches.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { com.nikopick.zamoled.data.Diag.share(context) }) { Text("Share") }
            TextButton(onClick = { com.nikopick.zamoled.data.Diag.clear(); android.widget.Toast.makeText(context, "Log cleared", android.widget.Toast.LENGTH_SHORT).show() }) { Text("Clear") }
        }
    }
}

/** Open-source notices: what Zamoled is built on. */
@Composable
private fun NoticesCard() {
    var open by remember { mutableStateOf(false) }
    LeafCard(Modifier.fillMaxWidth().clickable { open = true }, shape = LeafSm) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("Open-source notices", style = MaterialTheme.typography.titleMedium)
            Text("What Zamoled is built on", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("OK") } },
            title = { Text("Open-source notices") },
            text = {
                Text(
                    "Night Photos: photographs from Unsplash (Jeremy Thomas, Hans, Arthur Osipyan, Theo, Adrian Balasoiu, Connor Carruthers), Unsplash License, changed to pure black.\n\n" +
                        "Cat artwork: Noto Emoji, © Google, Apache-2.0 (via svgmoji, MIT).\n\n" +
                        "AndroidX, Jetpack Compose, WorkManager, Kotlin and coroutines: Apache-2.0.\n\n" +
                        "Full list: github.com/VK-VfX/nikezverapps, NOTICE.md",
                )
            },
        )
    }
}
