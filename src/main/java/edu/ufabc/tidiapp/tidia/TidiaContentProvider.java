package edu.ufabc.tidiapp.tidia;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.support.annotation.NonNull;

import edu.ufabc.tidiapp.R;
import hugo.weaving.DebugLog;

@SuppressWarnings("ConstantConditions")
@DebugLog
public class TidiaContentProvider extends android.content.ContentProvider {

    private Database database;

    @Override
    public boolean onCreate() {
        database = new Database(getContext());
        return true;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        TidiaContent type = TidiaContent.match(uri);
        return String.format("%s/vnd.tidiapp.%s", type.providerMime, type.providerTable);
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        TidiaContent type = TidiaContent.match(uri);
        long id = database.getWritableDatabase().insertOrThrow(type.providerTable, null, values);
        if (id >= 0)
            getContext().getContentResolver().notifyChange(uri, null, false);
        return Uri.withAppendedPath(uri, values.getAsString(TidiaContent.KEY_ID));
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        TidiaContent type = TidiaContent.match(uri);
        Cursor cursor = database.getReadableDatabase().query(type.providerTable, projection, type.getProviderSelection(selection), type.getProviderSelectionValues(selectionArgs), null, null, sortOrder);
        if (cursor != null)
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        TidiaContent type = TidiaContent.match(uri);
        int count = database.getWritableDatabase().delete(type.providerTable, type.getProviderSelection(selection), type.getProviderSelectionValues(selectionArgs));
        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        TidiaContent type = TidiaContent.match(uri);
        int count = database.getWritableDatabase().update(type.providerTable, values, type.getProviderSelection(selection), type.getProviderSelectionValues(selectionArgs));
        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }

    public static class Database extends SQLiteOpenHelper {

        Database(Context context) {
            super(context, context.getString(R.string.database_name), null, 1);
        }

        @Override
        public void onConfigure(SQLiteDatabase db) {
            db.setForeignKeyConstraintsEnabled(true);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(String.format("CREATE TABLE IF NOT EXISTS %s(%s)", TidiaContent.Site.TABLE_NAME, TidiaContent.Site.TABLE));
            db.execSQL(String.format("CREATE TABLE IF NOT EXISTS %s(%s)", TidiaContent.Assignment.TABLE_NAME, TidiaContent.Assignment.TABLE));
            db.execSQL(String.format("CREATE TABLE IF NOT EXISTS %s(%s)", TidiaContent.Resource.TABLE_NAME, TidiaContent.Resource.TABLE));
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL(String.format("DROP TABLE IF EXISTS %s", TidiaContent.Site.TABLE_NAME));
            db.execSQL(String.format("DROP TABLE IF EXISTS %s", TidiaContent.Assignment.TABLE_NAME));
            db.execSQL(String.format("DROP TABLE IF EXISTS %s", TidiaContent.Resource.TABLE_NAME));
            onCreate(db);
        }
    }
}
