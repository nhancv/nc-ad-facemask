package com.nhancv.facemask;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RectF;
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
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.nhancv.facemask.m2d.M2DLandmarkView;
import com.nhancv.facemask.m2d.M2DPosController;
import com.nhancv.facemask.tracking.FaceTrackingListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import zeusees.tracking.Face;


public class CameraFragment extends Fragment
        implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback, FaceLandmarkListener {

    private static final String TAG = CameraFragment.class.getSimpleName();

    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    private final SparseIntArray ORIENTATIONS = new SparseIntArray();

    {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final int MAX_PREVIEW_WIDTH = 640;//1920
    private static final int MAX_PREVIEW_HEIGHT = 480;//1080


    String[] maskFilters = new String[]{"cat"};
    // Hash map that includes id and bitmaps
    HashMap<String, HashMap<String, Bitmap>> maskFilterElements = new HashMap<>();
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }


        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
            transformMatrix.setScale(width / (float) mPreviewSize.getHeight(), height / (float) mPreviewSize.getWidth());
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    private String mCameraId = "1";
    private AutoFitTextureView mTextureView;
    private SurfaceView mOverlap;
    private Matrix transformMatrix = new Matrix();

    private M2DPosController m2DPosController;
    private M2DLandmarkView landmarkView;
    // For camera preview.
    private CameraCaptureSession mCaptureSession;
    // A reference to the opened {@link CameraDevice}.
    private CameraDevice mCameraDevice;
    // Size of camera preview
    private Size mPreviewSize;
    // CameraDevice.StateCallback is called when CameraDevice changes its state.
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;

            if (mOnGetPreviewListener != null) {
                mOnGetPreviewListener.deInitialize();
            }
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }

            if (mOnGetPreviewListener != null) {
                mOnGetPreviewListener.deInitialize();
            }
        }

    };

    private HandlerThread preImageProcessThread;
    private Handler mPreImageProcess;

    private HandlerThread trackingThread;
    private Handler trackingHandler;

    private HandlerThread faceDetectionThread;
    private Handler mFaceDetectionHandler;

    private HandlerThread postImageProcessThread;
    private Handler mPostImageHandler;

    private Handler uiHandler;

    private ImageReader previewReader;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;

    // A {@link Semaphore} to prevent the app from exiting before closing the camera.
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    // Whether the current camera device supports Flash or not.
    private boolean mFlashSupported;

    public static CameraFragment newInstance() {
        return new CameraFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = view.findViewById(R.id.fragment_camera_textureview);
        landmarkView = view.findViewById(R.id.fragment_camera_2dlandmarkview);
        mOverlap = view.findViewById(R.id.surfaceViewOverlap);
        mOverlap.setZOrderOnTop(true);
        mOverlap.getHolder().setFormat(PixelFormat.TRANSLUCENT);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        loadImageOverlay();
        m2DPosController = new M2DPosController(landmarkView);
        m2DPosController.update(this.maskFilterElements.get("cat"));
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onClick(final View view) {
        view.setEnabled(false);
        view.postDelayed(() -> view.setEnabled(true), 500);

    }

    @Override
    public void landmarkUpdate(List<Face> visionDetRetList, int bmW, int bmH) {
        uiHandler.post(() -> m2DPosController.landmarkUpdate(visionDetRetList, bmW, bmH));
    }

    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (int i = 0; i < manager.getCameraIdList().length; i++) {
                String cameraId = manager.getCameraIdList()[i];
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                if (cameraId.equals(mCameraId)) {
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
                    mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                            rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                            maxPreviewHeight, aspectRatio);

                    Log.e(TAG, "setUpCameraOutputs mPreviewSize: " + mPreviewSize);
                    // We fit the aspect ratio of TextureView to the size of preview we picked.
                    int orientation = getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        mTextureView.setAspectRatio(
                                mPreviewSize.getWidth(), mPreviewSize.getHeight());
                        landmarkView.setAspectRatio(
                                mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    } else {
                        mTextureView.setAspectRatio(
                                mPreviewSize.getHeight(), mPreviewSize.getWidth());
                        landmarkView.setAspectRatio(
                                mPreviewSize.getHeight(), mPreviewSize.getWidth());
                    }

                    // Check if the flash is supported.
                    Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    mFlashSupported = available == null ? false : available;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    // Opens the camera specified by mCameraId
    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mPreImageProcess);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    // Closes the current CameraDevice
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != previewReader) {
                previewReader.close();
                previewReader = null;
            }

            mOnGetPreviewListener.deInitialize();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    // Starts a background thread and its Handler
    private void startBackgroundThread() {
        preImageProcessThread = new HandlerThread("PreProcessingImageThread");
        preImageProcessThread.start();
        mPreImageProcess = new Handler(preImageProcessThread.getLooper());

        faceDetectionThread = new HandlerThread("FaceDetectionThread");
        faceDetectionThread.start();
        mFaceDetectionHandler = new Handler(faceDetectionThread.getLooper());

        trackingThread = new HandlerThread("TrackingThread");
        trackingThread.start();
        trackingHandler = new Handler(trackingThread.getLooper());

        postImageProcessThread = new HandlerThread("PostImageProcessingThread");
        postImageProcessThread.start();
        mPostImageHandler = new Handler(postImageProcessThread.getLooper());

        uiHandler = new Handler(Looper.getMainLooper());
    }

    // Stops a background thread and its Handler
    private void stopBackgroundThread() {
        try {
            if (preImageProcessThread != null) {
                preImageProcessThread.quitSafely();
                preImageProcessThread.join();
            }
            if (faceDetectionThread != null) {
                faceDetectionThread.quitSafely();
                faceDetectionThread.join();
            }
            if (trackingThread != null) {
                trackingThread.quitSafely();
                trackingThread.join();
            }
            if (postImageProcessThread != null) {
                postImageProcessThread.quitSafely();
                postImageProcessThread.join();
            }
            preImageProcessThread = null;
            mPreImageProcess = null;

            faceDetectionThread = null;
            mFaceDetectionHandler = null;

            trackingThread = null;
            trackingHandler = null;

            postImageProcessThread = null;
            mPostImageHandler = null;

            uiHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private final FaceTrackingListener mOnGetPreviewListener = new FaceTrackingListener();

    // Creates a new CameraCaptureSession for camera preview.
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Create the reader for the preview frames.
            previewReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            previewReader.setOnImageAvailableListener(mOnGetPreviewListener, mPreImageProcess);
            mPreviewRequestBuilder.addTarget(previewReader.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, previewReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Turn Off auto mode
                                if (mFlashSupported) {
                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                                }
                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest, null, mPreImageProcess);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mOnGetPreviewListener.initialize(getView(), Objects.requireNonNull(getActivity()).getApplicationContext(),
                mCameraId,
                getActivity().findViewById(R.id.fragment_camera_iv_overlay),
                getActivity().findViewById(R.id.fragment_camera_tv_fps),
                trackingHandler,
                mFaceDetectionHandler, uiHandler, mPostImageHandler, this, mOverlap, transformMatrix);
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    public void loadImageOverlay() {
        for (final String name : this.maskFilters) {
            HashMap<String, Bitmap> elements = new HashMap<>();
            elements.put("head", BitmapFactory.decodeResource(this.getResources(), R.drawable.cat_00001));
            elements.put("nose", BitmapFactory.decodeResource(this.getResources(), R.drawable.cat_00001));
            maskFilterElements.put(name, elements);
        }
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

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> activity.finish())
                    .create();
        }

    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        assert parent != null;
                        parent.requestPermissions(new String[]{Manifest.permission.CAMERA},
                                REQUEST_CAMERA_PERMISSION);
                    })
                    .setNegativeButton(android.R.string.cancel,
                            (dialog, which) -> {
                                assert parent != null;
                                Activity activity = parent.getActivity();
                                if (activity != null) {
                                    activity.finish();
                                }
                            })
                    .create();
        }
    }

}
