package com.nikopick.zamoled.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Zamoled's number for the shared Cat Club: its day streak, with the shield state in the label. Read-only, counts only;
 * Purrcast and Maps and Cats show it next to their own. Nothing else about you is exposed.
 */
class CatClubProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val ctx: Context = context ?: return MatrixCursor(arrayOf("value", "label"))
        return MatrixCursor(arrayOf("value", "label")).apply { addRow(arrayOf<Any>(StreakStore.read(ctx).count, StreakStore.label(ctx))) }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.com.nikopick.zamoled.catclub"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
