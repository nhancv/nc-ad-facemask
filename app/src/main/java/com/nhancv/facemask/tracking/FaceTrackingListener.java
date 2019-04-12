package com.nhancv.facemask.tracking;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.nhancv.facemask.FaceLandmarkListener;
import com.nhancv.facemask.R;
import com.nhancv.facemask.fps.StableFps;
import com.nhancv.facemask.util.EGLUtils;
import com.nhancv.facemask.util.GLBitmap;
import com.nhancv.facemask.util.GLFrame;
import com.nhancv.facemask.util.GLFramebuffer;
import com.nhancv.facemask.util.GLPoints;
import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;
import com.tzutalin.dlibtest.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import hugo.weaving.DebugLog;
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
    private FaceDet mFaceDet;
    private ImageView ivOverlay;
    private TextView tvFps;
    private String cameraId;
    private FaceLandmarkListener faceLandmarkListener;
    private List<VisionDetRet> results;
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
        this.context = context;
        this.cameraId = cameraId;
        this.trackingHandler = trackingHandler;
        this.mFaceDetectionHandler = faceDetectionHandler;
        this.mUIHandler = mUIHandler;
        this.mPostImageHandler = mPostImageHandler;

        this.faceLandmarkListener = faceLandmarkListener;
        this.mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        this.ivOverlay = ivOverlay;
        this.tvFps = tvFps;


        initModelFiles();

        mMultiTrack106 = new FaceTracking("/sdcard/ZeuseesFaceTracking/models");

        if (!new File(Constants.getFaceShapeModelPath()).exists()) {
            //FileUtils.copyFileFromRawToOthers(context, R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath());
            FileUtils.copyFileFromRawToOthers(context, R.raw.shape_predictor_5_face_landmarks, Constants.getFaceShapeModelPath());
        }
        greenPaint = new Paint();
        greenPaint.setColor(Color.GREEN);
        greenPaint.setStrokeWidth(2);
        greenPaint.setStyle(Paint.Style.STROKE);

        redPaint = new Paint();
        redPaint.setColor(Color.RED);
        redPaint.setStrokeWidth(1);
        redPaint.setStyle(Paint.Style.STROKE);

        bluePaint = new Paint();
        bluePaint.setColor(Color.BLUE);
        bluePaint.setStrokeWidth(1);
        bluePaint.setStyle(Paint.Style.STROKE);
    }

    @DebugLog
    public void deInitialize() {
        synchronized (FaceTrackingListener.this) {
            if (mFaceDet != null) {
                mFaceDet.release();
            }
            renderFps.stop();
            detectFps.stop();
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

        if (results != null && results.size() > 0) {
            //start tracking after finishing face detection
            trackingHandler.post(this::step3TrackingProcess);
        }

        // Fps for render to ui thread
        if (!renderFps.isStarted()) {
            renderFps.start(fps -> {
                if (mPostImageHandler != null) {
                    mPostImageHandler.post(() -> step4RenderProcess(fps));
                }
            });
        }
    }


    private byte[] mNv21Data;
    private EGLUtils mEglUtils;
    private GLFramebuffer mFramebuffer;
    private GLFrame mFrame;
    private GLPoints mPoints;
    private GLBitmap mBitmap;
    private FaceTracking mMultiTrack106 = null;
    private boolean mTrack106 = false;
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
                mFramebuffer = new GLFramebuffer();
                mFrame = new GLFrame();
                mPoints = new GLPoints();
//                mBitmap = new GLBitmap(, R.drawable.ic_action_info);

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
//                byte[] data = ImageUtil.YUV_420_888toNV21(image);
                byte[] data = ImageUtil.convertYUV420ToNV21(image);
                System.arraycopy(data, 0, mNv21Data, 0, data.length);
            }

//            if (mEglUtils == null) {
//                return false;
//            }

            if (mTrack106) {
                mMultiTrack106.FaceTrackingInit(mNv21Data, mPreviewHeight, mPreviewWidth);
                mTrack106 = !mTrack106;
            } else {
                mMultiTrack106.Update(mNv21Data, mPreviewHeight, mPreviewWidth);
            }
            List<Face> faceActions = mMultiTrack106.getTrackingInfo();

            float[] p = null;
            float[] points = null;
            for (Face r : faceActions) {
                face = r;
                points = new float[106 * 2];

                Rect rect = new Rect(mPreviewHeight - r.left, r.top, mPreviewHeight - r.right, r.bottom);
                Log.d(TAG, "step1PreImageProcess: " + rect);
                for (int i = 0; i < 106; i++) {
                    int x = mPreviewHeight - r.landmarks[i * 2];
                    int y = r.landmarks[i * 2 + 1];
                    points[i * 2] = view2openglX(x, mPreviewHeight);
                    points[i * 2 + 1] = view2openglY(y, mPreviewWidth);
                    if (i == 70) {
                        p = new float[8];
                        p[0] = view2openglX(x + 20, mPreviewHeight);
                        p[1] = view2openglY(y - 20, mPreviewWidth);
                        p[2] = view2openglX(x - 20, mPreviewHeight);
                        p[3] = view2openglY(y - 20, mPreviewWidth);
                        p[4] = view2openglX(x + 20, mPreviewHeight);
                        p[5] = view2openglY(y + 20, mPreviewWidth);
                        p[6] = view2openglX(x - 20, mPreviewHeight);
                        p[7] = view2openglY(y + 20, mPreviewWidth);
                    }
                }
                if (p != null) {
                    break;
                }
            }

            if (points != null) {
                for (float point : points) {
//                Log.e(TAG, "step1PreImageProcess: " + point);
                }
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

            image.close();
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
            Bitmap bm32 = drawOnResultBoundingBox();
            if (mUIHandler != null) {
                mUIHandler.post(() -> {
                    tvFps.setText(log);
                    ivOverlay.setImageBitmap(bm32);
                });
            }
//            faceLandmarkListener.landmarkUpdate(visionDetRets, mCroppedBitmap.getWidth(), mCroppedBitmap.getHeight());

        }
    }

    private Bitmap drawOnResultBoundingBox() {
        Bitmap bm32 = mRGBframeBitmap2.copy(mRGBframeBitmap2.getConfig(), true);

        Log.e(TAG, "drawOnResultBoundingBox: " + face);
        if (face != null) {
            Canvas canvas = new Canvas(bm32);

            for (int i = 0; i < face.landmarks.length; i+=2) {
                canvas.drawCircle(face.landmarks[i], face.landmarks[i+1], 5, redPaint);
            }


            canvas.drawRect(new Rect(10, 10, 100, 100), greenPaint);

        }
//
//        Rect bounds = new Rect(visionDetRet.getLeft(), visionDetRet.getTop(), visionDetRet.getRight(), visionDetRet.getBottom());
//        Canvas canvas = new Canvas(bm32);
//        canvas.drawRect(bounds, greenPaint);
//
//        if (boundingBox != null && !boundingBox.empty()) {
//            RectF r = new RectF((float) boundingBox.x, (float) boundingBox.y,
//                    (float) (boundingBox.x + boundingBox.width), (float) (boundingBox.y + boundingBox.height));
//            canvas.drawRect(r, redPaint);
//        }
//
//        if (originBox != null) {
//            canvas.drawRect(originBox, bluePaint);
//        }
//
//        List<Point> landmarks = visionDetRet.getFaceLandmarks();
//        for (Point landmark : landmarks) {
//            canvas.drawPoint(landmark.x, landmark.y, greenPaint);
//        }
//
        return bm32;
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

    private void initModelFiles() {
        String assetPath = "ZeuseesFaceTracking";
        String sdcardPath = Environment.getExternalStorageDirectory()
                + File.separator + assetPath;
        copyFilesFromAssets(context, assetPath, sdcardPath);
    }

    private void copyFilesFromAssets(Context context, String oldPath, String newPath) {
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
}
