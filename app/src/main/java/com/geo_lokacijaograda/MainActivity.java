package com.geo_lokacijaograda;

import android.Manifest;

import android.support.v7.app.ActionBar;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        OnMapReadyCallback,
        GoogleMap.OnMarkerClickListener,
        GoogleMap.OnMapLongClickListener,
        ResultCallback<Status> {

    private static final String TAG = MainActivity.class.getSimpleName();

    private GoogleMap map;
    private GoogleApiClient googleApiClient;
    private Location lastLocation;
    private TextView textLat, textLong;
    private MapFragment mapFragment;
    private static final String NOTIFICATION_MSG = "NOTIFICATION MSG";
    public float geofenceRadius = 0;


    // Stvaranje Intent-a, spaja komponente jedne aplikacije/aktivnosti sa drugima
    public static Intent makeNotificationIntent(Context context, String msg) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(NOTIFICATION_MSG, msg);
        return intent;
    }


    ActionBar actionBar;
    private Button mButton;
    private EditText mEdit;


    // metoda koja se poziva kada se aktivnost pokreće
    @Override
    protected void onCreate(Bundle savedInstanceState) {                                              //za slanje podataka između Aktivitija
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textLat = (TextView) findViewById(R.id.geo_širina);
        textLong = (TextView) findViewById(R.id.geo_dužina);

        initGMaps();
        createGoogleApi();

        actionBar = getSupportActionBar();
        actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#0176a0")));    //postavljanje boje pozadine

        Toast.makeText(getApplicationContext(),"Dugo držite da biste stvorili marker!",Toast.LENGTH_LONG).show();

        mButton = (Button) findViewById(R.id.btnOK);
        mEdit = (EditText) findViewById(R.id.etRadijus);
        final String value = mEdit.getText().toString();                                               //dobivamo vrijednost od onog što je korisnik upisao

        getRadius();
    }

    // upisivanje radijusa za geo-ogradu
    public void getRadius() {
        mButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        conditionsInput();
                    }
                }
        );
    }


    // uvjeti kod upisa u editText
    public void conditionsInput(){
        if (mEdit.getText().length() < 1) {
            Toast.makeText(getApplicationContext(), "Niste upisali radijus!", Toast.LENGTH_LONG).show();
            return;
        }

        if (Float.parseFloat(mEdit.getText().toString()) > 3000 || Float.parseFloat(mEdit.getText().toString()) < 30) {                                                                                                                                               //ograničenja unosa radijusa
            Toast.makeText(getApplicationContext(), "Upišite broj između 30 i 3000 metara", Toast.LENGTH_LONG).show();
        }else {
            geofenceRadius = Float.valueOf(String.valueOf(mEdit.getText()));
            Toast.makeText(getApplicationContext(), "Upisali ste radijus od " + mEdit.getText() + "m", Toast.LENGTH_LONG).show();
        }
    }



    // započini sa procesom kreiranja geo-ograde
    private void startGeofence() {
        Log.i(TAG, "startGeofence()");
        if (geoFenceMarker != null) {
            Geofence geofence = createGeofence(geoFenceMarker.getPosition(), geofenceRadius);                                                      //stvori geo-ogradu sa oznakom i radijusom
            GeofencingRequest geofenceRequest = createGeofenceRequest(geofence);                                                                    //pitaj za zahtjev
            addGeofence(geofenceRequest);
        }else {
            Log.e(TAG, "Geofence marker is null");
        }
    }

    // napravi geo-ogradu
    private Geofence createGeofence(LatLng latLng, float radius) {
        return new Geofence.Builder()
                .setRequestId(GEOFENCE_REQ_ID)
                .setCircularRegion(latLng.latitude, latLng.longitude, radius)
                .setExpirationDuration(GEO_DURATION)                                //geo-ograda će biti automatski uklonjena nakon ovog perioda
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER              //postavlja prijelaze (ulaz/izlaz)
                        | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
    }

    private Circle geoFenceLimits;

    // crtanje kruga za geo-ogradu na mapi
    private void drawGeofence() {
        if (geoFenceLimits != null)                                                                     //crtanje samo 1 kruga
            geoFenceLimits.remove();
        CircleOptions circleOptions = new CircleOptions()                                               //definiranje opcija za krug
                .center(geoFenceMarker.getPosition())
                .strokeColor(0xff888888)
                .strokeWidth(7)
                .fillColor(Color.argb(100, 190, 90, 100))
                .radius(geofenceRadius);
        geoFenceLimits = map.addCircle(circleOptions);
    }



    // započmi google mapu
    private void initGMaps() {
        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);            //stavljanje mape u app
        mapFragment.getMapAsync(this);
    }


    // stvaranje GoogleApiClient objekta
    private void createGoogleApi() {
        Log.d(TAG, "createGoogleApi()");
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }


    // kada se aktiviti pokreće, poziva se GoogleApiClient za konekciju
    @Override
    protected void onStart() {
        super.onStart();
        if(checkPermission() == false) {
            askPermission();
        }
        googleApiClient.connect();
    }


    // kada activity prestaje sa radom, poziva se GoogleApiClient za odspajanje
    @Override
    protected void onStop() {
        super.onStop();
        googleApiClient.disconnect();
    }


    // stvaranje izbornika
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();                                 //objekt koji kreira izbornik iz xml resursa
        inflater.inflate(R.menu.menu, menu);
        return true;
    }


    // stvaranje opcija u izborniku
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.stvori: {
                conditionsMenu();
                return true;
            }
            case R.id.obriši: {
                clearGeofence();
                return true;
            }
            case R.id.sakrij: {
                map.clear();
                Toast.makeText(getApplicationContext(), "Obrisali ste sve oznake", Toast.LENGTH_LONG).show();
                return true;
            }
            case R.id.mapaNormalna:
                map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                break;
            case R.id.mapaSatelit:
                map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                break;
            case R.id.mapaTeren:
                map.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                break;
            case R.id.mapaHibrid:
                map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                break;
            default:break;
        }
        return super.onOptionsItemSelected(item);
    }


    // uvjeti stvaranje ograde s obzirom na unos radijusa
    public void conditionsMenu(){
        if(geofenceRadius >= 30 && geofenceRadius <= 3000) {
            startGeofence();
            Toast.makeText(getApplicationContext(), "Stvorili ste geo-ogradu radijusa " + geofenceRadius + "m", Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(getApplicationContext(), "Upišite radijus. Nemoguće stvoriti             geo-ogradu", Toast.LENGTH_LONG).show();
        }
    }


    private static final long GEO_DURATION = 120 * 120 * 1000;
    private static final String GEOFENCE_REQ_ID = "ID1";                            //ID ograde




    // napravi zahtjev za geo-ogradu
    private GeofencingRequest createGeofenceRequest(Geofence geofence) {
        return new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();
    }



    // dodaj napravljeni zahtjev za geo-ogradu na uređajnu listu za praćenje
    private void addGeofence(GeofencingRequest request) {
        if (checkPermission())                                                              //ako je dopušten pristup lokaciji
            LocationServices.GeofencingApi
                    .addGeofences(googleApiClient, request, createGeofencePendingIntent())
                    .setResultCallback(this);
    }


    // brisanje geo-ograde
    private void clearGeofence() {
        LocationServices.GeofencingApi
                .removeGeofences(googleApiClient, createGeofencePendingIntent())
                .setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                if (status.isSuccess()) {
                    removeGeofenceDraw();                                                                                       //obriši geo-ogradu
                    Toast.makeText(getApplicationContext(), "Obrisali ste geo-ogradu", Toast.LENGTH_LONG).show();          //ispis poruke na ekranu
                }
            }
        });
    }


    //poziva se kada je rezultat spreman
    @Override
    public void onResult(@NonNull Status status) {
        Log.i(TAG, "onResult: " + status);
        if (status.isSuccess()) {
            saveGeofence();
            drawGeofence();
        }
    }

    private final String KEY_GEOFENCE_LAT = "geo-ograda širina";
    private final String KEY_GEOFENCE_LON = "geo-ograda dužina";



    // spremanje markera geo-ograde sa preferencama
    private void saveGeofence() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);                                        //za spremanje manjih ključnih vrijednosti/podataka
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putLong(KEY_GEOFENCE_LAT, Double.doubleToRawLongBits(geoFenceMarker.getPosition().latitude));
        editor.putLong(KEY_GEOFENCE_LON, Double.doubleToRawLongBits(geoFenceMarker.getPosition().longitude));
        editor.apply();
    }




    // brisanje kruga i markera geo-ograde
    private void removeGeofenceDraw() {
        Log.d(TAG, "removeGeofenceDraw()");
        if (geoFenceMarker != null)
            geoFenceMarker.remove();
            if (geoFenceLimits != null)
                geoFenceLimits.remove();
    }



    // provjeri dopuštenje za pristup lokaciji
    private boolean checkPermission() {
        Log.d(TAG, "checkPermission()");
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)        // provjeri dopuštenje pristupa lokaciji ako još nije odobreno
                == PackageManager.PERMISSION_GRANTED);
    }

    private final int REQ_PERMISSION = 999;



    // pitaj za dopuštenje za pristup lokaciji
    private void askPermission() {
        Log.d(TAG, "askPermission()");
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_PERMISSION);
    }



    // provjeri korisnikov odgovor na zahtjevanu dozvolu
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult()");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQ_PERMISSION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLastKnownLocation();                                             // dozvoljeno, uzima zadnju lokaciju
                } else {
                    permissionsDenied();                                                // odbijeno
                }
                break;
            }
        }
    }

    // aplikacija neće raditi bez dopuštenja (upozorenja)
    private void permissionsDenied() {
        Log.w(TAG, "permissionsDenied()");
    }


    // uzmi zadnju poznatu lokaciju
    private void getLastKnownLocation() {
        Log.d(TAG, "getLastKnownLocation()");
        if (checkPermission()) {                                                                 //ako je pristup lokaciji dozvoljen
            lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);   //uzmi korisnikovu lokaciju
            if (lastLocation != null) {
                writeLastLocation();
                startLocationUpdates();                                                          //započmi sa update-om lokacije
            } else {
                Log.w(TAG, "No location retrieved yet");
                startLocationUpdates();
            }
        } else
            askPermission();                                                                     //ako pristup nije odobren, pitaj za dopuštenje
    }

    //napiši geografsku širinu i dužinu
    private void writeLastLocation() {
        writeActualLocation(lastLocation);
    }


    // u aplikaciji ispisuje širinu i dužinu
    private void writeActualLocation(Location location) {
        textLat.setText("Širina : " + location.getLatitude());
        textLong.setText("Dužina: " + location.getLongitude());

        markerLocation(new LatLng(location.getLatitude(), location.getLongitude()));
    }

    private Marker locationMarker;


    // kreiranje ikone na mapi za prikaz trenutne lokacije
    private void markerLocation(LatLng latLng) {
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .title(getCompleteAddressString(latLng.latitude,latLng.longitude))              //pretvaranje koordinate u adresu gdje se trenutačno nalazimo
                .snippet("Ovdje se nalazim");

        if (map != null) {
            if (locationMarker != null)
                locationMarker.remove();
            locationMarker = map.addMarker(markerOptions);
        }
    }

    private LocationRequest locationRequest;
    private final int UPDATE_INTERVAL = 1000;                                                   //interval za ažuriranje definiran u milisekundama
    private final int FASTEST_INTERVAL = 900;                                                   //najveća brzina kojom aplikacija može ažurirati lokaciju



    // započmi ažuriranje lokacije
    private void startLocationUpdates() {
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL);

        if (checkPermission())
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);     //nakon odobrenja poziva ažuriranja za trenutnu lokaciju
    }


    // poziva se kada je klijent uključen/isključen sa usluge
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        getLastKnownLocation();
        recoverGeofenceMarker();
    }


    // obnavljanje zadnjeg geo-lokacijskog markera
    private void recoverGeofenceMarker() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);

        if (sharedPref.contains(KEY_GEOFENCE_LAT) && sharedPref.contains(KEY_GEOFENCE_LON)) {
            double lat = Double.longBitsToDouble(sharedPref.getLong(KEY_GEOFENCE_LAT, -1));
            double lon = Double.longBitsToDouble(sharedPref.getLong(KEY_GEOFENCE_LON, -1));
            LatLng latLng = new LatLng(lat, lon);
            markerForGeofence(latLng);
            drawGeofence();
        }
    }

    private Marker geoFenceMarker;                                                                   //marker za geo-ogradu


    // stvaranje markera za geo-ogradu
    private void markerForGeofence(LatLng latLng) {
        Log.i(TAG, "markerForGeofence(" + latLng + ")");
        String title = latLng.latitude + ", " + latLng.longitude;
        MarkerOptions markerOptions = new MarkerOptions()                                           //definiranje opcija za marker geo-ograde
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
                .title(title)
                .snippet((getCompleteAddressString(latLng.latitude,latLng.longitude)) );
        if (map != null) {
            if (geoFenceMarker != null)
                geoFenceMarker.remove();                                                            //ukloni zadnji marker geo-ograde
            geoFenceMarker = map.addMarker(markerOptions);                                          //dodaj novi marker geo-ograde

        }
    }

    static final LatLng FERIT = new LatLng(45.556814, 18.695803);



    // poziv za povrat se zove kada je mapa spremna
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d(TAG, "onMapReady()");
        map = googleMap;
        map.setOnMapLongClickListener(this);                                                    //poziva se kada je mapa dotaknuta
        map.setOnMarkerClickListener(this);                                                     //postavlja marker na određeno mjesto na mapi
        map.addMarker(new MarkerOptions()                                                       //definiranje opcija markera
                .position(FERIT)
                .title("FERIT")
                .snippet("Fakultet elektrotehnike, računarstva i inf. tehnologija"))
                .setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));
        map.setMyLocationEnabled(true);
    }


    // poziva se kada korisnik klikne na mapu
    @Override
    public void onMapLongClick(LatLng latLng) {
        Log.d(TAG, "onMapClick()");
        markerForGeofence(latLng);
    }

    // poziva se kada se lokacija promijeni
    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;
        writeActualLocation(location);
    }

    // poziva se kada je klijent trenutno isključen, svi zahtjevi su trenutno poništeni
    @Override
    public void onConnectionSuspended(int i) {
        Log.w(TAG, "onConnectionSuspended()");
    }

    // poziva se kada se dogodi greška prilikom spajanja klijenta na servis
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.w(TAG, "onConnectionFailed()");
    }

    // povrati podatke od markera
    @Override
    public boolean onMarkerClick(Marker marker) {
        Log.d(TAG, "onMarkerClickListener: " + marker.getPosition());
        return false;
    }


    private PendingIntent geoFencePendingIntent;

    private final int GEOFENCE_REQ_CODE = 0;                                                                 //za identificiranje od kojeg Intenta smo došli


    // stvaranje PeningIntenta za geo-ogradu
    private PendingIntent createGeofencePendingIntent() {
        if (geoFencePendingIntent != null)
            return geoFencePendingIntent;

        Intent intent = new Intent(this, GeofenceTransitionService.class);
        return PendingIntent.getService(this, GEOFENCE_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }



    // pretraživanje mjesta sa dodanim markerom i zumiranje trenutne pozicije
    public void geoLocate(View view) throws IOException {                                               //ako unos/ispis nije uspio bacit će IOException
        EditText et = (EditText) findViewById(R.id.upišiMjesto);                                        //registrira xml 'EditText' sa datotekom 'et'
        String location = et.getText().toString();                                                      //getText() uzima ono što smo upisali, pretvara u string
        List<Address> addressList = null;

        if (location != null && !location.equals("")) {
            Geocoder geocoder = new Geocoder(this);                                            //stvaranje objekta geocoder koji pretvara lokaciju u koordinate i obrnuto
            try {
                addressList = geocoder.getFromLocationName(location, 1);
            } catch (IOException e) {
                e.printStackTrace();                                                                   //služi za praćenje iznimke, ako se dogodi greška printstack će identificirati koja je metoda to izazvala
            }
            if (addressList != null && !addressList.isEmpty()) {
                Address address = addressList.get(0);
                LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());

                String title1 = "Širina:" + latLng.latitude + " Dužina: " + latLng.longitude;
                map.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title(title1)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                        .snippet(address.getAddressLine(0)));
                Toast.makeText(getApplicationContext(), "Mjesto koje ste tražili: "+location, Toast.LENGTH_LONG).show();

                markerForGeofence(latLng);                                                                  //stvaranje ograde na traženom mjestu
                map.animateCamera(CameraUpdateFactory.newLatLng(latLng));                                   //zumira trenutnu poziciju
                float zoom = 15f;                                                                           //zumiranje mape određenog levela
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, zoom);                //pomiče mapu kod traženog mjesta
                map.animateCamera(cameraUpdate);                                                            //mapa će se polako pomaknuti novim atributima

            } else {
                Toast.makeText(getApplicationContext(), "Upišite dostupnu lokaciju!", Toast.LENGTH_LONG).show();
            }
        }
        if(location.equals("")){
            Toast.makeText(getApplicationContext(), "Niste upisali lokaciju!", Toast.LENGTH_LONG).show();
        }
    }



    //pretvara koordinate u adresu gdje se trenutačno nalazimo
    private String getCompleteAddressString(double LATITUDE, double LONGITUDE) {
        String string = "";
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(LATITUDE, LONGITUDE, 1);
            if (addresses != null) {
                Address returnedAddress = addresses.get(0);
                StringBuilder strReturnedAddress = new StringBuilder("");

                for (int i = 0; i <= returnedAddress.getMaxAddressLineIndex(); i++) {
                    strReturnedAddress.append(returnedAddress.getAddressLine(i)).append("\n");
                }
                string = strReturnedAddress.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return string;
    }



    // upozorenje za izlaz iz aplikacije, mogućnost odabira
    @Override
    public void onBackPressed(){
        AlertDialog.Builder BackAlertDialog = new AlertDialog.Builder(MainActivity.this);
        BackAlertDialog.setTitle("Upozorenje");
        BackAlertDialog.setMessage("Jeste li sigurni da želite izaći iz aplikacije?");

        BackAlertDialog.setPositiveButton("NE", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        BackAlertDialog.setNegativeButton("DA", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        BackAlertDialog.show();
        return;
    }

}