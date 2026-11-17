package app.mapsandcats;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Maps and Cats' number for the shared Cat Club: how many cats you have caught on walks. Read-only, counts only;
 * Purrcast and Zamoled show it next to their own. The page saves the number here as it changes.
 */
public final class CatClubProvider extends ContentProvider {
    static final String PREFS = "catclub";

    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor c = new MatrixCursor(new String[]{"value", "label", "best", "last"});
        Context ctx = getContext();
        if (ctx != null) {
            android.content.SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            c.addRow(new Object[]{p.getInt("streak_days", 0), p.getString("streak_label", "day streak"), p.getInt("streak_best", 0), p.getLong("streak_last", 0)});
        }
        return c;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.app.mapsandcats.catclub"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
