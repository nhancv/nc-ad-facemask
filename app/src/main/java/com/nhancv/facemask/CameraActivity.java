package com.nhancv.facemask;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import org.opencv.android.OpenCVLoader;

public class CameraActivity extends AppCompatActivity {

    static {
        OpenCVLoader.initDebug();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, CameraFragment.newInstance())
                    .commit();
        }
    }
}
