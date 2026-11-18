package com.nikopick.zamoled.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.CalendarContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nikopick.zamoled.data.ProgressStore
import com.nikopick.zamoled.gen.SaveCode
import com.nikopick.zamoled.gen.SaveCodeResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The "Progress" block in Settings: save the streak code (notes, card, calendar, file, copy) and restore it. */
@Composable
fun ProgressBlock() {
    val context = LocalContext.current
    var showSave by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    var pendingBody by remember { mutableStateOf<String?>(null) }
    var withSettings by remember { mutableStateOf(false) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    val saveTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let { write(context, it, pendingBody) }; pendingBody = null }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { write(context, it, pendingBody) }; pendingBody = null }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().take(1_000_000).toByteArray().toString(Charsets.UTF_8) } }.getOrNull()
            toast(context, if (text == null) "Couldn't open that file." else runCatching {
                if (text.trimStart().startsWith("{")) ProgressStore.restoreBackupJson(context, text, withSettings) else ProgressStore.restoreFromText(context, text)
            }.getOrElse { it.message ?: "Couldn't restore that." })
        }
    }

    LeafCard(Modifier.fillMaxWidth().clickable { showSave = true }, shape = LeafSm) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("Save my progress", style = MaterialTheme.typography.titleMedium)
            Text("A short code for your streak. Keep it in notes, a screenshot or your calendar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(12.dp))
    LeafCard(Modifier.fillMaxWidth().clickable { showRestore = true }, shape = LeafSm) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("Restore progress", style = MaterialTheme.typography.titleMedium)
            Text("Paste a code or open a backup file. Nothing ever goes down.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showSave) {
        val code = remember { ProgressStore.code(context) }
        AlertDialog(
            onDismissRequest = { showSave = false },
            confirmButton = { TextButton(onClick = { showSave = false }) { Text("Close") } },
            title = { Text("Keep your streak safe") },
            text = {
                Column {
                    Text(code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    val items = listOf(
                        "Share to notes or chat" to { share(context, "Zamoled progress code:\n$code") },
                        "Save a screenshot card" to { saveCard(context, code) },
                        "Add to calendar" to { addCalendar(context, code) },
                        "Save as a text file" to { pendingBody = "Zamoled progress code\n$code\n"; runCatching { saveTxt.launch("zamoled-progress-$today.txt") } },
                        "Full backup file (favorites and settings)" to { pendingBody = ProgressStore.backupJson(context); runCatching { saveJson.launch("zamoled-backup-$today.json") } },
                        "Copy" to { copy(context, code) },
                    )
                    items.forEach { (label, act) ->
                        TextButton(onClick = { showSave = false; runCatching(act).onFailure { toast(context, "No app found for that. Try Copy.") } }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
        )
    }

    if (showRestore) {
        var text by remember { mutableStateOf(clipboardCode(context)) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showRestore = false },
            title = { Text("Restore progress") },
            text = {
                Column {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it; error = null }, minLines = 3, isError = error != null,
                        placeholder = { Text("ZAM1-XXXX-XXXX-...", fontFamily = FontFamily.Monospace) },
                        supportingText = { error?.let { Text(it) } },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Checkbox(checked = withSettings, onCheckedChange = { withSettings = it })
                        Text("Also restore my settings (backup files only)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { ProgressStore.restoreFromText(context, text) }
                        .onSuccess { showRestore = false; toast(context, it) }
                        .onFailure { error = it.message }
                }) { Text("Restore") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showRestore = false; runCatching { openFile.launch(arrayOf("*/*")) } }) { Text("Open file") }
                    TextButton(onClick = { showRestore = false }) { Text("Cancel") }
                }
            },
        )
    }
}

private fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_LONG).show()

private fun write(c: Context, uri: Uri, body: String?) {
    if (body == null) return
    toast(c, if (runCatching { c.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) } }.isSuccess) "Saved." else "Couldn't save that file.")
}

private fun share(c: Context, text: String) {
    c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Keep your progress code").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copy(c: Context, text: String) {
    (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Zamoled progress code", text))
    toast(c, "Copied. Paste it somewhere safe.")
}

private fun addCalendar(c: Context, code: String) {
    val now = System.currentTimeMillis()
    c.startActivity(
        Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "Zamoled progress code").putExtra(CalendarContract.Events.DESCRIPTION, code)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, now).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, now)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun clipboardCode(c: Context): String = runCatching {
    val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val t = cm.primaryClip?.getItemAt(0)?.coerceToText(c)?.toString().orEmpty()
    if (t.length < 5000 && SaveCode.decode(t, SaveCode.ZAM) is SaveCodeResult.Ok) t else ""
}.getOrDefault("")

/** A 1080 x 1350 black card: the app, the date and the code in big mono type; saved to Pictures/Trio (Android 10+, Zamoled's minimum). */
private fun saveCard(c: Context, code: String) {
    runCatching {
        val b = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.drawColor(0xFF000000.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF3DF5D0.toInt(); p.textSize = 64f; p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        cv.drawText("Zamoled progress", 80f, 190f, p)
        p.color = 0xFFAEAEB8.toInt(); p.textSize = 36f; p.typeface = Typeface.MONOSPACE
        cv.drawText("Saved " + SimpleDateFormat("d MMM yyyy", Locale.US).format(Date()), 80f, 250f, p)
        p.color = 0xFFF4F4F6.toInt(); p.textSize = 58f
        var line = ""
        var y = 520f
        for (g in code.split("-")) {
            val next = if (line.isEmpty()) g else "$line-$g"
            if (p.measureText(next) > 920f && line.isNotEmpty()) { cv.drawText(line, 80f, y, p); y += 92f; line = g } else line = next
        }
        if (line.isNotEmpty()) cv.drawText(line, 80f, y, p)
        p.color = 0xFFAEAEB8.toInt(); p.textSize = 38f; p.typeface = Typeface.DEFAULT
        cv.drawText("Keep this safe. Paste it into", 80f, 1120f, p)
        cv.drawText("Restore progress to get your streak back.", 80f, 1172f, p)
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "zamoled-progress-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Trio")
        }
        val uri = c.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)!!
        c.contentResolver.openOutputStream(uri)!!.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        toast(c, "Saved to Pictures/Trio.")
    }.onFailure { toast(c, "Couldn't save the picture. Try Copy.") }
}
