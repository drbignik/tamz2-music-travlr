package info.sigmaproject.musictravlr;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.sigmaproject.musictravlr.data.Track;

public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback, GoogleMap.OnMapLongClickListener, GoogleMap.OnMarkerClickListener, LocationListener {

    public static String SC_CLIENT_ID = "f0bcbacd3c99e2447ecfa68bad107651";

    private GoogleMap map;
    private Firebase rootRef;
    private LocationManager locationManager;
    private int SELECT_TRACK_REQUEST = 2;
    private int PERMISSION_REQUEST = 3;
    private ProgressDialog globalProgressDialog;

    private Map<LatLng, List<Track>> tracks = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        final MapsActivity self = this;

        if (!isNetworkAvailable()) {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setMessage("This app requires internet connection.")
                    .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            self.finish();
                        }
                    }).create();
            dialog.show();
            return;
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST);
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        globalProgressDialog = ProgressDialog.show(this, "Loading...",
                "Obtaining tracks. Please wait...", true, false);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        Firebase.setAndroidContext(this);
        rootRef = new Firebase("https://glowing-inferno-1872.firebaseio.com/android/music-travlr");

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 50000, 5, this);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 50000, 5, this);
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(0, 0), 5f));
        map.setOnMapLongClickListener(this);
        map.setOnMarkerClickListener(this);

        rootRef.child("markers").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // loop through all markers
                for (DataSnapshot markerSnapshot : dataSnapshot.getChildren()) {
                    LatLng position = firebaseRefToLatLng(markerSnapshot.getKey());
                    List<Track> tracks = MapsActivity.this.tracks.get(position);
                    if (tracks == null) {
                        tracks = new ArrayList<>();
                    }

                    for (DataSnapshot trackSnapshot : markerSnapshot.child("tracks").getChildren()) {
                        Track track = trackSnapshot.getValue(Track.class);
                        tracks.add(track);
                    }

                    MapsActivity.this.tracks.put(position, tracks);
                    map.addMarker(new MarkerOptions().position(position).title("Listen here!"));
                }
                globalProgressDialog.dismiss();
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == SELECT_TRACK_REQUEST) {
            Bundle extras = data.getExtras();
            if (extras.getBoolean(SelectTrackActivity.ACTION_CANCELED)) {
                return;
            }
            Track track = new Track();
            track.setId(extras.getInt(SelectTrackActivity.TRACK_ID));
            track.setTitle(extras.getString(SelectTrackActivity.TITLE));
            track.setStreamable(extras.getBoolean(SelectTrackActivity.TRACK_STREAMABLE));
            track.setStreamUrl(extras.getString(SelectTrackActivity.STREAM_URL));
            track.setUsername(extras.getString(SelectTrackActivity.USERNAME));

            double lat = extras.getDouble("lat");
            double lng = extras.getDouble("lng");
            LatLng position = new LatLng(lat, lng);

            List<Track> tracks = this.tracks.get(position);
            if (tracks == null) {
                tracks = new ArrayList<>();
            }
            tracks.add(track);
            this.tracks.put(position, tracks);

            map.addMarker(new MarkerOptions().position(position).title("Listen here!"));
            rootRef.child("markers").child(latLngToFirebaseKey(position)).child("tracks").child(track.getId().toString()).setValue(track);
            showTracksDialogAtPosition(position);
        }
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        Intent selectTrackIntent = new Intent(this, SelectTrackActivity.class);
        selectTrackIntent.putExtra("lat", latLng.latitude);
        selectTrackIntent.putExtra("lng", latLng.longitude);
        startActivityForResult(selectTrackIntent, SELECT_TRACK_REQUEST);
    }

    public void showTracksDialogAtPosition(final LatLng position) {
        final List<Track> tracks = this.tracks.get(position);
        final String[] trackNames = new String[tracks.size() + 1];
        trackNames[0] = "Add new track here...";
        for (int i = 0; i < tracks.size(); i++) {
            // Add track names after
            trackNames[i + 1] = tracks.get(i).getUsername() + " - " + tracks.get(i).getTitle();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose song")
                .setItems(trackNames, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            // Add new track
                            onMapLongClick(position);
                            return;
                        }
                        // (which - 1) - because the first item is 'Add new track'
                        Track track = tracks.get(which - 1);
                        Uri song = Uri.parse(track.getStreamUrl() + "?client_id=" + SC_CLIENT_ID);
                        Intent musicIntent = new Intent(Intent.ACTION_VIEW);
                        musicIntent.setDataAndType(song, "audio/*");
                        musicIntent.putExtra("name", track.getTitle());
                        musicIntent.putExtra("title", track.getTitle());
                        startActivity(musicIntent);
                    }
                });
        builder.create().show();
    }

    @Override
    public boolean onMarkerClick(final Marker marker) {
        showTracksDialogAtPosition(marker.getPosition());
        return true;
    }

    @Override
    public void onLocationChanged(Location location) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 15f));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // LEAVE EMPTY
    }

    @Override
    public void onProviderEnabled(String provider) {
        // LEAVE EMPTY
    }

    @Override
    public void onProviderDisabled(String provider) {
        // LEAVE EMPTY
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null;
    }

    private LatLng firebaseRefToLatLng(String firebaseKey) {
        Log.d("FIREBASE", firebaseKey);
        if (!firebaseKey.contains("lat") || !firebaseKey.contains("lng")) {
            return null;
        }
        String sLat = firebaseKey.substring(4, firebaseKey.indexOf('|')).replace(',', '.');
        String sLng = firebaseKey.substring(firebaseKey.indexOf('|') + 5, firebaseKey.length()).replace(',', '.');
        return new LatLng(Double.parseDouble(sLat), Double.parseDouble(sLng));
    }

    private String latLngToFirebaseKey(LatLng latLng) {
        String result = ("lat:" + latLng.latitude + "|lng:" + latLng.longitude).replaceAll("\\.", ",");
        return result;
//        return latLng.toString().replaceAll("([\\.#\\$\\[\\]])", "_");
    }
}
