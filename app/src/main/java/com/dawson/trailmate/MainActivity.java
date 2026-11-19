package com.dawson.trailmate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private EditText tripNameInput;
    private EditText tripNotesInput;
    private Button startTripButton;
    private Button pauseTripButton;
    private Button historyButton;
    private TextView locationStatusText;
    private TextView tripStatusText;

    private LocationManager locationManager;
    private TripDatabaseHelper database;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Location> routePoints = new ArrayList<>();

    private boolean tripActive = false;
    private boolean tripPaused = false;
    private String activeTripName = "";
    private String activeTripNotes = "";
    private String pendingTripName = "";
    private String pendingTripNotes = "";
    private long tripStartTimeMillis = 0;
    private long pauseStartedAtMillis = 0;
    private long accumulatedPauseMillis = 0;
    private float distanceMeters = 0f;
    private Location lastLocation;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!tripActive || tripPaused) {
                return;
            }

            updateTripTimer();
            handler.postDelayed(this, 1000);
        }
    };

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (!tripActive || tripPaused) {
                return;
            }

            if (location.hasAccuracy() && location.getAccuracy() > 50f) {
                locationStatusText.setText(
                        "GPS signal is weak. Waiting for a better reading."
                );
                return;
            }

            if (lastLocation != null) {
                float segmentMeters = lastLocation.distanceTo(location);

                if (segmentMeters >= 1f && segmentMeters <= 300f) {
                    distanceMeters += segmentMeters;
                }
            }

            lastLocation = new Location(location);
            routePoints.add(new Location(location));

            locationStatusText.setText(
                    String.format(
                            Locale.US,
                            "GPS active • %s • %d fixes",
                            formatDistance(distanceMeters),
                            routePoints.size()
                    )
            );

            updateTripTimer();
        }

        @Override
        public void onStatusChanged(
                String provider,
                int status,
                Bundle extras
        ) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
            if (locationStatusText != null) {
                locationStatusText.setText(
                        "Location is off. Turn it on to record your hike."
                );
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tripNameInput = findViewById(R.id.tripNameInput);
        tripNotesInput = findViewById(R.id.tripNotesInput);
        startTripButton = findViewById(R.id.startTripButton);
        pauseTripButton = findViewById(R.id.pauseTripButton);
        historyButton = findViewById(R.id.historyButton);
        locationStatusText = findViewById(R.id.locationStatusText);
        tripStatusText = findViewById(R.id.tripStatusText);

        locationManager =
                (LocationManager) getSystemService(LOCATION_SERVICE);
        database = new TripDatabaseHelper(this);

        locationPermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestMultiplePermissions(),
                        this::onLocationPermissionResult
                );

        startTripButton.setOnClickListener(view -> startOrFinishTrip());
        pauseTripButton.setOnClickListener(view -> toggleTripPause());
        historyButton.setOnClickListener(view ->
                startActivity(
                        new Intent(
                                MainActivity.this,
                                TripHistoryActivity.class
                        )
                )
        );
        findViewById(R.id.mapButton).setOnClickListener(view -> startActivity(new Intent(MainActivity.this, TrailMapActivity.class)));
        findViewById(R.id.settingsButton).setOnClickListener(view -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
    }

    private void startOrFinishTrip() {
        if (tripActive) {
            finishTrip();
            return;
        }

        String enteredName = tripNameInput.getText().toString().trim();

        if (enteredName.isEmpty()) {
            tripNameInput.setError("Enter a name for your hike");
            tripNameInput.requestFocus();
            return;
        }

        pendingTripName = enteredName;
        pendingTripNotes = tripNotesInput.getText().toString().trim();

        if (hasLocationPermission()) {
            startTripWithLocation(pendingTripName, pendingTripNotes);
        } else {
            locationPermissionLauncher.launch(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }
            );
        }
    }

    private void onLocationPermissionResult(Map<String, Boolean> results) {
        if (hasLocationPermission()) {
            startTripWithLocation(pendingTripName, pendingTripNotes);
        } else {
            locationStatusText.setText(
                    "Location permission is needed to record a hike."
            );
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String chooseLocationProvider() {
        try {
            boolean hasFinePermission =
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;

            if (hasFinePermission
                    && locationManager.isProviderEnabled(
                    LocationManager.GPS_PROVIDER
            )) {
                return LocationManager.GPS_PROVIDER;
            }

            if (locationManager.isProviderEnabled(
                    LocationManager.NETWORK_PROVIDER
            )) {
                return LocationManager.NETWORK_PROVIDER;
            }
        } catch (SecurityException ignored) {
        }

        return null;
    }

    private boolean requestLocationUpdates(String provider) {
        try {
            locationManager.requestLocationUpdates(
                    provider,
                    3000L,
                    2f,
                    locationListener,
                    Looper.getMainLooper()
            );
            return true;
        } catch (SecurityException error) {
            return false;
        }
    }

    private void removeLocationUpdates() {
        try {
            locationManager.removeUpdates(locationListener);
        } catch (SecurityException ignored) {
        }
    }

    private void startTrackingService() {
        Intent serviceIntent =
                new Intent(this, TrailTrackingService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopTrackingService() {
        stopService(new Intent(this, TrailTrackingService.class));
    }

    private void startTripWithLocation(String tripName, String tripNotes) {
        String provider = chooseLocationProvider();

        if (provider == null) {
            locationStatusText.setText(
                    "Turn on Location in device settings, then try again."
            );
            return;
        }

        activeTripName = tripName;
        activeTripNotes = tripNotes;
        tripActive = true;
        tripPaused = false;
        tripStartTimeMillis = System.currentTimeMillis();
        pauseStartedAtMillis = 0;
        accumulatedPauseMillis = 0;
        distanceMeters = 0f;
        lastLocation = null;
        routePoints.clear();

        startTrackingService();

        if (!requestLocationUpdates(provider)) {
            stopTrackingService();
            tripActive = false;
            activeTripName = "";
            activeTripNotes = "";
            locationStatusText.setText(
                    "Location permission is needed to record a hike."
            );
            return;
        }

        tripNameInput.setEnabled(false);
        tripNotesInput.setEnabled(false);
        startTripButton.setText("Finish hike");
        pauseTripButton.setText("Pause hike");
        pauseTripButton.setVisibility(View.VISIBLE);
        locationStatusText.setText("Waiting for GPS...");

        updateTripTimer();
        handler.postDelayed(timerRunnable, 1000);
    }

    private void toggleTripPause() {
        if (!tripActive) {
            return;
        }

        if (!tripPaused) {
            tripPaused = true;
            pauseStartedAtMillis = System.currentTimeMillis();
            handler.removeCallbacks(timerRunnable);
            removeLocationUpdates();
            stopTrackingService();
            pauseTripButton.setText("Resume hike");
            locationStatusText.setText("Hike paused. GPS tracking is paused.");
            updateTripTimer();
            return;
        }

        String provider = chooseLocationProvider();

        if (provider == null) {
            locationStatusText.setText(
                    "Turn on Location before resuming your hike."
            );
            return;
        }

        startTrackingService();

        if (!requestLocationUpdates(provider)) {
            stopTrackingService();
            locationStatusText.setText(
                    "Location permission is needed to resume tracking."
            );
            return;
        }

        accumulatedPauseMillis +=
                System.currentTimeMillis() - pauseStartedAtMillis;
        pauseStartedAtMillis = 0;
        tripPaused = false;

        pauseTripButton.setText("Pause hike");
        locationStatusText.setText("GPS tracking resumed.");
        updateTripTimer();
        handler.postDelayed(timerRunnable, 1000);
    }

    private void finishTrip() {
        long completedAt = System.currentTimeMillis();
        long durationMillis = getElapsedTimeMillis();
        int pointCount = routePoints.size();

        tripActive = false;
        tripPaused = false;
        handler.removeCallbacks(timerRunnable);
        removeLocationUpdates();
        stopTrackingService();

        long savedTripId = database.saveTrip(
                activeTripName,
                activeTripNotes,
                durationMillis,
                distanceMeters,
                pointCount,
                completedAt
        );

        int savedPointCount = 0;

        if (savedTripId >= 0) {
            savedPointCount = database.saveRoutePoints(
                    savedTripId,
                    routePoints
            );
        }

        tripStatusText.setText(
                "Finished hike: " + activeTripName
                        + "\nTime: " + formatDuration(durationMillis)
                        + "\nDistance: " + formatDistance(distanceMeters)
        );

        if (savedTripId >= 0) {
            locationStatusText.setText(
                    "Hike saved. GPS fixes recorded: " + savedPointCount
            );
        } else {
            locationStatusText.setText(
                    "Hike finished, but it could not be saved."
            );
        }

        tripNameInput.setEnabled(true);
        tripNotesInput.setEnabled(true);
        tripNameInput.setText("");
        tripNotesInput.setText("");
        startTripButton.setText("Start a hike");
        pauseTripButton.setVisibility(View.GONE);
        activeTripName = "";
        activeTripNotes = "";
    }

    private long getElapsedTimeMillis() {
        long currentTime = System.currentTimeMillis();
        long currentPause = tripPaused
                ? currentTime - pauseStartedAtMillis
                : 0;

        return Math.max(
                0,
                currentTime
                        - tripStartTimeMillis
                        - accumulatedPauseMillis
                        - currentPause
        );
    }

    private void updateTripTimer() {
        String status = tripPaused
                ? "Hike paused: "
                : "Hike active: ";

        tripStatusText.setText(
                status + activeTripName
                        + "\nElapsed time: "
                        + formatDuration(getElapsedTimeMillis())
                        + "\nDistance: " + formatDistance(distanceMeters)
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
        boolean useMiles = getSharedPreferences(
                SettingsActivity.PREFERENCES_NAME,
                MODE_PRIVATE
        ).getBoolean(SettingsActivity.KEY_USE_MILES, true);

        if (useMiles) {
            double miles = meters / 1609.344;
            return String.format(Locale.US, "%.2f mi", miles);
        } else {
            double kilometers = meters / 1000.0;
            return String.format(Locale.US, "%.2f km", kilometers);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timerRunnable);

        if (tripActive && isFinishing()) {
            finishTrip();
        } else if (!tripActive) {
            removeLocationUpdates();
            stopTrackingService();
        }

        if (database != null) {
            database.close();
        }

        super.onDestroy();
    }
}