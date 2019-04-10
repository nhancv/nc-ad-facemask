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
import android.graphics.RectF;
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

import org.opencv.video.SparsePyrLKOpticalFlow;
import hugo.weaving.DebugLog;

import static org.opencv.core.CvType.CV_32F;
import static org.opencv.core.CvType.CV_32FC2;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
public class OnGetImageListener implements OnImageAvailableListener {

    private static final String TAG = "OnGetImageListener";

    private static final int BM_FACE_W = 200;
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
    private String cameraId;
    private FaceLandmarkListener faceLandmarkListener;
    private List<VisionDetRet> results;
    private List<VisionDetRet> visionDetRets = new ArrayList<>();
    private BitmapConversion bitmapConversion = new BitmapConversion();
    private Paint greenPaint, redPaint, bluePaint;
    //Mosse tracker
    private Tracker mosse = TrackerMOSSE.create();
    //Create Sparse Optical Flow LK
    //default sparse optical flow config.
    private SparsePyrLKOpticalFlow opticalFlow = SparsePyrLKOpticalFlow.create();
    private MatConversion matConversion = new MatConversion();
    private Mat firstDetectGrayMat = new Mat();
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

    private long lastTime = 0;
    //MOSSE Tracking
    private RectF originBox = new RectF();
    //private Rect2d firstDetectBoundingBox = new Rect2d();
    //private Mat firstDetectCroppedMat = new Mat();

    private Rect2d boundingBox = new Rect2d();
    private Rect2d oldBoundingBox = new Rect2d();
    //Optical Flow LK tracking
    private Mat prevPts = new Mat(5,2,CV_32F);

    private Mat prevImg = new Mat();
    private List<Point> prevPtsList = new ArrayList<>();

    public OnGetImageListener() {
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

        if (!new File(Constants.getFaceShapeModelPath()).exists()) {
            //FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath());
            FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_5_face_landmarks, Constants.getFaceShapeModelPath());
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
        synchronized (OnGetImageListener.this) {
            if (mFaceDet != null) {
                mFaceDet.release();
            }
            renderFps.stop();
            detectFps.stop();
        }
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {

        if (step1PreImageProcess(reader)) return;

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
            return true;
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
        return false;
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
        results = mFaceDet.detect(bmp32);

        long endTime = System.currentTimeMillis();
        Log.d(TAG, "Detect face time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");

        //convert mCroppedBitmap to cropped Mat to start init
        firstDetectGrayMat = bitmapConversion.convertBitmap2GrayMat(bmp32);

        if (results.size() > 0) {

            if (visionDetRets.size() == 0) {
                visionDetRets.add(results.get(0));
            } else {
                visionDetRets.set(0, results.get(0));
            }

            VisionDetRet ret = visionDetRets.get(0);
            float x = ret.getLeft();
            float y = ret.getTop();
            float w = ret.getRight() - x;
            float h = ret.getBottom() - y;
            //prePts
            prevPts = matConversion.convertPts2OpenCVPts(ret.getFaceLandmarks());
            //clone the first detect crop Mats
            prevImg = firstDetectGrayMat;
            prevPtsList = ret.getFaceLandmarks();

//
//            originBox.left = ret.getLeft();
//            originBox.right = ret.getRight();
//            originBox.bottom = ret.getBottom();
//            originBox.top = ret.getTop();

            //update old bounding box = bounding box
//            oldBoundingBox.x = x;
//            oldBoundingBox.y = y;
//            oldBoundingBox.width = w;
//            oldBoundingBox.height = h;
//
//            firstDetectBoundingBox.x = x;
//            firstDetectBoundingBox.y = y;
//            firstDetectBoundingBox.width = w;
//            firstDetectBoundingBox.height = h;

//            if(firstDetectCroppedMat != null && !firstDetectCroppedMat.size().empty()) {
//                trackingHandler.post(() -> {
//                    Log.e(TAG, "step2FaceDetProcess: init mosse " + firstDetectBoundingBox);
//                    mosse = TrackerMOSSE.create();
//                    mosse.init(firstDetectCroppedMat, firstDetectBoundingBox);
//                });
//            }
            Log.d(TAG, "detectFrame" + oldBoundingBox);
        }
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
        if (prevPts!=null && prevImg!=null && !prevImg.empty()&&!prevPts.empty()) {
            Bitmap bmp32 = mCroppedBitmap.copy(mCroppedBitmap.getConfig(), true);
            Mat status = new Mat();
            Log.d(TAG,"Previous Points"+prevPts);
            //final Mat croppedMat = bitmapConversion.convertBitmap2Mat(bmp32);
            final Mat croppedGrayMat = bitmapConversion.convertBitmap2GrayMat(bmp32);
            long startTime = System.currentTimeMillis();
            //udpate bounding Box based on init boundingBox
//            boolean canTrack = mosse.update(croppedMat, boundingBox);
            //the same type with prePts
            Mat nextPts = new Mat(prevPts.rows(),1,CV_32FC2);
            Log.d(TAG,"Next Point "+nextPts);
            opticalFlow.calc(prevImg,croppedGrayMat,prevPts,nextPts,status);
            //Log.d(TAG, "BoundingBoxTracking" + boundingBox);

            long endTime = System.currentTimeMillis();

//            Log.d(TAG, "Tracking time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec with " + canTrack);
            Log.d(TAG, "Tracking time cost: " + String.valueOf((endTime - startTime) / 1000f));
            // Depend on (visionDetRets + oldBoundingBox ) combine with boundingBox => visionDetRets
            //Case 2: visionDetRet: 1 + oldBoundingBox: 1 + boundingBox 0 =>
            //if a bounding box can track
            VisionDetRet newRet;
            List<Point> pointsList = new ArrayList<>();
            List<Byte> statusList = new ArrayList<>();
            matConversion.convertMatPts2(nextPts,pointsList);
            matConversion.convertMat2ByteList(status,statusList);
            Log.d(TAG,"statusList result: "+statusList);
            for(int i = 0;i<pointsList.size();i++){
                //if point is good we keep
                if(statusList.get(i)!=0){
                    Log.d(TAG,"valid point");
                    continue;
                }
                //replace with prePoint
                pointsList.set(i,prevPtsList.get(i));
            }


            VisionDetRet ret = visionDetRets.get(0);
            newRet = normResult(ret,pointsList);
            visionDetRets.set(0,newRet);

            //update for next tracking
            prevImg = croppedGrayMat;
            prevPts = nextPts;
            prevPtsList = pointsList;

            //Convert from List Of next points to prePts matrix
//            if (canTrack) {
//                //Case 1: visionDetRet: 1 + oldBoundingBox: 1 + boundingBox 1 => new visionDetRets
//                if (visionDetRets.size() > 0 && !oldBoundingBox.empty()) {
//                    VisionDetRet ret = visionDetRets.get(0);
//
//                    //using the current bound box to get landmarks and compare with old bounding box
//                    newRet = normResult(ret, boundingBox);
//                    visionDetRets.set(0, newRet);//update with newRect
//
//                    //update oldboundingBox with bounding box
//                    oldBoundingBox.x = boundingBox.x;
//                    oldBoundingBox.y = boundingBox.y;
//                    oldBoundingBox.width = boundingBox.width;
//                    oldBoundingBox.height = boundingBox.height;
//                }
//
//            }

        }
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

        if (faceLandmarkListener != null && visionDetRets != null && mCroppedBitmap != null && tvFps != null) {
            if (!visionDetRets.isEmpty()) {
                Bitmap bm32 = drawOnResultBoundingBox(visionDetRets.get(0));
                if (mUIHandler != null) {
                    mUIHandler.post(() -> {
                        tvFps.setText(log);
                        ivOverlay.setImageBitmap(bm32);
                    });
                }
            }
//            faceLandmarkListener.landmarkUpdate(visionDetRets, mCroppedBitmap.getWidth(), mCroppedBitmap.getHeight());

        }
    }
    /*
    * Read: next points
    *Write: next Bounding
    * */
    private VisionDetRet normResult(VisionDetRet ret, List<Point> nextPoints){
        VisionDetRet result;
        int x1 =prevPtsList.get(0).x;
        int y1 = prevPtsList.get(0).y;
        int x2 = nextPoints.get(0).x;
        int y2 = nextPoints.get(0).y;
        int translateX = x2-x1;
        int translateY = y2 -y1;

        int boxWidth = ret.getBottom()-ret.getTop();
        int boxHeight = ret.getRight() - ret.getLeft();

        int prevEyeWidth = prevPtsList.get(2).x - x1;
        int width = nextPoints.get(2).x - x2;
        float wRatio = 1.0f*width/prevEyeWidth;
        int prevEyeHeight = prevPtsList.get(4).y - y1;
        int height = nextPoints.get(4).y - y2;
        float hRatio = 1.0f*height/prevEyeHeight;

        int newBoxWidth = (int) (boxWidth *wRatio);
        int newBoxHeight = (int) (boxHeight *hRatio);
        int boxX = ret.getLeft() +translateX;
        int boxY = ret.getTop() +translateY;

        String label = "p1";
        Point p1 = nextPoints.get(0);
        Point p2 = nextPoints.get(2);
        result = new VisionDetRet(label,100f,boxX,boxY,boxX+newBoxWidth,boxY+newBoxHeight);
        for (int i = 0; i < nextPoints.size(); i++) {
            result.addLandmark(nextPoints.get(i).x,nextPoints.get(i).y);
        }

        return result;
    }
    private VisionDetRet normResult(VisionDetRet ret, Rect2d boundingBox) {
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

        float xRatio = (float) ((float)boundingBox.width/oldBoundingBox.width);//x Scale Ratio
        float yRatio = (float)((float)boundingBox.height/oldBoundingBox.height);//y Scale Ratio
        int offsetX = (int) ((x - oldX)*xRatio);
        int offsetY = (int) ((y - oldY)*yRatio);
        List<Point> oldLandmarks = ret.getFaceLandmarks();
        for (int i = 0; i < oldLandmarks.size(); i++) {
            result.addLandmark(oldLandmarks.get(i).x + offsetX, oldLandmarks.get(i).y + offsetY);
        }
        return result;
    }

    private Bitmap drawOnResultBoundingBox(VisionDetRet visionDetRet) {
        Bitmap bm32 = mCroppedBitmap.copy(mCroppedBitmap.getConfig(), true);

        Rect bounds = new Rect(visionDetRet.getLeft(), visionDetRet.getTop(), visionDetRet.getRight(), visionDetRet.getBottom());
        Canvas canvas = new Canvas(bm32);
        canvas.drawRect(bounds, greenPaint);

        if (boundingBox != null && !boundingBox.empty()) {
            RectF r = new RectF((float) boundingBox.x, (float) boundingBox.y,
                    (float) (boundingBox.x + boundingBox.width), (float) (boundingBox.y + boundingBox.height));
            canvas.drawRect(r, redPaint);
        }

        if (originBox != null) {
            canvas.drawRect(originBox, bluePaint);
        }

        List<Point> landmarks = visionDetRet.getFaceLandmarks();
        for (Point landmark : landmarks) {
            canvas.drawPoint(landmark.x, landmark.y, greenPaint);
        }

        return bm32;
    }

}
