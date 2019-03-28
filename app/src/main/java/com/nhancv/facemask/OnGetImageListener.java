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

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;
import com.tzutalin.dlibtest.FileUtils;
import com.tzutalin.dlibtest.ImageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import hugo.weaving.DebugLog;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
public class OnGetImageListener implements OnImageAvailableListener {

    private static final int INPUT_SIZE = 150;
    private static final String TAG = "OnGetImageListener";

    private int mScreenRotation = 0;

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
    private Paint mFaceLandmarkPaint;
    private String cameraId;
    private FaceLandmarkListener faceLandmarkListener;
    /**
     * 0 forback camera
     * 1 for front camera
     * Initlity default camera is front camera
     */
    public static final String CAMERA_FRONT = "1";
    public static final String CAMERA_BACK = "0";

    @DebugLog
    public void initialize(
            final Context context,
            final String cameraId,
            final ImageView imageView,
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
        }
    }

    @DebugLog
    private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {

//        Display getOrient = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
//        Point point = new Point();
//        getOrient.getSize(point);
//        int screen_width = point.x;
//        int screen_height = point.y;
//        Log.d(TAG, String.format("screen size (%d,%d)", screen_width, screen_height));
//        if (screen_width < screen_height) {
//            if(cameraId.equals(CAMERA_BACK)){
//                mScreenRotation = 90;
//            } else {
                mScreenRotation = -90;
//            }
//        } else {
//            mScreenRotation = 0;
//        }
//
        final float minDim = Math.min(src.getWidth(), src.getHeight());

        final Matrix matrix = new Matrix();

        // We only want the center square out of the original rectangle.
        final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);//-120
        final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);//0
        matrix.preTranslate(translateX, translateY);//translate

        final float scaleFactor = dst.getHeight() / minDim;//150/720
        matrix.postScale(scaleFactor, scaleFactor);

        // Rotate around the center if necessary.
//        if (mScreenRotation != 0) {
            matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f); //translate to origin
            matrix.postRotate(mScreenRotation);
            matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f); //translate back
//        }
//        if(this.cameraId.equals(CAMERA_FRONT)) {
            matrix.postScale(-1,1);//matrix[-0.0,-0.208,0  -0.2083,0.0,175.0; 0,0,1]
            matrix.postTranslate(dst.getWidth(),0);//scale image back
//        }
        final Canvas canvas = new Canvas(dst);

        canvas.drawBitmap(src, matrix, null);
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
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
                float rate = Math.max(mPreviewWidth, mPreviewHeight) * 1f / Math.min(mPreviewWidth, mPreviewHeight);
                mCroppedBitmap = Bitmap.createBitmap(INPUT_SIZE, (int) (INPUT_SIZE * rate), Config.ARGB_8888);

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
        drawResizedBitmap(mRGBframeBitmap, mCroppedBitmap);

        if (mInferenceHandler != null)
            mInferenceHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (!new File(Constants.getFaceShapeModelPath()).exists()) {
                                FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath());
                            }

                            long startTime = System.currentTimeMillis();

                            List<VisionDetRet> results;
                            synchronized (OnGetImageListener.this) {
                                results = mFaceDet.detect(mCroppedBitmap);
                            }
                            long endTime = System.currentTimeMillis();
                            Log.d(TAG, "run: " + "Time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");
                            // Draw on bitmap
                            if (results != null) {

                                //Update 3d model
                                faceLandmarkListener.landmarkUpdate(results);

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
                            }

                        mUIHandler.post(() -> {
                            mWindow.setImageBitmap(mCroppedBitmap);
                        });

                            mIsComputing = false;
                        }
                    });

        Trace.endSection();
    }
}
