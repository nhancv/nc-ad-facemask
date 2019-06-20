package com.nhancv.facemask;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import zeusees.tracking.STUtils;

import static zeusees.tracking.STUtils.isExternalStorageReadable;
import static zeusees.tracking.STUtils.isExternalStorageWritable;


public class CameraActivity extends AppCompatActivity {

    static {
        System.loadLibrary("opencv_java4");
    }

    private static final String TAG = CameraActivity.class.getSimpleName();

    private static final int REQUEST_CODE_PERMISSION = 1;
    // Storage Permissions
    private static String[] PERMISSIONS_REQ = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };
    private CameraFragment cameraFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        if (null == savedInstanceState) {
            cameraFragment = CameraFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, cameraFragment)
                    .commit();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        // For API 23+ you need to request the read/write permissions even if they are already in your manifest.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (STUtils.verifyPermissions(this, PERMISSIONS_REQ, REQUEST_CODE_PERMISSION)) {
                init();
            }
        } else if (isExternalStorageWritable() && isExternalStorageReadable()) {
            init();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION) {
            boolean isDenied = false;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    isDenied = true;
                    break;
                }
            }
            if (isDenied) {
                Toast.makeText(this, getString(R.string.request_permission), Toast.LENGTH_SHORT).show();
            } else {
                init();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void init() {
        STUtils.initModelFiles(this);
        if (cameraFragment != null) {
            cameraFragment.init();
        }
    }

}
