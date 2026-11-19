package com.dawson.trailmate;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;

import java.util.List;

public class TripDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "trailmate.db";
    private static final int DATABASE_VERSION = 3;

    private static final String TABLE_TRIPS = "trips";
    private static final String TABLE_ROUTE_POINTS = "route_points";

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_NOTES = "notes";
    public static final String COLUMN_DURATION = "duration_millis";
    public static final String COLUMN_DISTANCE = "distance_meters";
    public static final String COLUMN_POINT_COUNT = "point_count";
    public static final String COLUMN_COMPLETED_AT = "completed_at";

    public static final String COLUMN_ROUTE_TRIP_ID = "trip_id";
    public static final String COLUMN_SEQUENCE = "sequence_number";
    public static final String COLUMN_LATITUDE = "latitude";
    public static final String COLUMN_LONGITUDE = "longitude";
    public static final String COLUMN_ACCURACY = "accuracy";
    public static final String COLUMN_RECORDED_AT = "recorded_at";

    private static final String CREATE_TRIPS_TABLE =
            "CREATE TABLE " + TABLE_TRIPS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_NAME + " TEXT NOT NULL, " +
                    COLUMN_NOTES + " TEXT NOT NULL DEFAULT '', " +
                    COLUMN_DURATION + " INTEGER NOT NULL, " +
                    COLUMN_DISTANCE + " REAL NOT NULL, " +
                    COLUMN_POINT_COUNT + " INTEGER NOT NULL, " +
                    COLUMN_COMPLETED_AT + " INTEGER NOT NULL" +
                    ")";

    private static final String CREATE_ROUTE_POINTS_TABLE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_ROUTE_POINTS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_ROUTE_TRIP_ID + " INTEGER NOT NULL, " +
                    COLUMN_SEQUENCE + " INTEGER NOT NULL, " +
                    COLUMN_LATITUDE + " REAL NOT NULL, " +
                    COLUMN_LONGITUDE + " REAL NOT NULL, " +
                    COLUMN_ACCURACY + " REAL NOT NULL, " +
                    COLUMN_RECORDED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + COLUMN_ROUTE_TRIP_ID + ") REFERENCES " +
                    TABLE_TRIPS + "(" + COLUMN_ID + ") ON DELETE CASCADE" +
                    ")";

    public TripDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase database) {
        super.onConfigure(database);
        database.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(CREATE_TRIPS_TABLE);
        database.execSQL(CREATE_ROUTE_POINTS_TABLE);
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase database,
            int oldVersion,
            int newVersion
    ) {
        if (oldVersion < 2) {
            database.execSQL(CREATE_ROUTE_POINTS_TABLE);
        }

        if (oldVersion < 3) {
            database.execSQL(
                    "ALTER TABLE " + TABLE_TRIPS +
                            " ADD COLUMN " + COLUMN_NOTES +
                            " TEXT NOT NULL DEFAULT ''"
            );
        }
    }

    public long saveTrip(
            String name,
            String notes,
            long durationMillis,
            float distanceMeters,
            int pointCount,
            long completedAt
    ) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_NOTES, notes);
        values.put(COLUMN_DURATION, durationMillis);
        values.put(COLUMN_DISTANCE, distanceMeters);
        values.put(COLUMN_POINT_COUNT, pointCount);
        values.put(COLUMN_COMPLETED_AT, completedAt);

        return getWritableDatabase().insert(TABLE_TRIPS, null, values);
    }

    public int updateTripDetails(long tripId, String name, String notes) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name.trim());
        values.put(COLUMN_NOTES, notes == null ? "" : notes.trim());

        return getWritableDatabase().update(
                TABLE_TRIPS,
                values,
                COLUMN_ID + "=?",
                new String[]{String.valueOf(tripId)}
        );
    }

    public int deleteTrip(long tripId) {
        SQLiteDatabase database = getWritableDatabase();
        String[] selectionArgs = {String.valueOf(tripId)};

        database.beginTransaction();
        try {
            database.delete(
                    TABLE_ROUTE_POINTS,
                    COLUMN_ROUTE_TRIP_ID + "=?",
                    selectionArgs
            );

            int deletedTrips = database.delete(
                    TABLE_TRIPS,
                    COLUMN_ID + "=?",
                    selectionArgs
            );

            database.setTransactionSuccessful();
            return deletedTrips;
        } finally {
            database.endTransaction();
        }
    }

    public int saveRoutePoints(long tripId, List<Location> points) {
        SQLiteDatabase database = getWritableDatabase();
        int insertedCount = 0;

        database.beginTransaction();
        try {
            for (int i = 0; i < points.size(); i++) {
                Location point = points.get(i);
                ContentValues values = new ContentValues();

                values.put(COLUMN_ROUTE_TRIP_ID, tripId);
                values.put(COLUMN_SEQUENCE, i);
                values.put(COLUMN_LATITUDE, point.getLatitude());
                values.put(COLUMN_LONGITUDE, point.getLongitude());
                values.put(
                        COLUMN_ACCURACY,
                        point.hasAccuracy() ? point.getAccuracy() : -1f
                );
                values.put(COLUMN_RECORDED_AT, point.getTime());

                database.insertOrThrow(TABLE_ROUTE_POINTS, null, values);
                insertedCount++;
            }

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }

        return insertedCount;
    }

    public Cursor getAllTrips() {
        return getReadableDatabase().query(
                TABLE_TRIPS,
                null,
                null,
                null,
                null,
                null,
                COLUMN_COMPLETED_AT + " DESC"
        );
    }

    public Cursor getRoutePoints(long tripId) {
        return getReadableDatabase().query(
                TABLE_ROUTE_POINTS,
                null,
                COLUMN_ROUTE_TRIP_ID + "=?",
                new String[]{String.valueOf(tripId)},
                null,
                null,
                COLUMN_SEQUENCE + " ASC"
        );
    }
}