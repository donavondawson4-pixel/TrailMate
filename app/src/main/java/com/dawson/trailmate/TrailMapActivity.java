package com.dawson.trailmate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.location.LocationComponent;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.location.modes.CameraMode;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;

public class TrailMapActivity extends AppCompatActivity {
    private static final String MAP_STYLE_URL =
            "https://tiles.openfreemap.org/styles/liberty";

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private Style mapStyle;
    private LocationComponent locationComponent;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private boolean locationUpdatesStarted;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean fineGranted = Boolean.TRUE.equals(
                                result.get(Manifest.permission.ACCESS_FINE_LOCATION)
                        );
                        boolean coarseGranted = Boolean.TRUE.equals(
                                result.get(Manifest.permission.ACCESS_COARSE_LOCATION)
                        );

                        if (fineGranted || coarseGranted) {
                            enableCurrentLocation();
                            startLocationUpdates();
                        } else {
                            Toast.makeText(
                                    this,
                                    "Location permission was not granted.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        setContentView(R.layout.activity_map);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapLibreMap map) {
                mapLibreMap = map;
                map.setCameraPosition(
                        new CameraPosition.Builder()
                                .target(new LatLng(33.5604, -81.7198))
                                .zoom(11.0)
                                .build()
                );
                map.setStyle(MAP_STYLE_URL, style -> {
                    mapStyle = style;
                    if (hasLocationPermission()) {
                        enableCurrentLocation();
                        startLocationUpdates();
                    } else {
                        locationPermissionLauncher.launch(
                                new String[]{
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                }
                        );
                    }
                });
            }
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void enableCurrentLocation() {
        if (mapLibreMap == null || mapStyle == null || !hasLocationPermission()) {
            return;
        }

        locationComponent = mapLibreMap.getLocationComponent();

        if (!locationComponent.isLocationComponentActivated()) {
            LocationComponentActivationOptions activationOptions =
                    LocationComponentActivationOptions.builder(this, mapStyle)
                            .useDefaultLocationEngine(false)
                            .build();

            locationComponent.activateLocationComponent(activationOptions);
        }

        locationComponent.setLocationComponentEnabled(true);
        locationComponent.setCameraMode(CameraMode.TRACKING);
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (locationUpdatesStarted
                || mapLibreMap == null
                || locationComponent == null
                || !hasLocationPermission()) {
            return;
        }

        locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (locationManager == null) {
            Toast.makeText(
                    this,
                    "Location service is unavailable.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                updateCurrentLocation(location);
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }

            @Override
            public void onStatusChanged(
                    String provider,
                    int status,
                    Bundle extras
            ) {
            }
        };

        try {
            boolean gpsEnabled =
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean networkEnabled =
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            Location lastKnownLocation = null;

            if (gpsEnabled) {
                lastKnownLocation =
                        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }

            if (lastKnownLocation == null && networkEnabled) {
                lastKnownLocation =
                        locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (lastKnownLocation != null) {
                updateCurrentLocation(lastKnownLocation);
            }

            if (gpsEnabled) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        0f,
                        locationListener,
                        Looper.getMainLooper()
                );
            }

            if (networkEnabled) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        1000L,
                        0f,
                        locationListener,
                        Looper.getMainLooper()
                );
            }

            locationUpdatesStarted = gpsEnabled || networkEnabled;

            if (!locationUpdatesStarted) {
                Toast.makeText(
                        this,
                        "Turn on device location and try again.",
                        Toast.LENGTH_SHORT
                ).show();
            } else if (lastKnownLocation == null) {
                Toast.makeText(
                        this,
                        "Waiting for a location fix.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        } catch (SecurityException exception) {
            Toast.makeText(
                    this,
                    "TrailMate cannot access location.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void updateCurrentLocation(Location location) {
        if (mapLibreMap == null || locationComponent == null) {
            return;
        }

        LatLng position = new LatLng(
                location.getLatitude(),
                location.getLongitude()
        );

        locationComponent.forceLocationUpdate(location);
        mapLibreMap.setCameraPosition(
                new CameraPosition.Builder()
                        .target(position)
                        .zoom(15.0)
                        .build()
        );
    }

    private void stopLocationUpdates() {
        if (locationManager != null
                && locationListener != null
                && locationUpdatesStarted) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException exception) {
                Toast.makeText(
                        this,
                        "TrailMate could not stop location updates.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }

        locationUpdatesStarted = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) {
            mapView.onStart();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        if (mapLibreMap != null && mapStyle != null && hasLocationPermission()) {
            enableCurrentLocation();
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        stopLocationUpdates();
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mapView != null) {
            mapView.onStop();
        }
        super.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @Override
    protected void onDestroy() {
        stopLocationUpdates();
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }
}