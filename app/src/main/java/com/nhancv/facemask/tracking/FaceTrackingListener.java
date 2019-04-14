package com.nhancv.facemask.tracking;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;

import com.nhancv.facemask.FaceLandmarkListener;
import com.nhancv.facemask.fps.StableFps;
import com.nhancv.facemask.util.STUtils;

import zeusees.tracking.Face;
import zeusees.tracking.FaceTracking;

public class FaceTrackingListener implements OnImageAvailableListener {

    /**
     * Static
     */
    private static final String TAG = "FaceTrackingListener";
    private static final int FRAME_DATA_READY_MSG = 0x01;

    /**
     * Thread
     */
    private HandlerThread trackingThread;
    private Handler trackingHandler;

    private HandlerThread postImageProcessThread;
    private Handler postImageHandler;

    /**
     * Inject
     */
    private Matrix transformMatrix;
    private SurfaceView overlapFaceView;
    private Handler uiHandler;
    private FaceLandmarkListener faceLandmarkListener;

    /**
     * Global vars
     */
    private int previewWidth;
    private int previewHeight;
    private Paint redPaint;
    private StableFps renderFps;
    private long lastTime;
    private final Object lockObj;


    private Paint landmarkPaint;
    private byte[] nv21Data;
    private byte[] tmpBuffer;
    private FaceTracking multiTrack106;
    private boolean mTrack106;

    public FaceTrackingListener() {
        lockObj = new Object();
    }

    public void initialize(
            final Matrix transformMatrix,
            final SurfaceView overlapFaceView,
            final FaceLandmarkListener faceLandmarkListener,
            final Handler uiHandler) {
        this.transformMatrix = transformMatrix;
        this.overlapFaceView = overlapFaceView;
        this.faceLandmarkListener = faceLandmarkListener;
        this.uiHandler = uiHandler;

        renderFps = new StableFps(30);

        redPaint = new Paint();
        redPaint.setColor(Color.RED);
        redPaint.setStrokeWidth(1);
        redPaint.setStyle(Paint.Style.STROKE);

        landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.BLUE);
        landmarkPaint.setStrokeWidth(1);
        landmarkPaint.setStyle(Paint.Style.FILL);

        if (multiTrack106 == null) {
            multiTrack106 = new FaceTracking("/sdcard/ZeuseesFaceTracking/models");
        }

        // Init threads
        postImageProcessThread = new HandlerThread("PostImageProcessingThread");
        postImageProcessThread.start();
        postImageHandler = new Handler(postImageProcessThread.getLooper());

        trackingThread = new HandlerThread("TrackingThread");
        trackingThread.start();
        trackingHandler = new Handler(trackingThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == FRAME_DATA_READY_MSG) {
                    System.arraycopy(nv21Data, 0, tmpBuffer, 0, nv21Data.length);
                    if (!mTrack106) {
                        multiTrack106.faceTrackingInit(tmpBuffer, previewHeight, previewWidth);
                        mTrack106 = true;
                    } else {
                        multiTrack106.faceTrackingUpdate(tmpBuffer, previewHeight, previewWidth);
                    }
                }
            }
        };

    }

    public void deInitialize() {
        renderFps.stop();

        mTrack106 = false;
        if (multiTrack106 != null) {
            multiTrack106 = null;
        }

        try {
            if (postImageProcessThread != null) {
                postImageProcessThread.quitSafely();
                postImageProcessThread.join();
            }
            postImageProcessThread = null;
            postImageHandler = null;

            if (trackingThread != null) {
                trackingThread.quitSafely();
                trackingThread.join();
            }
            trackingThread = null;
            trackingHandler = null;

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onImageAvailable(final ImageReader reader) {

        if (!preImageProcess(reader)) return;
        // Fps for render to ui thread
        if (!renderFps.isStarted()) {
            renderFps.start(fps -> {
                if (postImageHandler != null) {
                    postImageHandler.post(() -> frameRenderProcess(fps));
                }
            });
        }
    }

    private boolean preImageProcess(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return true;
            }
            // Initialize the storage bitmaps once when the resolution is known.
            if (previewWidth != image.getWidth() || previewHeight != image.getHeight()) {
                previewWidth = image.getWidth();
                previewHeight = image.getHeight();
                Log.d(TAG, String.format("Tracking at size %dx%d", previewWidth, previewHeight));

                nv21Data = new byte[previewWidth * previewHeight * 2];
                tmpBuffer = new byte[previewWidth * previewHeight * 2];
            }

            synchronized (lockObj) {
                byte[] data = ImageUtil.convertYUV420ToNV21(image);
                System.arraycopy(data, 0, nv21Data, 0, data.length);
                image.close();

                if (trackingHandler != null) {
                    trackingHandler.removeMessages(FRAME_DATA_READY_MSG);
                    trackingHandler.sendEmptyMessage(FRAME_DATA_READY_MSG);
                }
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

    private void frameRenderProcess(int fps) {
        final String log;
        long endTime = System.currentTimeMillis();
        if (lastTime == 0 || endTime == lastTime) {
            lastTime = System.currentTimeMillis();
            log = "Fps: " + fps;
        } else {
            log = "Fps: " + 1000 / (endTime - lastTime);
            lastTime = endTime;
        }

        if (!overlapFaceView.getHolder().getSurface().isValid()) {
            return;
        }
        Canvas canvas = overlapFaceView.getHolder().lockCanvas();
        if (canvas == null)
            return;
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        canvas.setMatrix(transformMatrix);

        if (multiTrack106 != null) {
            Face face = multiTrack106.getTrackingInfo();
            if (face != null) {
                Rect rect = new Rect(previewHeight - face.left, face.top, previewHeight - face.right, face.bottom);
                PointF[] points = new PointF[106];
                for (int i = 0; i < 106; i++) {
                    points[i] = new PointF(face.landmarks[i * 2], face.landmarks[i * 2 + 1]);
                }

                float[] visibles = new float[106];
                for (int i = 0; i < points.length; i++) {
                    visibles[i] = 1.0f;
                    points[i].x = previewHeight - points[i].x;
                }

                STUtils.drawFaceRect(canvas, rect, previewHeight, previewWidth, true);
                STUtils.drawPoints(canvas, landmarkPaint, points, visibles, previewHeight, previewWidth, true);
            }
        }
        canvas.drawText(log, 10, 30, redPaint);
        overlapFaceView.getHolder().unlockCanvasAndPost(canvas);

//        if (faceLandmarkListener != null && tvFps != null) {
//            if (uiHandler != null) {
//                uiHandler.post(() -> {
//                    tvFps.setText(log);
//                });
//            }
//        }
    }
}
