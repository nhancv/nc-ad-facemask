package com.nhancv.facemask;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.nhancv.facemask.m2d.SurfacePreview;
import com.nhancv.facemask.m2d.mask.MaskUpdater;
import com.nhancv.facemask.pose.SolvePNP;
import com.nhancv.facemask.tracking.FaceLandmarkTracking;
import com.nhancv.facemask.util.Constant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class CameraFragment extends Fragment
        implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

    /**
     * Static
     */
    private static final String TAG = CameraFragment.class.getSimpleName();

    public static final Point SCREEN_SIZE = new Point();
    public static final int MAX_PREVIEW_WIDTH = 640;//1920
    public static final int MAX_PREVIEW_HEIGHT = 480;//1080
    public static final int READER_WIDTH = 320;
    public static final int READER_HEIGHT = 240;
    public static int SURFACE_WIDTH; //1440
    public static int SURFACE_HEIGHT; //1080

    /**
     * Thread
     */
    private HandlerThread cameraSessionThread;
    private Handler cameraSessionHandler;

    private HandlerThread imagePreviewThread;
    private Handler imagePreviewHandler;

    /**
     * Global vars
     */
    private String cameraId = "1";
    private Matrix transformMatrix = new Matrix();

    /**
     * Camera component
     */
    // For camera preview.
    private ImageReader previewReader;
    private CameraDevice cameraDevice;
    private CaptureRequest previewRequest;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;

    // A Semaphore to prevent the app from exiting before closing the camera.
    private Semaphore cameraOpenCloseLock = new Semaphore(1);
    private final SparseIntArray ORIENTATIONS = new SparseIntArray();

    {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    // Whether the current camera device supports Flash or not.
    private boolean flashSupported;

    /**
     * UI component
     */
    private SurfacePreview surfacePreview;

    private boolean permissionReady;
    private int effectIndex;

    /**
     * Listener / Callback
     */
    private final FaceLandmarkTracking onGetPreviewListener = new FaceLandmarkTracking();

    // CameraDevice.StateCallback is called when CameraDevice changes its state.
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            cameraOpenCloseLock.release();
            CameraFragment.this.cameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            CameraFragment.this.cameraDevice = null;

            onGetPreviewListener.deInitialize();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            CameraFragment.this.cameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }

            onGetPreviewListener.deInitialize();
        }

    };

    public static CameraFragment newInstance() {
        return new CameraFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        /* Calculate VIEWPORT */
        // Get display size
        Display display = Objects.requireNonNull(getActivity()).getWindowManager().getDefaultDisplay();
        display.getSize(SCREEN_SIZE);
        int screenWidth = SCREEN_SIZE.x;
        int screenHeight = SCREEN_SIZE.y;
        //Sony: 1080x1776
        SURFACE_HEIGHT = screenWidth;
        SURFACE_WIDTH = SURFACE_HEIGHT * READER_WIDTH / READER_HEIGHT;

        float scaling = READER_HEIGHT / (float) READER_WIDTH;
        float viewRatio = screenWidth / (float) screenHeight;
        float s = scaling / viewRatio;
        int w, h;
        //AspectFill
        if (s > 1.0) {
            w = (int) (screenHeight * scaling);
            h = screenHeight;
        } else {
            w = screenWidth;
            h = (int) (screenWidth / scaling);
        }
        //transformMatrix.setScale(SURFACE_HEIGHT / (float) READER_HEIGHT, SURFACE_WIDTH / (float) READER_WIDTH);
        transformMatrix.setScale(w / (float) READER_HEIGHT, h / (float) READER_WIDTH);
        /* End Calculate VIEWPORT */

        SolvePNP.getInstance().initialize(new Point((int) (READER_WIDTH / 2f), (int) (READER_HEIGHT / 2f)));

        // Setup view
        View view = inflater.inflate(R.layout.fragment_camera, container, false);

        view.findViewById(R.id.bt_change_filter).setOnClickListener(v -> {
            effectIndex++;
            effectIndex = effectIndex % Constant.EFFECT_CONFIGS.length;
            Constant.EFFECT_ACTIVE = Constant.EFFECT_CONFIGS[effectIndex];

            MaskUpdater.MASK_ACTIVE = (MaskUpdater.MASK_ACTIVE + 1) % 2;

        });

        surfacePreview = view.findViewById(R.id.fragment_camera_surface_preview);
//        surfacePreview.setDisplayMode(ImageGLSurfaceView.DisplayMode.DISPLAY_ASPECT_FILL);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (permissionReady) {
            startBackgroundThread();
            openCamera(SURFACE_WIDTH, SURFACE_HEIGHT);
        }
    }

    /**
     * This function show toast notify require permission and finish activity
     */
    private void exitByPermissionNotReady() {
        showToast("Need permission");
        Objects.requireNonNull(getActivity()).finish();
    }

    /**
     * This function is called from Camera Activity after granted permission
     */
    public void updatePermissionReady() {
        permissionReady = true;
    }

    @Override
    public void onPause() {
        if (permissionReady) {
            release();
        }
        super.onPause();
    }

    private void release() {
        closeCamera();
        stopBackgroundThread();
    }

    @Override
    public void onClick(final View view) {
        view.setEnabled(false);
        view.postDelayed(() -> view.setEnabled(true), 500);
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        assert activity != null;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (int i = 0; i < manager.getCameraIdList().length; i++) {
                String cameraId = manager.getCameraIdList()[i];
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                if (cameraId.equals(this.cameraId)) {
                    StreamConfigurationMap map = characteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map == null) {
                        continue;
                    }

                    // Find out if we need to swap dimension to get the preview size relative to sensor
                    // coordinate.
                    int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                    //noinspection ConstantConditions
                    int mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    boolean swappedDimensions = false;
                    switch (displayRotation) {
                        case Surface.ROTATION_0:
                        case Surface.ROTATION_180:
                            if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                                swappedDimensions = true;
                            }
                            break;
                        case Surface.ROTATION_90:
                        case Surface.ROTATION_270:
                            if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                                swappedDimensions = true;
                            }
                            break;
                        default:
                            Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                    }

                    Point displaySize = new Point();
                    activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                    int rotatedPreviewWidth = width;
                    int rotatedPreviewHeight = height;
                    int maxPreviewWidth = displaySize.x;
                    int maxPreviewHeight = displaySize.y;

                    if (swappedDimensions) {
                        rotatedPreviewWidth = height;
                        rotatedPreviewHeight = width;
                        maxPreviewWidth = displaySize.y;
                        maxPreviewHeight = displaySize.x;
                    }

                    if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                        maxPreviewWidth = MAX_PREVIEW_WIDTH;
                    }

                    if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                        maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                    }

                    // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                    // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                    // garbage capture data.
                    Size aspectRatio = new Size(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT);
                    // Size of camera preview
                    Size previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                            rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                            maxPreviewHeight, aspectRatio);

                    Log.d(TAG, "setUpCameraOutputs previewSize: " + previewSize);
                    // Check if the flash is supported.
                    Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    flashSupported = available == null ? false : available;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    // Opens the camera specified by cameraId
    private void openCamera(int width, int height) {
        closeCamera();

        setUpCameraOutputs(width, height);
        CameraManager manager = (CameraManager) Objects.requireNonNull(getActivity()).getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (ActivityCompat.checkSelfPermission(Objects.requireNonNull(getActivity()), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                exitByPermissionNotReady();
                return;
            }
            manager.openCamera(cameraId, stateCallback, cameraSessionHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    // Closes the current CameraDevice
    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != previewReader) {
                previewReader.close();
                previewReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    // Starts a background thread and its Handler
    private void startBackgroundThread() {
        cameraSessionThread = new HandlerThread("CameraSessionThread");
        cameraSessionThread.start();
        cameraSessionHandler = new Handler(cameraSessionThread.getLooper());

        imagePreviewThread = new HandlerThread("PreProcessingImageThread");
        imagePreviewThread.start();
        imagePreviewHandler = new Handler(imagePreviewThread.getLooper());
    }

    // Stops a background thread and its Handler
    private void stopBackgroundThread() {
        try {
            onGetPreviewListener.deInitialize();

            if (cameraSessionThread != null) {
                cameraSessionThread.quitSafely();
                cameraSessionThread.join();
            }
            cameraSessionThread = null;
            cameraSessionHandler = null;

            if (imagePreviewThread != null) {
                imagePreviewThread.quitSafely();
                imagePreviewThread.join();
            }
            imagePreviewThread = null;
            imagePreviewHandler = null;

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Creates a new CameraCaptureSession for camera preview.
    private void createCameraPreviewSession() {
        try {
            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            // Create the reader for the preview frames.
            previewReader = ImageReader.newInstance(READER_WIDTH, READER_HEIGHT, ImageFormat.YUV_420_888, 2);
            previewReader.setOnImageAvailableListener(onGetPreviewListener, imagePreviewHandler);
            previewRequestBuilder.addTarget(previewReader.getSurface());
            cameraOpenCloseLock.acquire();
            // Here, we create a CameraCaptureSession for camera preview.
            if (previewReader != null) {
                cameraDevice.createCaptureSession(Collections.singletonList(previewReader.getSurface()),
                        new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                // The camera is already closed
                                if (null == cameraDevice) {
                                    return;
                                }

                                // When the session is ready, we start displaying the preview.
                                captureSession = cameraCaptureSession;
                                try {
                                    // Turn Off auto mode
                                    if (flashSupported) {
                                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                                    }
                                    // Finally, we start displaying the camera preview.
                                    previewRequest = previewRequestBuilder.build();
                                    captureSession.setRepeatingRequest(previewRequest, null, cameraSessionHandler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(
                                    @NonNull CameraCaptureSession cameraCaptureSession) {
                                showToast("Failed");
                                release();
                            }
                        }, null
                );
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            cameraOpenCloseLock.release();
        }
        // Initialize Preview listener
        onGetPreviewListener.initialize(getContext(), transformMatrix, surfacePreview);
    }

    // Shows a {@link Toast} on the UI thread.
    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices The list of sizes that the camera supports for the intended output
     * class
     * @param textureViewWidth The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth The maximum width that can be chosen
     * @param maxHeight The maximum height that can be chosen
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            Log.d(TAG, "chooseOptimalSize: " + option);
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    public static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

}
