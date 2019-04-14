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
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.nhancv.facemask.FaceLandmarkListener;
import com.nhancv.facemask.fps.StableFps;
import com.nhancv.facemask.util.STUtils;

import java.util.ArrayList;
import java.util.List;

import zeusees.tracking.Face;
import zeusees.tracking.FaceTracking;

public class FaceTrackingListener implements OnImageAvailableListener {

    private static final String TAG = "FaceTrackingListener";
    private static final int MESSAGE_DRAW_POINTS = 100;

    private static final int BM_FACE_W = 300;
    private static int BM_FACE_H = BM_FACE_W;
    private int previewWidth = 0;
    private int previewHeight = 0;
    private byte[][] yuvBytes;
    private int[] rgbBytes = null;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap rbgFrameBitmap2 = null;
    private Bitmap croppedBitmap = null;

    private Context context;
    private Handler trackingHandler;
    private Handler faceDetectionHandler;
    private Handler uiHandler;
    private Handler postImageHandler;
    private ImageView ivOverlay;
    private TextView tvFps;
    private String cameraId;
    private FaceLandmarkListener faceLandmarkListener;
    private Paint greenPaint, redPaint, bluePaint;
    private TextureView cameraTextureView;
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
    private HandlerThread handlerThread;
    private Handler handler;

    private Paint paint;
    private byte[] nv21Data;
    private byte[] tmpBuffer;
    private FaceTracking multiTrack106 = null;
    private boolean mTrack106 = false;
    private SurfaceView overlap;
    private Matrix matrix = new Matrix();
    private Face face;

    public FaceTrackingListener() {
        renderFps = new StableFps(40);
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
            final TextureView cameraTextureView,
            final SurfaceView overlap,
            final Matrix transformMatrix) {
        this.context = context;
        this.cameraId = cameraId;
        this.trackingHandler = trackingHandler;
        this.faceDetectionHandler = faceDetectionHandler;
        this.uiHandler = mUIHandler;
        this.postImageHandler = mPostImageHandler;

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

        this.cameraTextureView = cameraTextureView;
        this.overlap = overlap;
        this.matrix = transformMatrix;
        if (multiTrack106 == null) {
            multiTrack106 = new FaceTracking("/sdcard/ZeuseesFaceTracking/models");
        }
        paint = new Paint();
        paint.setColor(Color.rgb(57, 138, 243));
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.FILL);

        handlerThread = new HandlerThread("SurfaceRender");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DRAW_POINTS) {
//                    synchronized (lockObj) {

                        synchronized (nv21Data) {
                            System.arraycopy(nv21Data, 0, tmpBuffer, 0, nv21Data.length);
                            if (!mTrack106) {
                                multiTrack106.FaceTrackingInit(tmpBuffer, previewHeight, previewWidth);
                                mTrack106 = true;
                            } else {
                                multiTrack106.Update(tmpBuffer, previewHeight, previewWidth);
                            }

//                            List<Face> faceActions = new ArrayList<>(multiTrack106.getTrackingInfo());
                            List<Face> faceActions = multiTrack106.getTrackingInfo();

                            if (faceActions != null) {

                                if (!overlap.getHolder().getSurface().isValid()) {
                                    return;
                                }

                                Canvas canvas = overlap.getHolder().lockCanvas();
                                if (canvas == null)
                                    return;

                                canvas.drawColor(0, PorterDuff.Mode.CLEAR);
                                canvas.setMatrix(matrix);
                                boolean rotate270 = true;
                                for (Face r : faceActions) {

                                    Rect rect = new Rect(previewHeight - r.left, r.top, previewHeight - r.right, r.bottom);

                                    PointF[] points = new PointF[106];
                                    for (int i = 0; i < 106; i++) {
                                        points[i] = new PointF(r.landmarks[i * 2], r.landmarks[i * 2 + 1]);
                                    }

                                    float[] visibles = new float[106];


                                    for (int i = 0; i < points.length; i++) {
                                        visibles[i] = 1.0f;
                                        if (rotate270) {
                                            points[i].x = previewHeight - points[i].x;
                                        }
                                    }

                                    STUtils.drawFaceRect(canvas, rect, previewHeight,
                                            previewWidth, true);
                                    STUtils.drawPoints(canvas, paint, points, visibles, previewHeight,
                                            previewWidth, true);

                                }
                                overlap.getHolder().unlockCanvasAndPost(canvas);
                            }
                        }
//                    }

                }
            }
        };

    }

    public void deInitialize() {
        synchronized (FaceTrackingListener.this) {
            renderFps.stop();
            detectFps.stop();
            mTrack106 = false;
            if (multiTrack106 != null) {
                multiTrack106 = null;
            }
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
            try {
                handlerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        handlerThread = null;
        handler = null;
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {

        if (!step1PreImageProcess(reader)) return;

        // Fps for detect face
//        if (!detectFps.isStarted()) {
//            detectFps.start(fps -> {
//                if (faceDetectionHandler != null && croppedBitmap != null) {
//                    faceDetectionHandler.post(this::step2FaceDetProcess);
//                }
//            });
//        }

//        trackingHandler.post(this::step3TrackingProcess);

        // Fps for render to ui thread
//        if (!renderFps.isStarted()) {
//            renderFps.start(fps -> {
//                if (postImageHandler != null) {
//                    postImageHandler.post(() -> step4RenderProcess(fps));
//                }
//            });
//        }
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
            if (previewWidth != image.getWidth() || previewHeight != image.getHeight()) {
                previewWidth = image.getWidth();
                previewHeight = image.getHeight();
                Log.d(TAG, String.format("Initializing at size %dx%d", previewWidth, previewHeight));

                nv21Data = new byte[previewWidth * previewHeight * 2];
                tmpBuffer = new byte[previewWidth * previewHeight * 2];

                rgbBytes = new int[previewWidth * previewHeight];
                rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
                rbgFrameBitmap2 = Bitmap.createBitmap(previewHeight, previewWidth, Config.ARGB_8888);
                float scaleInputRate = Math.max(previewWidth, previewHeight) * 1f / Math.min(previewWidth, previewHeight);
                BM_FACE_H = (int) (BM_FACE_W * scaleInputRate);
                croppedBitmap = Bitmap.createBitmap(rgbFrameBitmap, 0, 0, previewWidth, previewHeight);
                yuvBytes = new byte[planes.length][];
                for (int i = 0; i < planes.length; ++i) {
                    yuvBytes[i] = new byte[planes[i].getBuffer().capacity()];
                }
            }

            synchronized (lockObj) {
                byte[] data = ImageUtil.convertYUV420ToNV21(image);
                System.arraycopy(data, 0, nv21Data, 0, data.length);
                image.close();

                handler.removeMessages(MESSAGE_DRAW_POINTS);
                handler.sendEmptyMessage(MESSAGE_DRAW_POINTS);
            }
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Log.e(TAG, "Exception!", e);
            return false;
        }
        return true;
    }

    /**
     * Process and output: visionDetRets, oldBoundingBox
     * <p>
     * Write: results, visionDetRets, oldBoundingBox
     * Read: croppedBitmap (clone)
     */
    /***
     * Process and output: preImg, prevPts
     * Write: prImg, prevPts(clone), prePtsList
     *
     * */
    private void step2FaceDetProcess() {
        long startTime = System.currentTimeMillis();

//        Bitmap bmp32 = croppedBitmap.copy(croppedBitmap.getConfig(), true);
//        results = mFaceDet.detect(bmp32);

        synchronized (nv21Data) {
            if (!mTrack106) {
                multiTrack106.FaceTrackingInit(nv21Data, previewHeight, previewWidth);
                mTrack106 = !mTrack106;
            } else {
                multiTrack106.Update(nv21Data, previewHeight, previewWidth);
            }
        }

        long endTime = System.currentTimeMillis();
        Log.d(TAG, "Detect face time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");

    }

    /**
     * Tracking and update: visionDetRets depend on boundingBox, oldBoundingBox
     * <p>
     * Write: boundingBox, oldBoundingBox, visionDetRets
     * Read: croppedBitmap (clone), visionDetRets
     */
    /**
     * Tracking and update: visionDetRets depend on preImg, nextImg, prevPts, prevPtsList
     * <p>
     * Write: prevImg,prePts, PrevPtsList visionDetRets =>NextPts
     * Read: croppedBitmap (clone), visionDetRets, PrevPtList
     */
    private void step3TrackingProcess() {

    }

    /**
     * Render object by: visionDetRets, croppedBitmap
     * Read only
     */
    private void step4RenderProcess(int fps) {
        final String log;

        List<Face> faceActions = new ArrayList<>(multiTrack106.getTrackingInfo());

        if (faceActions != null) {

            if (!overlap.getHolder().getSurface().isValid()) {
                return;
            }

            Canvas canvas = overlap.getHolder().lockCanvas();
            if (canvas == null)
                return;

            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            canvas.setMatrix(matrix);
            boolean rotate270 = true;
            for (Face r : faceActions) {

                Rect rect = new Rect(previewHeight - r.left, r.top, previewHeight - r.right, r.bottom);

                PointF[] points = new PointF[106];
                for (int i = 0; i < 106; i++) {
                    points[i] = new PointF(r.landmarks[i * 2], r.landmarks[i * 2 + 1]);
                }

                float[] visibles = new float[106];


                for (int i = 0; i < points.length; i++) {
                    visibles[i] = 1.0f;
                    if (rotate270) {
                        points[i].x = previewHeight - points[i].x;
                    }
                }

                STUtils.drawFaceRect(canvas, rect, previewHeight,
                        previewWidth, true);
                STUtils.drawPoints(canvas, paint, points, visibles, previewHeight,
                        previewWidth, true);

            }
            overlap.getHolder().unlockCanvasAndPost(canvas);
        }

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
            if (uiHandler != null) {
                uiHandler.post(() -> {
                    tvFps.setText(log);
//                    ivOverlay.setImageBitmap(bm32);
                });
            }
//            faceLandmarkListener.landmarkUpdate(visionDetRets, croppedBitmap.getWidth(), croppedBitmap.getHeight());

        }
    }

    private Bitmap drawOnResultBoundingBox() {
        Bitmap bm32 = rbgFrameBitmap2.copy(rbgFrameBitmap2.getConfig(), true);

        if (face != null) {
            Canvas canvas = new Canvas(bm32);

            for (int i = 0; i < face.landmarks.length; i += 2) {
                canvas.drawCircle(face.landmarks[i], face.landmarks[i + 1], 5, redPaint);
            }


            Rect rect = new Rect(previewHeight - face.left, face.top, previewHeight - face.right, face.bottom);

            int left = rect.left;
            rect.left = previewHeight - rect.right;
            rect.right = previewHeight - left;
            canvas.drawRect(rect, greenPaint);

        }

        return bm32;
    }

}
