package com.tracking;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import com.nhancv.facemask.AutoFitTextureView;
import com.nhancv.facemask.R;
import com.nhancv.facemask.tracking.FaceTrackingListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import hugo.weaving.DebugLog;


public class CameraOverlapFragment extends Fragment implements ImageReader.OnImageAvailableListener {
    private static final String TAG = CameraOverlapFragment.class.getSimpleName();
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    public final int MAX_PREVIEW_WIDTH = 640;
    public final int MAX_PREVIEW_HEIGHT = 480;

    private final int STATE_PREVIEW = 0;
    private String mCameraId = "1";
    private AutoFitTextureView mTextureView;
    // For camera preview.
    private CameraCaptureSession mCaptureSession;
    // A reference to the opened {@link CameraDevice}.
    private CameraDevice mCameraDevice;
    // Size of camera preview
    protected android.util.Size mPreviewSize;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private ImageReader previewReader;
    protected SurfaceView mOverlap;
    private Matrix transformMatrix = new Matrix();

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {
        @SuppressLint("LongLogTag")
        @DebugLog
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @SuppressLint("LongLogTag")
        @DebugLog
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

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
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
        }

    };

    private int mState = STATE_PREVIEW;
    // A {@link Semaphore} to prevent the app from exiting before closing the camera.
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    // Whether the current camera device supports Flash or not.
    private boolean mFlashSupported;
    // Orientation of the camera sensor
    private int mSensorOrientation;

    private HandlerThread preImageProcessThread;
    private Handler mPreImageProcess;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    @SuppressLint("LongLogTag")
    @DebugLog
    private static android.util.Size chooseOptimalSize(android.util.Size[] choices, int textureViewWidth,
                                                       int textureViewHeight, int maxWidth, int maxHeight, android.util.Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<android.util.Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<android.util.Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (android.util.Size option : choices) {
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

    @SuppressLint("LongLogTag")
    @DebugLog
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
            previewReader.setOnImageAvailableListener(this, mPreImageProcess);
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
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mPreImageProcess);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "onConfigureFailed: Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */

    @SuppressLint("LongLogTag")
    @DebugLog
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

    @Override
    public void onImageAvailable(ImageReader reader) {

    }


    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<android.util.Size> {

        @Override
        public int compare(android.util.Size lhs, android.util.Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera_overlap, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = view.findViewById(R.id.surfaceViewCamera);
        mOverlap = view.findViewById(R.id.surfaceViewOverlap);
        mOverlap.setZOrderOnTop(true);
        mOverlap.getHolder().setFormat(PixelFormat.TRANSLUCENT);
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */

    @SuppressLint("LongLogTag")
    @DebugLog
    private void startBackgroundThread() {
        preImageProcessThread = new HandlerThread("PreProcessingImageThread");
        preImageProcessThread.start();
        mPreImageProcess = new Handler(preImageProcessThread.getLooper());

    }

    /**
     * Stops the background thread and its {@link Handler}.
     */

    @SuppressLint("LongLogTag")
    @DebugLog
    private void stopBackgroundThread() {
        try {
            if (preImageProcessThread != null) {
                preImageProcessThread.quitSafely();
                preImageProcessThread.join();
            }
            preImageProcessThread = null;
            mPreImageProcess = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

//    private final FaceTrackingListener mOnGetPreviewListener = new FaceTrackingListener();

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
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


    public Matrix getMatrix() {
        return transformMatrix;
    }

    @SuppressLint("LongLogTag")
    @DebugLog
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
                    mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
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
                    android.util.Size aspectRatio = new Size(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT);
                    mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                            rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                            maxPreviewHeight, aspectRatio);

                    // We fit the aspect ratio of TextureView to the size of preview we picked.
                    int orientation = getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        mTextureView.setAspectRatio(
                                mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    } else {
                        mTextureView.setAspectRatio(
                                mPreviewSize.getHeight(), mPreviewSize.getWidth());
                    }

                    // Check if the flash is supported.
                    Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    mFlashSupported = available == null ? false : available;
                    //get previewWidth Height to set to CameraMatrix
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("LongLogTag")
    @DebugLog
    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
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

    /**
     * Closes the current {@link CameraDevice}.
     */

    @DebugLog
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

        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

}
