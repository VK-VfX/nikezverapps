package com.nikopick.zamoled.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nikopick.zamoled.data.ApkInstaller
import com.nikopick.zamoled.data.Prefs
import com.nikopick.zamoled.data.UpdateInfo
import com.nikopick.zamoled.data.UpdateResult
import com.nikopick.zamoled.data.Updates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class OtaPhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, ERROR }

/** Where the update check stands, shared by the banner on the home screen and the Settings screen. */
@Stable
class OtaState(private val context: Context, private val scope: CoroutineScope, private val prefs: Prefs) {
    var phase by mutableStateOf(OtaPhase.IDLE)
        private set
    var info by mutableStateOf<UpdateInfo?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** The banner was closed for this session. */
    var dismissed by mutableStateOf(false)

    /** True while the new version is downloading or being handed to Android's installer. */
    var installing by mutableStateOf(false)
        private set
    var progress by mutableStateOf<String?>(null)
        private set

    val available: Boolean get() = info != null && phase == OtaPhase.AVAILABLE

    /** True while the home banner should be showing. */
    val showBanner: Boolean get() = available && !dismissed && prefs.skippedVersion != info?.version

    fun check(manual: Boolean = false) {
        if (phase == OtaPhase.CHECKING) return
        scope.launch {
            phase = OtaPhase.CHECKING
            message = null
            when (val r = Updates.check()) {
                is UpdateResult.Available -> {
                    info = r.info
                    phase = OtaPhase.AVAILABLE
                    if (manual) dismissed = false
                }
                UpdateResult.UpToDate -> { info = null; phase = if (manual) OtaPhase.UP_TO_DATE else OtaPhase.IDLE }
                UpdateResult.Failed -> {
                    phase = if (manual) OtaPhase.ERROR else OtaPhase.IDLE
                    if (manual) message = "Could not reach GitHub. Check your connection."
                }
            }
        }
    }

    private fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Downloads the new APK inside the app, checks it and hands it to Android's installer, which asks you to confirm. */
    fun download() {
        val i = info ?: return
        if (installing) return
        if (!ApkInstaller.canInstall(context)) {
            message = "Allow Zamoled to install apps, then tap Install again."
            ApkInstaller.openInstallPermission(context)
            return
        }
        scope.launch {
            installing = true
            message = null
            progress = "Downloading…"
            try {
                val file = ApkInstaller.download(context, i) { progress = "Downloading $it%" }
                progress = "Installing…"
                ApkInstaller.install(context, file)
                progress = "Waiting for you to confirm the install"
            } catch (e: Exception) {
                com.nikopick.zamoled.data.Diag.error("update-install", e)
                progress = null
                message = e.message ?: "The update did not finish."
            } finally {
                installing = false
            }
        }
    }

    fun openReleasePage() {
        info?.let { open(it.pageUrl) }
    }

    fun skipVersion() {
        info?.let { prefs.skip(it.version) }
    }
}
