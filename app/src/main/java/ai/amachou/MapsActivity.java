package ai.amachou;

import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * An activity that displays a map showing the place at the device's current location.
 */
public class MapsActivity extends AppCompatActivity
        implements OnMapReadyCallback {

    private static final String HOSPITALS_PATH = "hospitals.json";

    private JSONObject nearestHospital;

    private static final String TAG = MapsActivity.class.getSimpleName();
    private GoogleMap mMap;
    private CameraPosition mCameraPosition;

    // The entry points to the Places API.

    // The entry point to the Fused Location Provider.
    private FusedLocationProviderClient mFusedLocationProviderClient;

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private final LatLng mDefaultLocation = new LatLng(-33.8523341, 151.2106085);
    private static final int DEFAULT_ZOOM = 12;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean mLocationPermissionGranted;

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private Location userLocation;

    // Keys for storing activity state.
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            userLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_maps);

        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Build the map.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

    /**
     * Saves the state of the map when the activity is paused.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, userLocation);
            super.onSaveInstanceState(outState);
        }
    }

    /**
     * Manipulates the map when it's available.
     * This callback is triggered when the map is ready to be used.
     */
    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;

        // Prompt the user for permission.
        getLocationPermission();

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();
    }

    /**
     * Gets the current location of the device, and positions the map's camera.
     */
    private void getDeviceLocation() {
        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        userLocation = task.getResult();
                        Log.i("LOCATION: ",
                                userLocation.getLongitude() + " | " + userLocation.getLatitude()
                        );
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(
                                        userLocation.getLatitude(),
                                        userLocation.getLongitude()
                                ),
                                DEFAULT_ZOOM
                        ));

                        displayNearestHospital();

                        try {
                            LatLng hospital = new LatLng(
                                    nearestHospital.getDouble("X"),
                                    nearestHospital.getDouble("Y")
                            );
                            mMap.addMarker(new MarkerOptions().position(hospital)
                                    .title(nearestHospital.getString("name")));
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(hospital, DEFAULT_ZOOM));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }


                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.");
                        Log.e(TAG, "Exception: %s", task.getException());
                        mMap.moveCamera(CameraUpdateFactory
                                .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                        mMap.getUiSettings().setMyLocationButtonEnabled(false);
                    }
                });
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
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
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    /**
     * Prompts the user to select the current place from a list of likely places, and shows the
     * current place on the map - provided the user has granted location permission.
     */
    private void showCurrentPlace() {
        if (mMap == null) {
            return;
        }

        if (mLocationPermissionGranted) {
            displayNearestHospital();
        } else {
            // The user has not granted permission.
            Log.i(TAG, "The user did not grant location permission.");

            // Add a default marker, because the user hasn't selected a place.
            try {
                mMap.addMarker(new MarkerOptions()
                        .title(this.nearestHospital.getString("name"))
                        .position(new LatLng(this.nearestHospital.getDouble("Y"), this.nearestHospital.getDouble("X")))
                        .snippet("Hello"));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // Prompt the user for permission.
            getLocationPermission();
        }
    }

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                userLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    private double distance(LatLng a, LatLng b, char unit) {
        double theta = a.longitude - b.longitude;
        double dist = Math.sin(
                deg2rad(a.latitude)) * Math.sin(deg2rad(b.latitude)
        ) + Math.cos(deg2rad(a.latitude)) * Math.cos(deg2rad(b.latitude)) * Math.cos(deg2rad(theta));

        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        if (unit == 'K') {
            dist = dist * 1.609344;
        } else if (unit == 'N') {
            dist = dist * 0.8684;
        }
        return (dist);
    }

    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }

    public JSONObject getNearestHospital(Location userLocation, JSONArray hospitals) throws JSONException {
        double prevDist = Double.MAX_VALUE;
        JSONObject nearestHospital = hospitals.getJSONObject(0);
        for (int i = 0; i < hospitals.length(); i++) {
            JSONObject hospital = hospitals.getJSONObject(i);
            LatLng hospitalPosition = new LatLng(
                    hospital.getDouble("Y"),
                    hospital.getDouble("X")
            );
            LatLng userPosition = new LatLng(
                    userLocation.getLatitude(),
                    userLocation.getLongitude()
            );
            double currDist = distance(hospitalPosition, userPosition, 'K');
            if (currDist < prevDist) {
                prevDist = currDist;
                nearestHospital = hospital;
            }
        }
        return nearestHospital;
    }

    public void displayNearestHospital() {
        try {
            JSONArray hospitals = Helper.loadJSONFile(
                    getApplicationContext(),
                    HOSPITALS_PATH
            ).getJSONArray("data");

            this.nearestHospital = getNearestHospital(userLocation, hospitals);

            String dialogMsg = this.nearestHospital != null ?
                    getResources().getString(
                            R.string.the_nearest_hospital_is
                    ) + this.nearestHospital.getString("name") :
                    getResources().getString(R.string.unable_to_read_gps_informations);

            new AlertDialog
                    .Builder(this)
                    .setMessage(dialogMsg)
                    .setCancelable(true)
                    .show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}

//public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
//
//
//
//    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
//    private GoogleMap mMap;
//
//
//    private boolean mLocationPermissionGranted;
//
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_maps);
//        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
//        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
//                .findFragmentById(R.id.map);
//        assert mapFragment != null;
//        mapFragment.getMapAsync(this);
//    }
//

//
//
//
//
//    @Override
//    public void onMapReady(GoogleMap googleMap) {
//
//    }
//
//    @Override
//    public void onPointerCaptureChanged(boolean hasCapture) {
//
//    }
//}
