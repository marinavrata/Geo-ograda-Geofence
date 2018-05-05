package com.geo_lokacijaograda;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;

public class SplashScreenActivity extends Activity {

    private static int SPLASH_SCREEN_DELAY = 2000;                                                          //trajanje splashscreena

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(SplashScreenActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        }, SPLASH_SCREEN_DELAY);
    }
}
