package com.example.maps;

import static com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapColorScheme;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.maps.databinding.ActivityMapsBinding;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap map;
    private ActivityMapsBinding binding;
    private LocationCallback locationCallback;
    private PlacesClient placesClient;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private boolean locationPermissionGranted;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private Location lastKnownLocation;
    private CameraPosition cameraPosition;
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";
    private Polygon main_mask;
    private List<LatLng> hole;

    //Customizable configurations
    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final int DEFAULT_ZOOM = 18;
    private final LatLng defaultLocation = new LatLng(25.0260079, 121.5381223);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Construct a FusedLocationProviderClient.
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Prompt the user for permission.
        getLocationPermission();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    lastKnownLocation = location;
                    double lon = location.getLongitude();
                    if (lon < 121.539090) {
                        Toast.makeText(MapsActivity.this,
                                "Longitude safe",
                                Toast.LENGTH_SHORT).show();
                    }
                    if (lon > 121.539090) {
                        Toast.makeText(MapsActivity.this,
                                "Longitude exceeds 121.539090!",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };

    }

    /**
     * Saves the state of the map when the activity is paused.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (map != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, map.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, lastKnownLocation);
        }
        super.onSaveInstanceState(outState);
    }


    /**
     * Prompts the user for permission to use the device location.
     */
    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    private void updateLocationUI() {
        if (map == null) {
            return;
        }
        try {
            if (locationPermissionGranted) {
                map.setMyLocationEnabled(true);
                map.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                map.setMyLocationEnabled(false);
                map.getUiSettings().setMyLocationButtonEnabled(false);
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    /**
     * Gets the current location of the device, and positions the map's camera.
     */
    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (locationPermissionGranted) {
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            lastKnownLocation = task.getResult();
                            if (lastKnownLocation != null) {
                                updatePolygons();
                                map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(lastKnownLocation.getLatitude(),
                                                lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                            }
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            map.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
                            map.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage(), e);
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        locationPermissionGranted = false;
        if (requestCode
                == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true;
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
        updateLocationUI();
        getDeviceLocation();
    }

    /**
     * Manipulates the map once available.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        // Set map to dark mode
        map.setMapColorScheme(MapColorScheme.DARK);
        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();
        // Get the current location of the device and set the position of the map.
        getDeviceLocation();
        // Turn off all default place labels.
        String mapStyleJson = "[\n" +
                "  {\n" +
                "    \"featureType\": \"poi\",\n" +
                "    \"elementType\": \"labels\",\n" +
                "    \"stylers\": [\n" +
                "      { \"visibility\": \"off\" }\n" +
                "    ]\n" +
                "  }\n" +
                "]";
        try {
            boolean success = googleMap.setMapStyle(new MapStyleOptions(mapStyleJson));

            if (!success) {
                Log.e("MapStyle", "Style parsing failed.");
            }
        } catch (Exception e) {
            Log.e("MapStyle", "Can't set map style. Error: ", e);
        }
        // Add a hole.
        hole = Arrays.asList(
                // Generates initial area with points 0.001 lat. and long. away from the -
                // - starting location. In this case:
                // 25.026026, 121.538090
                new LatLng(25.027026, 121.537090),
                new LatLng(25.027026, 121.539090),
                new LatLng(25.025026, 121.539090),
                new LatLng(25.025026, 121.537090)
        );
        saveHoleToFile(hole);
        // Draw main mask polygon.
        main_mask = googleMap.addPolygon(new PolygonOptions()
                .add(
                        // Set to the boundaries of the island of Taiwan.
                        new LatLng(25.299655, 120.035032),
                        new LatLng(21.896799, 120.035032),
                        new LatLng(21.896799, 122.007174),
                        new LatLng(25.299655, 122.007174))
                // Set it as opaquely deep blue, same as the color of the ocean in dark mode.
                .fillColor(0xFF00102E)
                .strokeWidth(0)
                .addHole(hole)
        );

        // Set default zoom when MyLocation button is clicked.
        map.setOnMyLocationButtonClickListener(() -> {
            getDeviceLocation();
            return true; // Return true to consume the event.
        });

        if (locationPermissionGranted) {
            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(5000); // 5 seconds
            locationRequest.setFastestInterval(2000); // Optional: limit how fast updates can come
            try{
                fusedLocationProviderClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        null // Use main looper
                );
            } catch (SecurityException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void updatePolygons(){
        if (lastKnownLocation.getLongitude() > 121.539090) {
            Toast.makeText(MapsActivity.this,
                    "Longitude exceeds 121.539090!",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void saveHoleToFile(List<LatLng> hole) {
        StringBuilder data = new StringBuilder();
        for (LatLng point : hole) {
            data.append(point.latitude).append(",").append(point.longitude).append("\n");
        }
        try {
            // Use internal storage
            File file = new File(getFilesDir(), "hole_coordinates");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data.toString().getBytes());
            fos.close();
            Log.i(TAG, "Hole coordinates saved to " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error saving hole coordinates", e);
        }
    }

    private List<LatLng> readHoleFromFile() {
        List<LatLng> result = new ArrayList<>();
        try {
            File file = new File(getFilesDir(), "hole_coordinates");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    double lat = Double.parseDouble(parts[0]);
                    double lng = Double.parseDouble(parts[1]);
                    result.add(new LatLng(lat, lng));
                }
            }
            reader.close();
        } catch (IOException e) {
            Log.e(TAG, "Error reading hole coordinates", e);
        }
        return result;
    }


    private void editHole(){
        if (main_mask != null) {
            // Edit the hole
            List<LatLng> trimmedHole = new ArrayList<>(hole.subList(0, 3));
            hole = trimmedHole;

            // Remove old polygon
            main_mask.remove();

            // Re-add polygon with updated hole
            main_mask = map.addPolygon(new PolygonOptions()
                    .add(
                            new LatLng(25.299655, 120.035032),
                            new LatLng(21.896799, 120.035032),
                            new LatLng(21.896799, 122.007174),
                            new LatLng(25.299655, 122.007174)
                    )
                    .fillColor(0xFF00102E)
                    .strokeWidth(0)
                    .addHole(readHoleFromFile())
            );
        }
    }
}