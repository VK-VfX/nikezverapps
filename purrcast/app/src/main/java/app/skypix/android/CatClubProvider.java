package app.skypix.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Purrcast's number for the shared Cat Club: your current streak of days. Read-only, counts only; Zamoled and
 * Maps and Cats show it next to their own. Nothing else about you is exposed.
 */
public final class CatClubProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor c = new MatrixCursor(new String[]{"value", "label"});
        if (getContext() != null) c.addRow(new Object[]{Store.streak(getContext()), Store.streakLabel(getContext())});
        return c;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.app.skypix.weather.catclub"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
