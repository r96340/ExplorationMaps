package com.example.maps;

import static java.lang.Math.abs;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
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
import com.example.maps.databinding.ActivityMapsBinding;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.net.PlacesClient;

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
    private boolean isMapReady = false;
    private boolean isLocationReady = false;
    private double originLatitude;
    private double originLongitude;

    //Customizable configurations
    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final int DEFAULT_ZOOM = 18;
    private final LatLng DEFAULT_LOCATION = new LatLng(25.0260079, 121.5381223);
    private final double SHOW_RADIUS = 0.0003;

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
                    if (location != null) {
                        lastKnownLocation = location;
                        double lat = location.getLatitude();
                        double lon = location.getLongitude();

                        if (checkOutside(lat, lon)) {
                            makeToast("Location callback adds a new hole.");
                            addNewHoles(lat, lon);
                        }
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

    private void makeToast(String text){
        Toast.makeText(MapsActivity.this,
                text,
                Toast.LENGTH_LONG).show();
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
                                Toast.makeText(MapsActivity.this,
                                        "Location service running",
                                        Toast.LENGTH_LONG).show();
                                if(checkOutside(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude())){
                                    addNewHoles(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                                    makeToast("getDeviceLocation adds a new hole.");
                                }
                                map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(lastKnownLocation.getLatitude(),
                                                lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                            }
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            map.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM));
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

        // Initialize map and add known holes
        File file = new File(getFilesDir(), "hole_coordinates");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        refreshHoles();
        // Set origin point
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();  // Read the first line only
            if (line != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    originLatitude = Double.parseDouble(parts[0].trim());
                    originLongitude = Double.parseDouble(parts[1].trim());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading first hole coordinate", e);
        }

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

    public void refreshHoles(){
        // Remove previous polygon
        if (main_mask != null) {
            main_mask.remove();
        }

        // Build the base polygon (Taiwan boundary)
        PolygonOptions polygonOptions = new PolygonOptions()
                .add(
                        new LatLng(25.299655, 120.035032),
                        new LatLng(21.896799, 120.035032),
                        new LatLng(21.896799, 122.007174),
                        new LatLng(25.299655, 122.007174))
                .fillColor(0xFF00102E)
                .strokeWidth(0);
        // Add all hole rings

        List<List<LatLng>> holes = readHolesFromFile();
        for (List<LatLng> hole : holes) {
            polygonOptions.addHole(hole);
        }

        // Create the polygon with holes
        main_mask = map.addPolygon(polygonOptions);
    }

    public void addNewHoles(double lat, double lon){
        // Make new hole.
        makeHoleFromCenter(lat, lon);
        // Save new hole.
        saveHoleToFile(lat, lon);

        // Remove previous polygon
        if (main_mask != null) {
            main_mask.remove();
        }

        // Build the base polygon (Taiwan boundary)
        PolygonOptions polygonOptions = new PolygonOptions()
                .add(
                        new LatLng(25.299655, 120.035032),
                        new LatLng(21.896799, 120.035032),
                        new LatLng(21.896799, 122.007174),
                        new LatLng(25.299655, 122.007174))
                .fillColor(0xFF00102E)
                .strokeWidth(0);
        // Add all hole rings
        List<List<LatLng>> holes = readHolesFromFile();
        for (List<LatLng> hole : holes) {
            polygonOptions.addHole(hole);
        }

        // Create the polygon with holes
        main_mask = map.addPolygon(polygonOptions);
    }

    public void makeHoleFromCenter(double lat, double lon){
        double deltaLat = abs(lat-originLatitude);
        double deltaLon = abs(lon-originLongitude);
        if(deltaLat > 0.0006){

        } else if(deltaLon > 0.0006){

        } else {
            hole = Arrays.asList(
                    new LatLng(lat + SHOW_RADIUS, lon - SHOW_RADIUS),
                    new LatLng(lat + SHOW_RADIUS, lon + SHOW_RADIUS),
                    new LatLng(lat - SHOW_RADIUS, lon + SHOW_RADIUS),
                    new LatLng(lat - SHOW_RADIUS, lon - SHOW_RADIUS)
            );
        }
    }

    public boolean checkOutside(double lat, double lon) {
        try {
            File file = new File(getFilesDir(), "hole_coordinates");
            boolean isOutside = true;
            if (!file.exists()) return true;

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    double holeLat = Double.parseDouble(parts[0]);
                    double holeLon = Double.parseDouble(parts[1]);

                    double latDiff = abs(lat - holeLat);
                    double lonDiff = abs(lon - holeLon);

                    if (latDiff <= SHOW_RADIUS && lonDiff <= SHOW_RADIUS) {
                        // Inside one of the holes
                        isOutside = false;
                        break;
                    }
                }
            }

            reader.close();
            return isOutside;

        } catch (IOException e) {
            Log.e(TAG, "Error checking location against holes", e);
            return false;
        }
    }

    // This saves the centers of already opened holes to a file.
    private void saveHoleToFile(double lat, double lon) {
        StringBuilder data = new StringBuilder();
        data.append(lat).append(",").append(lon).append("\n");
        try {
            // Use internal storage
            File file = new File(getFilesDir(), "hole_coordinates");
            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write(data.toString().getBytes());
            fos.close();
            Log.i(TAG, "Hole coordinates saved to " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error saving hole coordinates", e);
        }
    }

    private List<List<LatLng>> readHolesFromFile() {
        List<List<LatLng>> holeRings = new ArrayList<>();
        try {
            File file = new File(getFilesDir(), "hole_coordinates");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    double lat = Double.parseDouble(parts[0]);
                    double lon = Double.parseDouble(parts[1]);

                    // Create a square ring (hole) around the center point
                    List<LatLng> hole = Arrays.asList(
                            new LatLng(lat + SHOW_RADIUS, lon - SHOW_RADIUS),
                            new LatLng(lat + SHOW_RADIUS, lon + SHOW_RADIUS),
                            new LatLng(lat - SHOW_RADIUS, lon + SHOW_RADIUS),
                            new LatLng(lat - SHOW_RADIUS, lon - SHOW_RADIUS)
                    );

                    holeRings.add(hole);
                }
            }
            reader.close();
        } catch (IOException e) {
            Log.e(TAG, "Error reading hole coordinates", e);
        }
        return holeRings;
    }
}