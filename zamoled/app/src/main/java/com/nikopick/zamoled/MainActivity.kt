package com.nikopick.zamoled

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.nikopick.zamoled.data.Screen
import com.nikopick.zamoled.gen.CodeSource
import com.nikopick.zamoled.data.Prefs
import com.nikopick.zamoled.data.Updates
import com.nikopick.zamoled.ui.ThumbCache
import com.nikopick.zamoled.data.ShareLink
import com.nikopick.zamoled.ui.ZamoledApp
import com.nikopick.zamoled.ui.ZamoledTheme

class MainActivity : ComponentActivity() {
    /** The key of a wallpaper that arrived by shared link and has not been shown yet. */
    private val linked = mutableStateOf<String?>(null)

    override fun onStart() {
        super.onStart()
        com.nikopick.zamoled.data.HeroSwitch.refresh(this)
        com.nikopick.zamoled.data.StreakStore.touch(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        com.nikopick.zamoled.data.Diag.init(this)
        com.nikopick.zamoled.data.Diag.log("start", "Zamoled ${com.nikopick.zamoled.data.Updates.installedVersion} Android ${android.os.Build.VERSION.RELEASE} ${android.os.Build.MODEL}")
        Screen.init(this)
        com.nikopick.zamoled.data.StreakStore.touch(this)
        CodeSource.init(assets)
        com.nikopick.zamoled.gen.PhotoPack.init(assets)
        com.nikopick.zamoled.gen.HeroPack.init(assets)
        ThumbCache.init(this)
        Updates.schedule(this, getSharedPreferences("zamoled", MODE_PRIVATE).getBoolean(Prefs.AUTO_UPDATE, true))
        if (savedInstanceState == null) readLink(intent)
        setContent {
            ZamoledTheme {
                ZamoledApp(linkedKey = linked.value, onLinkShown = { linked.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readLink(intent)
    }

    private fun readLink(intent: Intent?) {
        ShareLink.parse(intent?.dataString)?.let { linked.value = it.key }
    }
}
