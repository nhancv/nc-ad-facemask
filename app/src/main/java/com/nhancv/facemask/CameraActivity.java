package com.nhancv.facemask;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.SeekBar;

import com.nhancv.facemask.util.CameraOverlap;
import com.nhancv.facemask.util.EGLUtils;
import com.nhancv.facemask.util.GLBitmap;
import com.nhancv.facemask.util.GLFrame;
import com.nhancv.facemask.util.GLFramebuffer;
import com.nhancv.facemask.util.GLPoints;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import hugo.weaving.DebugLog;
import zeusees.tracking.Face;
import zeusees.tracking.FaceTracking;

public class CameraActivity extends AppCompatActivity {

    static {
        OpenCVLoader.initDebug();
    }

    private static final String TAG = CameraActivity.class.getSimpleName();

    private static final int REQUEST_CODE_PERMISSION = 2;
    // Storage Permissions
    private static String[] PERMISSIONS_REQ = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_camera);
        // Just use hugo to print log
        isExternalStorageWritable();
        isExternalStorageReadable();

        // For API 23+ you need to request the read/write permissions even if they are already in your manifest.

        int currentapiVersion = android.os.Build.VERSION.SDK_INT;

        if (currentapiVersion >= Build.VERSION_CODES.M && verifyPermissions(this)) {
            if (null == savedInstanceState) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.container, CameraFragment.newInstance())
                        .commit();
            }


            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int width = size.x;
            int height = size.y;

            //onCreate: Width 1080 - Height 1776 (Sony E5655)
            Log.e(TAG, "onCreate: Width " + width + " - Height " + height);

//            init();
        }
    }

    /**
     * Checks if the app has permission to write to device storage or open camera
     * If the app does not has permission then the user will be prompted to grant permissions
     */
    @DebugLog
    private static boolean verifyPermissions(Activity activity) {
        // Check if we have write permission
        int writePermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int readPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int cameraPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);

        if (writePermission != PackageManager.PERMISSION_GRANTED ||
                readPermission != PackageManager.PERMISSION_GRANTED ||
                cameraPermission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_REQ,
                    REQUEST_CODE_PERMISSION
            );
            return false;
        } else {
            return true;
        }
    }

    /* Checks if external storage is available for read and write */
    @DebugLog
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /* Checks if external storage is available to at least read */
    @DebugLog
    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    public void copyFilesFromAssets(Context context, String oldPath, String newPath) {
        try {
            String[] fileNames = context.getAssets().list(oldPath);
            if ((fileNames != null ? fileNames.length : 0) > 0) {
                // directory
                File file = new File(newPath);
                if (!file.mkdir()) {
                    Log.d("mkdir", "can't make folder");

                }

                for (String fileName : fileNames) {
                    copyFilesFromAssets(context, oldPath + "/" + fileName,
                            newPath + "/" + fileName);
                }
            } else {
                // file
                InputStream is = context.getAssets().open(oldPath);
                FileOutputStream fos = new FileOutputStream(new File(newPath));
                byte[] buffer = new byte[1024];
                int byteCount;
                while ((byteCount = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, byteCount);
                }
                fos.flush();
                is.close();
                fos.close();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    void InitModelFiles() {
        String assetPath = "ZeuseesFaceTracking";
        String sdcardPath = Environment.getExternalStorageDirectory()
                + File.separator + assetPath;
        copyFilesFromAssets(this, assetPath, sdcardPath);

    }


    private FaceTracking mMultiTrack106 = null;
    private boolean mTrack106 = false;


    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private byte[] mNv21Data;
    private CameraOverlap cameraOverlap;
    private final Object lockObj = new Object();

    private SurfaceView mSurfaceView;

    private EGLUtils mEglUtils;
    private GLFramebuffer mFramebuffer;
    private GLFrame mFrame;
    private GLPoints mPoints;
    private GLBitmap mBitmap;

    private SeekBar seekBarA;
    private SeekBar seekBarB;
    private SeekBar seekBarC;

    @SuppressLint("SdCardPath")
    private void init() {
        InitModelFiles();

        mMultiTrack106 = new FaceTracking("/sdcard/ZeuseesFaceTracking/models");

        cameraOverlap = new CameraOverlap(this);
        mNv21Data = new byte[CameraOverlap.PREVIEW_WIDTH * CameraOverlap.PREVIEW_HEIGHT * 2];
        mFramebuffer = new GLFramebuffer();
        mFrame = new GLFrame();
        mPoints = new GLPoints();
        mBitmap = new GLBitmap(this, R.drawable.ic_action_info);
        mHandlerThread = new HandlerThread("DrawFacePointsThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        cameraOverlap.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                synchronized (lockObj) {
                    System.arraycopy(data, 0, mNv21Data, 0, data.length);
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mEglUtils == null) {
                            return;
                        }
                        mFrame.setS(seekBarA.getProgress() / 100.0f);
                        mFrame.setH(seekBarB.getProgress() / 360.0f);
                        mFrame.setL(seekBarC.getProgress() / 100.0f - 1);

                        if (mTrack106) {
                            mMultiTrack106.FaceTrackingInit(mNv21Data, CameraOverlap.PREVIEW_HEIGHT, CameraOverlap.PREVIEW_WIDTH);
                            mTrack106 = !mTrack106;
                        } else {
                            mMultiTrack106.Update(mNv21Data, CameraOverlap.PREVIEW_HEIGHT, CameraOverlap.PREVIEW_WIDTH);
                        }
                        boolean rotate270 = cameraOverlap.getOrientation() == 270;

                        List<Face> faceActions = mMultiTrack106.getTrackingInfo();
                        float[] p = null;
                        float[] points = null;
                        for (Face r : faceActions) {
                            points = new float[106 * 2];
                            Rect rect = new Rect(CameraOverlap.PREVIEW_HEIGHT - r.left, r.top, CameraOverlap.PREVIEW_HEIGHT - r.right, r.bottom);
                            for (int i = 0; i < 106; i++) {
                                int x;
                                if (rotate270) {
                                    x = r.landmarks[i * 2];
                                } else {
                                    x = CameraOverlap.PREVIEW_HEIGHT - r.landmarks[i * 2];
                                }
                                int y = r.landmarks[i * 2 + 1];
                                points[i * 2] = view2openglX(x, CameraOverlap.PREVIEW_HEIGHT);
                                points[i * 2 + 1] = view2openglY(y, CameraOverlap.PREVIEW_WIDTH);
                                if (i == 70) {
                                    p = new float[8];
                                    p[0] = view2openglX(x + 20, CameraOverlap.PREVIEW_HEIGHT);
                                    p[1] = view2openglY(y - 20, CameraOverlap.PREVIEW_WIDTH);
                                    p[2] = view2openglX(x - 20, CameraOverlap.PREVIEW_HEIGHT);
                                    p[3] = view2openglY(y - 20, CameraOverlap.PREVIEW_WIDTH);
                                    p[4] = view2openglX(x + 20, CameraOverlap.PREVIEW_HEIGHT);
                                    p[5] = view2openglY(y + 20, CameraOverlap.PREVIEW_WIDTH);
                                    p[6] = view2openglX(x - 20, CameraOverlap.PREVIEW_HEIGHT);
                                    p[7] = view2openglY(y + 20, CameraOverlap.PREVIEW_WIDTH);
                                }
                            }
                            if (p != null) {
                                break;
                            }
                        }
                        int tid = 0;
                        if (p != null) {
//                            mBitmap.setPoints(p);
//                            tid = mBitmap.drawFrame();
                        }
                        mFrame.drawFrame(tid, mFramebuffer.drawFrameBuffer(), mFramebuffer.getMatrix());
                        if (points != null) {
                            mPoints.setPoints(points);
                            mPoints.drawPoints();
                        }
                        mEglUtils.swap();

                    }
                });
            }
        });
//        mSurfaceView = findViewById(R.id.surface_view);
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(final SurfaceHolder holder, int format, final int width, final int height) {
                Log.d("=============", "surfaceChanged");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mEglUtils != null) {
                            mEglUtils.release();
                        }
                        mEglUtils = new EGLUtils();
                        mEglUtils.initEGL(holder.getSurface());
                        mFramebuffer.initFramebuffer();
                        mFrame.initFrame();
                        mFrame.setSize(width, height, CameraOverlap.PREVIEW_HEIGHT, CameraOverlap.PREVIEW_WIDTH);
                        mPoints.initPoints();
                        mBitmap.initFrame(CameraOverlap.PREVIEW_HEIGHT, CameraOverlap.PREVIEW_WIDTH);
                        cameraOverlap.openCamera(mFramebuffer.getSurfaceTexture());
                    }
                });

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        cameraOverlap.release();
                        mFramebuffer.release();
                        mFrame.release();
                        mPoints.release();
                        mBitmap.release();
                        if (mEglUtils != null) {
                            mEglUtils.release();
                            mEglUtils = null;
                        }
                    }
                });

            }
        });
        if (mSurfaceView.getHolder().getSurface() != null && mSurfaceView.getWidth() > 0) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mEglUtils != null) {
                        mEglUtils.release();
                    }
                    mEglUtils = new EGLUtils();
                    mEglUtils.initEGL(mSurfaceView.getHolder().getSurface());
                    mFramebuffer.initFramebuffer();
                    mFrame.initFrame();
                    mFrame.setSize(mSurfaceView.getWidth(), mSurfaceView.getHeight(), CameraOverlap.PREVIEW_HEIGHT, CameraOverlap.PREVIEW_WIDTH);
                    mPoints.initPoints();
                    mBitmap.initFrame(CameraOverlap.PREVIEW_HEIGHT, CameraOverlap.PREVIEW_WIDTH);
                    cameraOverlap.openCamera(mFramebuffer.getSurfaceTexture());
                }
            });
        }
//        seekBarA = findViewById(R.id.seek_bar_a);
//        seekBarB = findViewById(R.id.seek_bar_b);
//        seekBarC = findViewById(R.id.seek_bar_c);
    }

    private float view2openglX(int x, int width) {
        float centerX = width / 2.0f;
        float t = x - centerX;
        return t / centerX;
    }

    private float view2openglY(int y, int height) {
        float centerY = height / 2.0f;
        float s = centerY - y;
        return s / centerY;
    }

}
