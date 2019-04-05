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
import android.os.Trace;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import hugo.weaving.DebugLog;

import org.opencv.core.Mat;
import org.opencv.core.Rect2d;
import org.opencv.tracking.Tracker;
import org.opencv.tracking.TrackerMOSSE;
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

    private boolean mIsComputing = false;
    private Handler mInferenceHandler;
    private Handler mUIHandler;

    private Context mContext;
    private FaceDet mFaceDet;
    private ImageView mWindow;
    private TextView tvFps;
    private Paint mFaceLandmarkPaint;
    private String cameraId;
    private FaceLandmarkListener faceLandmarkListener;
    private List<VisionDetRet> results;
    private BitmapConversion bitmapConversion = new BitmapConversion();
    private Mat croppedMat;
    //private Mat curFace; //img1
    //private Mat overlayMat; //img2
   // private Mat curFaceWarped;
    //private Bitmap overlayImage;
    /**
     * 0 forback camera
     * 1 for front camera
     * Initlity default camera is front camera
     */
    Tracker mosse = TrackerMOSSE.create();
    public static final String CAMERA_FRONT = "1";
    public static final String CAMERA_BACK = "0";

    private StableFps stableFps;
    public OnGetImageListener() {
        stableFps = new StableFps(30);
    }

    @DebugLog
    public void initialize(
            final Context context,
            final String cameraId,
            final ImageView imageView,
            final TextView tvFps,
            final Handler handler,
            final Handler mUIHandler,
            final FaceLandmarkListener faceLandmarkListener) {
        this.mContext = context;
        this.cameraId = cameraId;
        this.mInferenceHandler = handler;
        this.mUIHandler = mUIHandler;
        this.faceLandmarkListener = faceLandmarkListener;
        mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        mWindow = imageView;
        this.tvFps = tvFps;

        mFaceLandmarkPaint = new Paint();
        mFaceLandmarkPaint.setColor(Color.GREEN);
        mFaceLandmarkPaint.setStrokeWidth(2);
        mFaceLandmarkPaint.setStyle(Paint.Style.STROKE);
    }

    @DebugLog
    public void deInitialize() {
        synchronized (OnGetImageListener.this) {
            if (mFaceDet != null) {
                mFaceDet.release();
            }
            stableFps.stop();
        }
    }

    private long lastTime = 0;
    Rect2d boundingBox;
    private boolean detectDlib;
    int countF = 6;
    boolean isValid = false;

    @Override
    public void onImageAvailable(final ImageReader reader) {
        if(!stableFps.isStarted()) {
            stableFps.start(fps -> {

                final String log;
                long endTime = System.currentTimeMillis();
                if(lastTime == 0 || endTime == lastTime) {
                    lastTime = System.currentTimeMillis();
                    log = "Fps: " + fps;
                } else {
                    log = "Fps: " + 1000 / (endTime - lastTime);
                    lastTime = endTime;
                }

                if (faceLandmarkListener != null && results != null && mCroppedBitmap != null && tvFps != null) {
//                    drawOnResults(results);
                    if(boundingBox == null && !results.isEmpty()) {
                        VisionDetRet ret = results.get(0);
                        float x = ret.getLeft();
                        float y = ret.getTop();
                        float w = ret.getRight() - x;
                        float h = ret.getBottom() - y;
                        boundingBox = new Rect2d(x,y,w,h);
                    }
                    if(boundingBox!= null)  {
                        drawOnResultsByBoundingBox(boundingBox);
                        if (mUIHandler != null) {
                            mUIHandler.post(() -> {
                                tvFps.setText(log);
                                mWindow.setImageBitmap(mCroppedBitmap);
                            });

                        }
                    }
//                    faceLandmarkListener.landmarkUpdate(results, mCroppedBitmap.getWidth(), mCroppedBitmap.getHeight());


                }

            });
        }
        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            // No mutex needed as this method is not reentrant.
            if (mIsComputing) {
                image.close();
                return;
            }
            mIsComputing = true;

            Trace.beginSection("imageAvailable");

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
            Trace.endSection();
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
        if (this.cameraId.equals(CAMERA_FRONT)) {
            matrix.postScale(-1, 1);
            matrix.postTranslate(BM_FACE_H, 0);//scale image back
        }

        if(mCroppedBitmap.isRecycled()) mCroppedBitmap.recycle();
        mCroppedBitmap = Bitmap.createBitmap(mRGBframeBitmap, 0, 0, mPreviewWidth, mPreviewHeight, matrix, false);

        if (mInferenceHandler != null)
            mInferenceHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (!new File(Constants.getFaceShapeModelPath()).exists()) {
                                //FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath());
                                FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_5_face_landmarks, Constants.getFaceShapeModelPath());
                            }

                            long startTime = System.currentTimeMillis();

                            synchronized (OnGetImageListener.this) {

                                if(results == null || results.size() == 0) {
                                    results = mFaceDet.detect(mCroppedBitmap);
                                } else {
                                    croppedMat = bitmapConversion.convertBitmap2Mat(mCroppedBitmap);
                                    if(boundingBox!=null) {
                                        mosse.init(croppedMat,boundingBox );
                                        boolean isValid= mosse.update(croppedMat,boundingBox);
                                    }
                                }


//                                croppedMat = bitmapConversion.convertBitmap2Mat(mCroppedBitmap);
//                                if(boundingBox!=null &&countF<=5) {
//                                    if(detectDlib&&countF ==0)
//                                        mosse.init(croppedMat,boundingBox );
//                                    boolean isValid= mosse.update(croppedMat,boundingBox);
//                                    countF+=1;
//                                }
//                                else {
//                                    countF = 0;
//
//                                    detectDlib = true;
//                                }
                            }

                            long endTime = System.currentTimeMillis();
                            Log.d(TAG, "run: " + "Time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");
                            // Draw on bitmap
                            //bug here results is = 0

                            if (results != null) {
                                // Notify results
                                //faceLandmarkListener.landmarkUpdate(results, mCroppedBitmap.getWidth(), mCroppedBitmap.getHeight());
                                // Demo results
//                                drawOnResults(results);

                            }
                            mIsComputing = false;
                        }
                    });

        Trace.endSection();
    }

    private void drawOnResults(List<VisionDetRet> results) {
        for (final VisionDetRet ret : results) {
            float resizeRatio = 1.0f;
            Rect bounds = new Rect();
            bounds.left = (int) (ret.getLeft() * resizeRatio);
            bounds.top = (int) (ret.getTop() * resizeRatio);
            bounds.right = (int) (ret.getRight() * resizeRatio);
            bounds.bottom = (int) (ret.getBottom() * resizeRatio);
            Canvas canvas = new Canvas(mCroppedBitmap);
            canvas.drawRect(bounds, mFaceLandmarkPaint);

            // Draw landmark
            ArrayList<Point> landmarks = ret.getFaceLandmarks();
            for (Point point : landmarks) {
                int pointX = (int) (point.x * resizeRatio);
                int pointY = (int) (point.y * resizeRatio);
                canvas.drawCircle(pointX, pointY, 2, mFaceLandmarkPaint);
            }
        }

//        mUIHandler.post(() -> {
//            mWindow.setImageBitmap(mCroppedBitmap);
//        });
    }

    private void drawOnResultsByBoundingBox(Rect2d boundingBox) {

        float resizeRatio = 1.0f;
        Rect bounds = new Rect();
        bounds.left = (int) (boundingBox.x * resizeRatio);
        bounds.top = (int) (boundingBox.y * resizeRatio);
        bounds.right = (int) ((boundingBox.x + boundingBox.width) * resizeRatio);
        bounds.bottom = (int) ((boundingBox.y + boundingBox.height) * resizeRatio);
        Canvas canvas = new Canvas(mCroppedBitmap);
        canvas.drawRect(bounds, mFaceLandmarkPaint);

    }
}
