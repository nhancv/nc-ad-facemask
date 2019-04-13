package com.nhancv.facemask.tracking;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.nhancv.facemask.FaceLandmarkListener;
import com.nhancv.facemask.fps.StableFps;
import com.nhancv.facemask.util.STUtils;

import java.util.List;

import zeusees.tracking.Face;
import zeusees.tracking.FaceTracking;

public class FaceTrackingListener implements OnImageAvailableListener {

    private static final String TAG = "FaceTrackingListener";

    private static final int BM_FACE_W = 300;
    private static int BM_FACE_H = BM_FACE_W;
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private byte[][] mYUVBytes;
    private int[] mRGBBytes = null;
    private Bitmap mRGBframeBitmap = null;
    private Bitmap mRGBframeBitmap2 = null;
    private Bitmap mCroppedBitmap = null;

    private Context context;
    private Handler trackingHandler;
    private Handler mFaceDetectionHandler;
    private Handler mUIHandler;
    private Handler mPostImageHandler;
    private ImageView ivOverlay;
    private TextView tvFps;
    private String cameraId;
    private FaceLandmarkListener faceLandmarkListener;
    private Paint greenPaint, redPaint, bluePaint;
    /**
     * 0 forback camera
     * 1 for front camera
     * Initlity default camera is front camera
     */
    private static final String CAMERA_FRONT = "1";
    private static final String CAMERA_BACK = "0";

    private StableFps renderFps;
    private StableFps detectFps;

    private long lastTime = 0;
    private final Object lockObj = new Object();

    public FaceTrackingListener() {
        renderFps = new StableFps(30);
        detectFps = new StableFps(10);
    }

    public void initialize(
            final View parentView,
            final Context context,
            final String cameraId,
            final ImageView ivOverlay,
            final TextView tvFps,
            final Handler trackingHandler,
            final Handler faceDetectionHandler,
            final Handler mUIHandler,
            final Handler mPostImageHandler,
            final FaceLandmarkListener faceLandmarkListener,
            final SurfaceView overlap,
            final Matrix transformMatrix) {
        this.context = context;
        this.cameraId = cameraId;
        this.trackingHandler = trackingHandler;
        this.mFaceDetectionHandler = faceDetectionHandler;
        this.mUIHandler = mUIHandler;
        this.mPostImageHandler = mPostImageHandler;

        this.faceLandmarkListener = faceLandmarkListener;
        this.ivOverlay = ivOverlay;
        this.tvFps = tvFps;

        greenPaint = new Paint();
        greenPaint.setColor(Color.GREEN);
        greenPaint.setStrokeWidth(2);
        greenPaint.setStyle(Paint.Style.STROKE);

        redPaint = new Paint();
        redPaint.setColor(Color.RED);
        redPaint.setStrokeWidth(2);
        redPaint.setStyle(Paint.Style.STROKE);

        bluePaint = new Paint();
        bluePaint.setColor(Color.BLUE);
        bluePaint.setStrokeWidth(2);
        bluePaint.setStyle(Paint.Style.STROKE);

        this.mOverlap = overlap;
        this.matrix = transformMatrix;
        mMultiTrack106 = new FaceTracking("/sdcard/ZeuseesFaceTracking/models");
        mPaint = new Paint();
        mPaint.setColor(Color.rgb(57, 138, 243));
        mPaint.setStrokeWidth(2);
        mPaint.setStyle(Paint.Style.FILL);

    }

    public void deInitialize() {
        synchronized (FaceTrackingListener.this) {
            renderFps.stop();
            detectFps.stop();
            mTrack106 = false;
        }
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {

        if (!step1PreImageProcess(reader)) return;

        // Fps for detect face
        if (!detectFps.isStarted()) {
            detectFps.start(fps -> {
                if (mFaceDetectionHandler != null && mCroppedBitmap != null) {
                    mFaceDetectionHandler.post(this::step2FaceDetProcess);
                }
            });
        }

//        trackingHandler.post(this::step3TrackingProcess);

        // Fps for render to ui thread
        if (!renderFps.isStarted()) {
            renderFps.start(fps -> {
                if (mPostImageHandler != null) {
                    mPostImageHandler.post(() -> step4RenderProcess(fps));
                }
            });
        }
    }


    private Paint mPaint;
    private byte[] mNv21Data;
    private FaceTracking mMultiTrack106 = null;
    private boolean mTrack106 = false;
    private SurfaceView mOverlap;
    private Matrix matrix = new Matrix();
    private Face face;


    private boolean step1PreImageProcess(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return true;
            }
            final Plane[] planes = image.getPlanes();

            // Initialize the storage bitmaps once when the resolution is known.
            if (mPreviewWidth != image.getWidth() || mPreviewHeight != image.getHeight()) {
                mPreviewWidth = image.getWidth();
                mPreviewHeight = image.getHeight();
                Log.d(TAG, String.format("Initializing at size %dx%d", mPreviewWidth, mPreviewHeight));

                mNv21Data = new byte[mPreviewWidth * mPreviewHeight * 2];

                mRGBBytes = new int[mPreviewWidth * mPreviewHeight];
                mRGBframeBitmap = Bitmap.createBitmap(mPreviewWidth, mPreviewHeight, Config.ARGB_8888);
                mRGBframeBitmap2 = Bitmap.createBitmap(mPreviewHeight, mPreviewWidth, Config.ARGB_8888);
                float scaleInputRate = Math.max(mPreviewWidth, mPreviewHeight) * 1f / Math.min(mPreviewWidth, mPreviewHeight);
                BM_FACE_H = (int) (BM_FACE_W * scaleInputRate);
                mCroppedBitmap = Bitmap.createBitmap(mRGBframeBitmap, 0, 0, mPreviewWidth, mPreviewHeight);
                mYUVBytes = new byte[planes.length][];
                for (int i = 0; i < planes.length; ++i) {
                    mYUVBytes[i] = new byte[planes[i].getBuffer().capacity()];
                }
            }

            synchronized (lockObj) {
                byte[] data = ImageUtil.convertYUV420ToNV21(image);
                System.arraycopy(data, 0, mNv21Data, 0, data.length);
                image.close();


                if (!mTrack106) {
                    mMultiTrack106.FaceTrackingInit(mNv21Data, mPreviewHeight, mPreviewWidth);
                    mTrack106 = !mTrack106;
                } else {
                    mMultiTrack106.Update(mNv21Data, mPreviewHeight, mPreviewWidth);
                }

                List<Face> faceActions = mMultiTrack106.getTrackingInfo();

                if (faceActions != null) {

                    if (!mOverlap.getHolder().getSurface().isValid()) {
                        return false;
                    }

                    Canvas canvas = mOverlap.getHolder().lockCanvas();
                    if (canvas == null)
                        return false;

                    canvas.drawColor(0, PorterDuff.Mode.CLEAR);
                    canvas.setMatrix(matrix);
                    boolean rotate270 = true;
                    for (Face r : faceActions) {

                        Rect rect = new Rect(mPreviewHeight - r.left, r.top, mPreviewHeight - r.right, r.bottom);

                        Log.d(TAG, "handleDrawPoints: " + rect);
                        PointF[] points = new PointF[106];
                        for (int i = 0; i < 106; i++) {
                            points[i] = new PointF(r.landmarks[i * 2], r.landmarks[i * 2 + 1]);
                        }

                        float[] visibles = new float[106];


                        for (int i = 0; i < points.length; i++) {
                            visibles[i] = 1.0f;
                            if (rotate270) {
                                points[i].x = mPreviewHeight - points[i].x;
                            }
                        }

                        STUtils.drawFaceRect(canvas, rect, mPreviewHeight,
                                mPreviewWidth, true);
                        STUtils.drawPoints(canvas, mPaint, points, visibles, mPreviewHeight,
                                mPreviewWidth, true);

                    }
                    mOverlap.getHolder().unlockCanvasAndPost(canvas);
                }


//            for (int i = 0; i < planes.length; ++i) {
//                planes[i].getBuffer().get(mYUVBytes[i]);
//            }
//
//            final int yRowStride = planes[0].getRowStride();
//            final int uvRowStride = planes[1].getRowStride();
//            final int uvPixelStride = planes[1].getPixelStride();
//            ImageUtils.convertYUV420ToARGB8888(
//                    mYUVBytes[0],
//                    mYUVBytes[1],
//                    mYUVBytes[2],
//                    mRGBBytes,
//                    mPreviewWidth,
//                    mPreviewHeight,
//                    yRowStride,
//                    uvRowStride,
//                    uvPixelStride,
//                    false);

            }
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Log.e(TAG, "Exception!", e);
            return false;
        }
//        mRGBframeBitmap.setPixels(mRGBBytes, 0, mPreviewWidth, 0, 0, mPreviewWidth, mPreviewHeight);
//
//        // Resized mRGBframeBitmap
//        final Matrix matrix = new Matrix();
//        matrix.postScale(BM_FACE_H * 1f / mPreviewWidth, BM_FACE_W * 1f / mPreviewHeight);
//        int mScreenRotation = 90;
//        if (cameraId.equals(CAMERA_FRONT)) {
//            mScreenRotation = -90;
//        }
//        matrix.postRotate(mScreenRotation);
//        if (cameraId.equals(CAMERA_FRONT)) {
//            matrix.postScale(-1, 1);
//            matrix.postTranslate(BM_FACE_H, 0);//scale image back
//        }
//
//        mCroppedBitmap = Bitmap.createBitmap(mRGBframeBitmap, 0, 0, mPreviewWidth, mPreviewHeight, matrix, false);
        return true;
    }

    /**
     * Process and output: visionDetRets, oldBoundingBox
     * <p>
     * Write: results, visionDetRets, oldBoundingBox
     * Read: mCroppedBitmap (clone)
     */
    /***
     * Process and output: preImg, prevPts
     * Write: prImg, prevPts(clone), prePtsList
     *
     * */
    private void step2FaceDetProcess() {
        long startTime = System.currentTimeMillis();

        Bitmap bmp32 = mCroppedBitmap.copy(mCroppedBitmap.getConfig(), true);
//        results = mFaceDet.detect(bmp32);

        long endTime = System.currentTimeMillis();
        Log.d(TAG, "Detect face time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");

    }

    /**
     * Tracking and update: visionDetRets depend on boundingBox, oldBoundingBox
     * <p>
     * Write: boundingBox, oldBoundingBox, visionDetRets
     * Read: mCroppedBitmap (clone), visionDetRets
     */
    /**
     * Tracking and update: visionDetRets depend on preImg, nextImg, prevPts, prevPtsList
     * <p>
     * Write: prevImg,prePts, PrevPtsList visionDetRets =>NextPts
     * Read: mCroppedBitmap (clone), visionDetRets, PrevPtList
     */
    private void step3TrackingProcess() {

    }

    /**
     * Render object by: visionDetRets, mCroppedBitmap
     * Read only
     */
    private void step4RenderProcess(int fps) {
        final String log;
        long endTime = System.currentTimeMillis();
        if (lastTime == 0 || endTime == lastTime) {
            lastTime = System.currentTimeMillis();
            log = "Fps: " + fps;
        } else {
            log = "Fps: " + 1000 / (endTime - lastTime);
            lastTime = endTime;
        }

        if (faceLandmarkListener != null && tvFps != null) {
//            Bitmap bm32 = drawOnResultBoundingBox();
            if (mUIHandler != null) {
                mUIHandler.post(() -> {
                    tvFps.setText(log);
//                    ivOverlay.setImageBitmap(bm32);
                });
            }
//            faceLandmarkListener.landmarkUpdate(visionDetRets, mCroppedBitmap.getWidth(), mCroppedBitmap.getHeight());

        }
    }

    private Bitmap drawOnResultBoundingBox() {
        Bitmap bm32 = mRGBframeBitmap2.copy(mRGBframeBitmap2.getConfig(), true);

        if (face != null) {
            Canvas canvas = new Canvas(bm32);

            for (int i = 0; i < face.landmarks.length; i += 2) {
                canvas.drawCircle(face.landmarks[i], face.landmarks[i + 1], 5, redPaint);
            }


            Rect rect = new Rect(mPreviewHeight - face.left, face.top, mPreviewHeight - face.right, face.bottom);

            int left = rect.left;
            rect.left = mPreviewHeight - rect.right;
            rect.right = mPreviewHeight - left;
            canvas.drawRect(rect, greenPaint);

        }

        return bm32;
    }

}
