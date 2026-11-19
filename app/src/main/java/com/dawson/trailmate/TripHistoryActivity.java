package com.dawson.trailmate;

import android.database.Cursor;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class TripHistoryActivity extends AppCompatActivity {

    private TripDatabaseHelper database;
    private ListView tripListView;
    private TextView emptyHistoryText;
    private TripAdapter tripAdapter;

    private final ArrayList<Long> tripIds = new ArrayList<>();
    private final ArrayList<String> tripNames = new ArrayList<>();
    private final ArrayList<String> tripNotes = new ArrayList<>();
    private final ArrayList<String> tripSummaries = new ArrayList<>();
    private final ArrayList<String> tripDetails = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_history);

        database = new TripDatabaseHelper(this);
        tripListView = findViewById(R.id.tripListView);
        emptyHistoryText = findViewById(R.id.emptyHistoryText);

        tripListView.setEmptyView(emptyHistoryText);

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(view -> finish());

        tripAdapter = new TripAdapter();
        tripListView.setAdapter(tripAdapter);

        loadTrips();
    }

    private void loadTrips() {
        tripIds.clear();
        tripNames.clear();
        tripNotes.clear();
        tripSummaries.clear();
        tripDetails.clear();

        Cursor cursor = database.getAllTrips();

        try {
            while (cursor.moveToNext()) {
                long tripId = cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                                TripDatabaseHelper.COLUMN_ID
                        )
                );

                String name = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                TripDatabaseHelper.COLUMN_NAME
                        )
                );

                String notes = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                TripDatabaseHelper.COLUMN_NOTES
                        )
                );

                long duration = cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                                TripDatabaseHelper.COLUMN_DURATION
                        )
                );

                float distance = cursor.getFloat(
                        cursor.getColumnIndexOrThrow(
                                TripDatabaseHelper.COLUMN_DISTANCE
                        )
                );

                int pointCount = cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                                TripDatabaseHelper.COLUMN_POINT_COUNT
                        )
                );

                long completedAt = cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                                TripDatabaseHelper.COLUMN_COMPLETED_AT
                        )
                );

                String date = DateFormat.getDateTimeInstance(
                        DateFormat.MEDIUM,
                        DateFormat.SHORT
                ).format(new Date(completedAt));

                if (name == null) {
                    name = "";
                }

                if (notes == null) {
                    notes = "";
                }

                String notesText = notes.isEmpty()
                        ? "No notes added."
                        : notes;

                tripIds.add(tripId);
                tripNames.add(name);
                tripNotes.add(notes);
                tripSummaries.add(
                        formatDistance(distance)
                                + " • " + formatDuration(duration)
                                + "\n" + date
                );
                tripDetails.add(
                        "Completed: " + date
                                + "\nDuration: " + formatDuration(duration)
                                + "\nDistance: " + formatDistance(distance)
                                + "\nGPS fixes: " + pointCount
                                + "\n\nNotes:\n" + notesText
                );
            }
        } finally {
            cursor.close();
        }

        tripAdapter.notifyDataSetChanged();
    }

    private void showEditTripDialog(
            long tripId,
            String currentName,
            String currentNotes
    ) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(4));

        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("Hike name");
        nameInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        nameInput.setText(currentName);

        EditText notesInput = new EditText(this);
        notesInput.setHint("Notes");
        notesInput.setMinLines(3);
        notesInput.setGravity(Gravity.TOP);
        notesInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );
        notesInput.setText(currentNotes);

        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        nameParams.bottomMargin = dp(8);

        form.addView(nameInput, nameParams);
        form.addView(
                notesInput,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        new AlertDialog.Builder(this)
                .setTitle("Edit hike")
                .setView(form)
                .setPositiveButton("Save", (dialog, which) -> {
                    String updatedName =
                            nameInput.getText().toString().trim();
                    String updatedNotes =
                            notesInput.getText().toString().trim();

                    if (updatedName.isEmpty()) {
                        Toast.makeText(
                                this,
                                "Enter a hike name.",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    int updatedRows = database.updateTripDetails(
                            tripId,
                            updatedName,
                            updatedNotes
                    );

                    if (updatedRows > 0) {
                        Toast.makeText(
                                this,
                                "Hike updated.",
                                Toast.LENGTH_SHORT
                        ).show();
                        loadTrips();
                    } else {
                        Toast.makeText(
                                this,
                                "The hike could not be updated.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmation(long tripId, String tripName) {
        new AlertDialog.Builder(this)
                .setTitle("Delete hike?")
                .setMessage(
                        "Delete \"" + tripName
                                + "\" and its saved GPS points?"
                )
                .setPositiveButton("Delete", (dialog, which) -> {
                    int deletedRows = database.deleteTrip(tripId);

                    if (deletedRows > 0) {
                        Toast.makeText(
                                this,
                                "Hike deleted.",
                                Toast.LENGTH_SHORT
                        ).show();
                        loadTrips();
                    } else {
                        Toast.makeText(
                                this,
                                "The hike could not be deleted.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTripDetails(
            long tripId,
            String title,
            String details
    ) {
        ArrayList<Location> savedPoints = new ArrayList<>();
        Cursor cursor = database.getRoutePoints(tripId);

        try {
            while (cursor.moveToNext()) {
                Location point = new Location("TrailMate");

                point.setLatitude(
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(
                                        TripDatabaseHelper.COLUMN_LATITUDE
                                )
                        )
                );

                point.setLongitude(
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(
                                        TripDatabaseHelper.COLUMN_LONGITUDE
                                )
                        )
                );

                point.setTime(
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TripDatabaseHelper.COLUMN_RECORDED_AT
                                )
                        )
                );

                float accuracy = cursor.getFloat(
                        cursor.getColumnIndexOrThrow(
                                TripDatabaseHelper.COLUMN_ACCURACY
                        )
                );

                if (accuracy >= 0f) {
                    point.setAccuracy(accuracy);
                }

                savedPoints.add(point);
            }
        } finally {
            cursor.close();
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(16));

        TextView detailsText = new TextView(this);
        detailsText.setText(details);
        detailsText.setTextSize(16f);

        content.addView(
                detailsText,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        RoutePreviewView routePreview = new RoutePreviewView(this);
        routePreview.setPoints(savedPoints);

        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(240)
                );

        previewParams.topMargin = dp(16);
        content.addView(routePreview, previewParams);
        scrollView.addView(content);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton("Close", null)
                .show();
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private String formatDuration(long durationMillis) {
        long totalSeconds = durationMillis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(
                    Locale.US,
                    "%02d:%02d:%02d",
                    hours,
                    minutes,
                    seconds
            );
        }

        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private String formatDistance(float meters) {
        double miles = meters / 1609.344;
        return String.format(Locale.US, "%.2f mi", miles);
    }

    private final class TripAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return tripIds.size();
        }

        @Override
        public Object getItem(int position) {
            return tripNames.get(position);
        }

        @Override
        public long getItemId(int position) {
            return tripIds.get(position);
        }

        @Override
        public View getView(
                int position,
                View convertView,
                ViewGroup parent
        ) {
            LinearLayout row = new LinearLayout(TripHistoryActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));

            TextView nameText = new TextView(TripHistoryActivity.this);
            nameText.setText(tripNames.get(position));
            nameText.setTextSize(18f);
            nameText.setTypeface(null, Typeface.BOLD);

            TextView summaryText = new TextView(TripHistoryActivity.this);
            summaryText.setText(tripSummaries.get(position));
            summaryText.setTextSize(14f);
            summaryText.setPadding(0, dp(4), 0, dp(4));

            long tripId = tripIds.get(position);
            String tripName = tripNames.get(position);
            String tripNote = tripNotes.get(position);
            String details = tripDetails.get(position);

            View.OnClickListener openDetails =
                    view -> showTripDetails(tripId, tripName, details);

            row.setOnClickListener(openDetails);
            nameText.setOnClickListener(openDetails);
            summaryText.setOnClickListener(openDetails);

            LinearLayout buttons = new LinearLayout(TripHistoryActivity.this);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END);

            Button editButton = new Button(TripHistoryActivity.this);
            editButton.setText("Edit");
            editButton.setOnClickListener(
                    view -> showEditTripDialog(tripId, tripName, tripNote)
            );

            Button deleteButton = new Button(TripHistoryActivity.this);
            deleteButton.setText("Delete");
            deleteButton.setOnClickListener(
                    view -> showDeleteConfirmation(tripId, tripName)
            );

            buttons.addView(editButton);
            buttons.addView(deleteButton);

            row.addView(
                    nameText,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );

            row.addView(
                    summaryText,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );

            row.addView(
                    buttons,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );

            return row;
        }
    }

    @Override
    protected void onDestroy() {
        if (database != null) {
            database.close();
        }

        super.onDestroy();
    }
}