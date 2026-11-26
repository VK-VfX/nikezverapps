package com.nikopick.depthlock

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Teal, onPrimary = Color.Black, background = Color.Black, surface = CardBg,
                    onSurface = Color.White,
                )
            ) { DepthLockApp() }
        }
    }
}

private val Teal = Color(0xFF3DF5D0)
private val CardBg = Color(0xFF111111)
private val Edge = Color(0xFF4A4A4A)
private val Soft = Color(0xFFE2E2E2)
private val Heading = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
private val Mono = FontFamily.Monospace

@Composable
private fun DepthLockApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(Prefs.load(ctx)) }
    var assets by remember { mutableStateOf(Store.load(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(if (assets.subject != null) "Photo ready. Subject is cut out." else "No photo yet. Pick one with a clear subject.")
    }
    val renderer = remember { DepthRenderer() }

    fun update(n: ClockSettings) { s = n; Prefs.save(ctx, n) }

    fun autoPlace(a: DepthAssets, base: ClockSettings, msg: String) {
        val sub = a.subject
        if (sub == null) { status = msg; return }
        val (y, cov) = AutoPlace.best(sub, base)
        update(base.copy(y = y))
        status = if (cov < 0.04f) "$msg Your subject does not reach the clock. Try Stretch, or a photo where the subject is bigger."
        else "$msg Clock tucked behind your subject (${(cov * 100).toInt()}% covered). Fine-tune with Height."
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            status = "Cutting out your subject. First time can take a minute while Google fetches the model."
            scope.launch {
                Cutout.process(ctx, uri)
                    .onSuccess { assets = Store.load(ctx); autoPlace(assets, s, "Done. Subject is cut out.") }
                    .onFailure { status = "That one did not work: ${it.message ?: "unknown error"}. Try a photo with a clearer subject." }
                busy = false
            }
        }
    }

    fun runOwn(msg: String, ok: String, job: suspend () -> Result<Unit>) {
        busy = true; status = msg
        scope.launch {
            job().onSuccess { assets = Store.load(ctx); autoPlace(assets, s, ok) }
                .onFailure { status = "That one did not work: ${it.message ?: "unknown error"}." }
            busy = false
        }
    }
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runOwn("Loading your background.", "Your background is in.") { Cutout.ownBackground(ctx, uri) }
    }
    val pickSubject = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runOwn("Loading your cut-out.", "Your cut-out is in.") { Cutout.ownSubject(ctx, uri) }
    }

    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            Looks.importFont(ctx, uri)
                .onSuccess { update(s.copy(font = Looks.CUSTOM)); status = "Your font is in." }
                .onFailure { status = "That file is not a usable font. Pick a .ttf or .otf." }
        }
    }

    val dm = ctx.resources.displayMetrics
    Column(
        Modifier.background(Color.Black).systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("DepthLock", fontFamily = Heading, fontSize = 36.sp, color = Color.White)
        Caption("Clock behind your subject. Lock screen, charging and always-on.")

        Panel("Preview") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(false, true).forEach { aod ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(
                            Modifier.fillMaxWidth().aspectRatio(dm.widthPixels.toFloat() / dm.heightPixels)
                                .clip(RoundedCornerShape(24.dp)).border(1.dp, Edge, RoundedCornerShape(24.dp))
                                .semantics { contentDescription = if (aod) "Preview of the always-on look" else "Preview of the lock screen" }
                        ) {
                            drawIntoCanvas { c ->
                                renderer.draw(
                                    c.nativeCanvas, size.width.toInt(), size.height.toInt(),
                                    if (aod) assets.forAod(s.aodMode) else assets, s, dim = if (aod) s.aodDim else 1f,
                                )
                            }
                        }
                        Caption(if (aod) "Always-on" else "Lock screen")
                    }
                }
            }
            Caption("Your screen: ${dm.widthPixels} x ${dm.heightPixels}")
            Caption(status)
            Btn(if (assets.subject == null) "Pick a photo" else "Pick another photo", enabled = !busy) {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        Panel("Your own images") {
            Caption("Already have a cut-out? Bring both layers. Use the same framing so they line up.")
            Btn("Pick my background", primary = false, enabled = !busy) {
                pickBg.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            Btn("Pick my cut-out (transparent PNG)", primary = false, enabled = !busy) {
                pickSubject.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            Btn("Remove cut-out (clock stays on top)", primary = false, enabled = !busy && assets.subject != null) {
                Store.clearSubject(ctx); assets = Store.load(ctx); status = "Cut-out removed."
            }
        }

        Panel("Clock") {
            Label("Style")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Stacked", s.style == 1, FontFamily.Default) { update(s.copy(style = 1, stretch = 1f)) }
                Choice("One line", s.style == 0, FontFamily.Default) { update(s.copy(style = 0, stretch = 1.7f)) }
            }
            Btn("Auto-place behind my subject", primary = false, enabled = assets.subject != null) {
                autoPlace(assets, s, "Placed.")
            }
            Label("Font")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Looks.fontNames.forEachIndexed { i, name ->
                    if (i != Looks.CUSTOM || Looks.hasCustom()) {
                        Choice(name, s.font == i, FontFamily(Looks.typeface(i, s.weight))) { update(s.copy(font = i)) }
                    }
                }
            }
            SliderRow("Weight", s.weight.toFloat(), 100f..900f, "${s.weight}") { update(s.copy(weight = (it / 10).toInt() * 10)) }
            Btn("Use my own font file (.ttf or .otf)", primary = false) { pickFont.launch(arrayOf("*/*")) }
            Label("Colours")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Looks.palettes.forEachIndexed { i, pal ->
                    val sel = s.color == i
                    Row(
                        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                            .border(if (sel) 2.dp else 1.dp, if (sel) Teal else Edge, RoundedCornerShape(16.dp))
                            .clickable { update(s.copy(color = i)) }
                            .semantics { role = Role.RadioButton; selected = sel; contentDescription = pal.name }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(pal.top)))
                        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(pal.bottom)))
                        Text(pal.name, fontSize = 15.sp, color = if (sel) Teal else Color.White)
                    }
                }
            }
            SliderRow("Size", s.size, 0.5f..1.1f, "${(s.size * 100).toInt()}%") { update(s.copy(size = it)) }
            SliderRow("Stretch (taller digits)", s.stretch, 0.8f..2.6f, "${(s.stretch * 100).toInt()}%") { update(s.copy(stretch = it)) }
            SliderRow("Height", s.y, 0.10f..0.60f, "${(s.y * 100).toInt()}%") { update(s.copy(y = it)) }
            SliderRow("Opacity", s.alpha, 0.3f..1f, "${(s.alpha * 100).toInt()}%") { update(s.copy(alpha = it)) }
            SwitchRow("24-hour time", s.h24) { update(s.copy(h24 = it)) }
            SwitchRow("Show date", s.date) { update(s.copy(date = it)) }
            SwitchRow("Tilt depth (layers move as you tilt)", s.tilt) { update(s.copy(tilt = it)) }
            SwitchRow("Clock on home screen too", s.clockOnHome) { update(s.copy(clockOnHome = it)) }
        }

        Panel("Always-on look") {
            Caption("Used by the screen saver, the always-on screen, and the wallpaper where your phone dims it.")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Wallpaper", s.aodMode == 0, FontFamily.Default) { update(s.copy(aodMode = 0)) }
                Choice("Subject only", s.aodMode == 1, FontFamily.Default) { update(s.copy(aodMode = 1)) }
                Choice("Clock only", s.aodMode == 2, FontFamily.Default) { update(s.copy(aodMode = 2)) }
            }
            SliderRow("Brightness", s.aodDim, 0.1f..0.9f, "${(s.aodDim * 100).toInt()}%") { update(s.copy(aodDim = it)) }
        }

        Panel("Use it") {
            Btn("Set as lock screen wallpaper", enabled = assets.subject != null) { setWallpaper(ctx) }
            Caption("On the preview screen, choose Lock screen.")
            Btn("Screen saver while charging", primary = false) { openDreamSettings(ctx) }
            Caption("Pick DepthLock under Screen saver. Shows a dim copy on the charger.")
            Btn("Always-on screen", primary = false, enabled = assets.subject != null) {
                ctx.startActivity(Intent(ctx, AodActivity::class.java))
            }
            Caption("Keeps the screen on at low brightness over the lock screen. Tap to close. Uses battery.")
        }

        Panel("Good to know") {
            Caption("Pixel draws its own clock above every wallpaper on the lock screen. If you see two clocks, shrink or restyle the system one under Wallpaper and style.")
            Caption("Pixel's built-in always-on display is drawn by the system. No app can replace it, which is why the screen saver and always-on screen exist.")
        }
    }
}

private fun setWallpaper(ctx: Context) {
    val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(ctx, DepthWallpaperService::class.java))
    ctx.startActivity(i)
}

private fun openDreamSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
    } catch (_: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardBg)
            .border(1.dp, Edge, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title.uppercase(), fontFamily = Heading, fontSize = 20.sp, color = Color.White)
        content()
    }
}

@Composable
private fun Caption(t: String) = Text(t, fontFamily = Mono, fontSize = 13.sp, lineHeight = 19.sp, color = Soft)

@Composable
private fun Label(t: String) = Text(t, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)

@Composable
private fun Btn(label: String, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val mod = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    val shape = RoundedCornerShape(16.dp)
    if (primary) {
        Button(onClick, mod, enabled, shape, colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.Black)) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick, mod, enabled, shape,
            border = androidx.compose.foundation.BorderStroke(2.dp, if (enabled) Teal else Edge),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal),
        ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, family: FontFamily, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Teal else Edge, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton; this.selected = selected }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = family, fontSize = 17.sp, color = if (selected) Teal else Color.White) }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
            Text(shown, fontFamily = Mono, fontSize = 14.sp, color = Soft)
        }
        Slider(
            value, onChange, Modifier.heightIn(min = 48.dp), valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Teal, activeTrackColor = Teal, inactiveTrackColor = Edge),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked, null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black, checkedTrackColor = Teal,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF2A2A2A), uncheckedBorderColor = Soft,
            ),
        )
    }
}
