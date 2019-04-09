package com.nhancv.facemask;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.nhancv.facemask.fps.StableFps;
import com.nhancv.facemask.m2d.BitmapConversion;
import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;
import com.tzutalin.dlibtest.FileUtils;
import com.tzutalin.dlibtest.ImageUtils;

import org.opencv.core.Mat;
import org.opencv.core.Rect2d;
import org.opencv.tracking.Tracker;
import org.opencv.tracking.TrackerMOSSE;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import hugo.weaving.DebugLog;
//import org.opencv.core.Mat;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
public class OnGetImageListener implements OnImageAvailableListener {

    private static final String TAG = "OnGetImageListener";

    private static final int BM_FACE_W = 150;
    private static int BM_FACE_H = BM_FACE_W;
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private byte[][] mYUVBytes;
    private int[] mRGBBytes = null;
    private Bitmap mRGBframeBitmap = null;
    private Bitmap mCroppedBitmap = null;

    private Handler trackingHandler;
    private Handler mFaceDetectionHandler;
    private Handler mUIHandler;
    private Handler mPostImageHandler;
    private Context mContext;
    private FaceDet mFaceDet;
    private ImageView ivOverlay;
    private TextView tvFps;
    private Paint mFaceLandmarkPaint;
    private String cameraId;
    private FaceLandmarkListener faceLandmarkListener;
    private List<VisionDetRet> results;
    private BitmapConversion bitmapConversion = new BitmapConversion();
    private Mat croppedMat;

    private Tracker mosse = TrackerMOSSE.create();

    //private Bitmap overlayImage;
    /**
     * 0 forback camera
     * 1 for front camera
     * Initlity default camera is front camera
     */
    private static final String CAMERA_FRONT = "1";
    private static final String CAMERA_BACK = "0";

    private StableFps renderFps;
    private StableFps detectFps;

    public OnGetImageListener() {
        renderFps = new StableFps(30);
        detectFps = new StableFps(1);
    }

    @DebugLog
    public void initialize(
            final Context context,
            final String cameraId,
            final ImageView ivOverlay,
            final TextView tvFps,
            final Handler trackingHandler,
            final Handler faceDetectionHandler,
            final Handler mUIHandler,
            final Handler mPostImageHandler,
            final FaceLandmarkListener faceLandmarkListener) {
        this.mContext = context;
        this.cameraId = cameraId;
        this.trackingHandler = trackingHandler;
        this.mFaceDetectionHandler = faceDetectionHandler;
        this.mUIHandler = mUIHandler;
        this.mPostImageHandler = mPostImageHandler;

        this.faceLandmarkListener = faceLandmarkListener;
        this.mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        this.ivOverlay = ivOverlay;
        this.tvFps = tvFps;

        mFaceLandmarkPaint = new Paint();
        mFaceLandmarkPaint.setColor(Color.GREEN);
        mFaceLandmarkPaint.setStrokeWidth(2);
        mFaceLandmarkPaint.setStyle(Paint.Style.STROKE);
        if (!new File(Constants.getFaceShapeModelPath()).exists()) {
            //FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath());
            FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_5_face_landmarks, Constants.getFaceShapeModelPath());
        }
    }

    @DebugLog
    public void deInitialize() {
        synchronized (OnGetImageListener.this) {
            if (mFaceDet != null) {
                mFaceDet.release();
            }
            renderFps.stop();
            detectFps.stop();
        }
    }

    private long lastTime = 0;
    private Rect2d boundingBox;
    private Rect2d oldBoundingBox;
    private VisionDetRet ret;//our origin

    public VisionDetRet normResult(VisionDetRet ret, Rect2d boundingBox) {
        VisionDetRet result;
        float x = (float) boundingBox.x;
        float y = (float) boundingBox.y;
        float oldX = (float) oldBoundingBox.x;
        float oldY = (float) oldBoundingBox.y;
        String label = "p1";
        float right = (float) (boundingBox.x + boundingBox.width);
        float bottom = (float) (boundingBox.y + boundingBox.height);
        //new result of VisionDetRet
        result = new VisionDetRet(label, 100f, (int) x, (int) y, (int) right, (int) bottom);
        int offsetX = (int) (x - oldX);
        int offsetY = (int) (y - oldY);
        List<Point> oldLandmarks = ret.getFaceLandmarks();
        for (int i = 0; i < oldLandmarks.size(); i++) {
            result.addLandmark(oldLandmarks.get(i).x + offsetX, oldLandmarks.get(i).y + offsetY);
        }
        return result;
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        // Fps for detect face
        if (!detectFps.isStarted()) {
            detectFps.start(fps -> {

                if (mFaceDetectionHandler != null && mCroppedBitmap != null) {
                    mFaceDetectionHandler.post(
                            () -> {
                                long startTime = System.currentTimeMillis();

                                results = mFaceDet.detect(mCroppedBitmap.copy(mCroppedBitmap.getConfig(), true));

                                long endTime = System.currentTimeMillis();
                                Log.d(TAG, "Detect face time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");
                                // Draw on bitmap
                                //bug here results is = 0
                                if (results.size() > 0
                                        && croppedMat != null && !croppedMat.size().empty()) {
                                    ret = results.get(0);
                                    float x = ret.getLeft();
                                    float y = ret.getTop();
                                    float w = ret.getRight() - x;
                                    float h = ret.getBottom() - y;
                                    boundingBox = new Rect2d(x, y, w, h);

                                    trackingHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            mosse.init(croppedMat, boundingBox);
                                        }
                                    });
                                }

                            });
                }

            });
        }


        // Fps for render to ui thread
        if (!renderFps.isStarted()) {
            renderFps.start(fps -> {

                if (mPostImageHandler != null) {
                    mPostImageHandler.post(() -> {
                        final String log;
                        long endTime = System.currentTimeMillis();
                        if (lastTime == 0 || endTime == lastTime) {
                            lastTime = System.currentTimeMillis();
                            log = "Fps: " + fps;
                        } else {
                            log = "Fps: " + 1000 / (endTime - lastTime);
                            lastTime = endTime;
                        }

                        if (faceLandmarkListener != null && results != null && mCroppedBitmap != null && tvFps != null) {
                            if (boundingBox == null && !results.isEmpty()) {
                                ret = results.get(0);
                                float x = ret.getLeft();
                                float y = ret.getTop();
                                float w = ret.getRight() - x;
                                float h = ret.getBottom() - y;
                                boundingBox = new Rect2d(x, y, w, h);
                                oldBoundingBox = new Rect2d(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);
                            }
                            if (boundingBox != null && !results.isEmpty()) {
                                drawOnResultBoundingBox(boundingBox);
                                if (mUIHandler != null) {
                                    mUIHandler.post(() -> {
                                        tvFps.setText(log);
//                                        mWindow.setImageBitmap(mCroppedBitmap);
                                    });

                                }
                                ret = results.get(0); //get the old ret face
                                VisionDetRet newRet = normResult(ret, boundingBox);//using old ret to get landmarks and the new boundingbox
                                oldBoundingBox = new Rect2d(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);
                                results = new ArrayList<>();
                                results.add(0, newRet); //add ret value to results
                            }
                            faceLandmarkListener.landmarkUpdate(results, mCroppedBitmap.getWidth(), mCroppedBitmap.getHeight());

                        }
                    });
                }

            });
        }


        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            final Plane[] planes = image.getPlanes();

            // Initialize the storage bitmaps once when the resolution is known.
            if (mPreviewWidth != image.getWidth() || mPreviewHeight != image.getHeight()) {
                mPreviewWidth = image.getWidth();
                mPreviewHeight = image.getHeight();

                Log.d(TAG, String.format("Initializing at size %dx%d", mPreviewWidth, mPreviewHeight));
                mRGBBytes = new int[mPreviewWidth * mPreviewHeight];
                mRGBframeBitmap = Bitmap.createBitmap(mPreviewWidth, mPreviewHeight, Config.ARGB_8888);
                float scaleInputRate = Math.max(mPreviewWidth, mPreviewHeight) * 1f / Math.min(mPreviewWidth, mPreviewHeight);
                BM_FACE_H = (int) (BM_FACE_W * scaleInputRate);
                mCroppedBitmap = Bitmap.createBitmap(mRGBframeBitmap, 0, 0, mPreviewWidth, mPreviewHeight);
                mYUVBytes = new byte[planes.length][];
                for (int i = 0; i < planes.length; ++i) {
                    mYUVBytes[i] = new byte[planes[i].getBuffer().capacity()];
                }
            }

            for (int i = 0; i < planes.length; ++i) {
                planes[i].getBuffer().get(mYUVBytes[i]);
            }

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            ImageUtils.convertYUV420ToARGB8888(
                    mYUVBytes[0],
                    mYUVBytes[1],
                    mYUVBytes[2],
                    mRGBBytes,
                    mPreviewWidth,
                    mPreviewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    false);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Log.e(TAG, "Exception!", e);
            return;
        }
        mRGBframeBitmap.setPixels(mRGBBytes, 0, mPreviewWidth, 0, 0, mPreviewWidth, mPreviewHeight);

        // Resized mRGBframeBitmap
        final Matrix matrix = new Matrix();
        matrix.postScale(BM_FACE_H * 1f / mPreviewWidth, BM_FACE_W * 1f / mPreviewHeight);
        int mScreenRotation = 90;
        if (cameraId.equals(CAMERA_FRONT)) {
            mScreenRotation = -90;
        }
        matrix.postRotate(mScreenRotation);
        if (cameraId.equals(CAMERA_FRONT)) {
            matrix.postScale(-1, 1);
            matrix.postTranslate(BM_FACE_H, 0);//scale image back
        }

        mCroppedBitmap = Bitmap.createBitmap(mRGBframeBitmap, 0, 0, mPreviewWidth, mPreviewHeight, matrix, false);

        if (results != null && results.size() > 0) {
            croppedMat = bitmapConversion.convertBitmap2Mat(mCroppedBitmap);

            trackingHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (boundingBox != null && !boundingBox.size().empty()) {
                        long startTime = System.currentTimeMillis();
                        mosse.update(croppedMat, boundingBox);
                        long endTime = System.currentTimeMillis();
                        Log.d(TAG, "Tracking time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");


                    }
                }
            });
        }

    }

    private void drawOnResultBoundingBox(Rect2d boundingBox) {
        Rect bounds = new Rect();
        bounds.left = (int) boundingBox.x;
        bounds.top = (int) boundingBox.y;
        bounds.bottom = (int) (boundingBox.y + boundingBox.height);
        bounds.right = (int) (boundingBox.x + boundingBox.width);
        Canvas canvas = new Canvas(mCroppedBitmap);
        canvas.drawRect(bounds, mFaceLandmarkPaint);
    }
}
