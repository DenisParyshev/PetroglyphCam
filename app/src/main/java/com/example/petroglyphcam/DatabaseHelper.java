package com.example.petroglyphcam;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "petroglyphs.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_PETROGLYPHS = "petroglyphs";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_IMAGE_URI = "image_uri";
    public static final String COLUMN_DESCRIPTION = "description";
    public static final String COLUMN_PRESERVATION = "preservation";
    public static final String COLUMN_ROCK_PARAMS = "rock_params";
    public static final String COLUMN_LATITUDE = "latitude";
    public static final String COLUMN_LONGITUDE = "longitude";
    public static final String COLUMN_ALTITUDE = "altitude";
    public static final String COLUMN_DIRECTION = "direction";
    public static final String COLUMN_DATE_ADDED = "date_added";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE " + TABLE_PETROGLYPHS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_IMAGE_URI + " TEXT UNIQUE,"
                + COLUMN_DESCRIPTION + " TEXT,"
                + COLUMN_PRESERVATION + " TEXT,"
                + COLUMN_ROCK_PARAMS + " TEXT,"
                + COLUMN_LATITUDE + " REAL,"
                + COLUMN_LONGITUDE + " REAL,"
                + COLUMN_ALTITUDE + " REAL,"
                + COLUMN_DIRECTION + " REAL,"
                + COLUMN_DATE_ADDED + " INTEGER DEFAULT (strftime('%s','now')))";
        db.execSQL(CREATE_TABLE);
        Log.d(TAG, "Database created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_PETROGLYPHS + " ADD COLUMN "
                    + COLUMN_DATE_ADDED + " INTEGER DEFAULT (strftime('%s','now'))");
            Log.d(TAG, "Database upgraded from version " + oldVersion + " to " + newVersion);
        }
    }

    public long addPetroglyph(String imageUri, String description, String preservation,
                              String rockParams, Location location, float direction) {
        SQLiteDatabase db = this.getWritableDatabase();
        long id = -1;

        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_IMAGE_URI, imageUri);
            values.put(COLUMN_DESCRIPTION, description != null ? description : "");
            values.put(COLUMN_PRESERVATION, preservation != null ? preservation : "");
            values.put(COLUMN_ROCK_PARAMS, rockParams != null ? rockParams : "");

            if (location != null) {
                values.put(COLUMN_LATITUDE, location.getLatitude());
                values.put(COLUMN_LONGITUDE, location.getLongitude());
                values.put(COLUMN_ALTITUDE, location.getAltitude());
            }
            values.put(COLUMN_DIRECTION, direction);

            id = db.insertWithOnConflict(TABLE_PETROGLYPHS, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
            Log.d(TAG, "Added petroglyph with ID: " + id);
        } catch (Exception e) {
            Log.e(TAG, "Error adding petroglyph", e);
        } finally {
            db.close();
        }
        return id;
    }

    public int deletePetroglyph(String imageUri) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_PETROGLYPHS, COLUMN_IMAGE_URI + " = ?",
                new String[]{imageUri});
        db.close();
        return result;
    }

    public Cursor searchPetroglyphs(String query) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COLUMN_DESCRIPTION + " LIKE ? OR " + COLUMN_ROCK_PARAMS + " LIKE ?";
        String[] selectionArgs = new String[]{"%" + query + "%", "%" + query + "%"};
        return db.query(TABLE_PETROGLYPHS,
                null, selection, selectionArgs, null, null,
                COLUMN_DATE_ADDED + " DESC");
    }

    public Cursor getPetroglyphById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_PETROGLYPHS,
                null, COLUMN_ID + " = ?", new String[]{String.valueOf(id)},
                null, null, null);
    }

    public Cursor getAllPetroglyphs() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PETROGLYPHS,
                null, null, null, null, null,
                COLUMN_DATE_ADDED + " DESC");

        Log.d(TAG, "Получено записей из БД: " + cursor.getCount());
        return cursor;
    }

    public Cursor getPetroglyphByUri(String imageUri) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_PETROGLYPHS,
                null,
                COLUMN_IMAGE_URI + " = ?",
                new String[]{imageUri},
                null, null, null);
    }

    public int updatePetroglyph(long id, String description, String preservation, String rockParams) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_DESCRIPTION, description);
        values.put(COLUMN_PRESERVATION, preservation);
        values.put(COLUMN_ROCK_PARAMS, rockParams);

        return db.update(TABLE_PETROGLYPHS, values,
                COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    }
}