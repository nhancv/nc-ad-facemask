package com.nhancv.facemask.tracking;

import android.content.Context;
import android.graphics.Bitmap;
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
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.nhancv.facemask.fps.NSemaphore;
import com.nhancv.facemask.fps.StableFps;
import com.nhancv.facemask.m2d.SurfacePreview;

import java.util.Locale;

import zeusees.tracking.Face;
import zeusees.tracking.FaceTracking;
import zeusees.tracking.STUtils;

public class FaceLandmarkTracking implements OnImageAvailableListener {

    /**
     * Static
     */
    private static final String TAG = FaceLandmarkTracking.class.getSimpleName();
    private static final int FRAME_DATA_READY_MSG = 0x01;
    private static final int PREVIEW_RENDER_MSG = 0x02;
    private static final boolean SHOW_LANDMARK = false;
    /**
     * Thread
     */
    private HandlerThread trackingThread;
    private Handler trackingHandler;

    private HandlerThread previewRenderThread;
    private Handler previewRenderHandler;

    /**
     * Inject
     */
    private Matrix transformMatrix;
    private SurfacePreview surfacePreview;
    /**
     * Global vars
     */
    private int previewWidth; //320
    private int previewHeight; //240
    private Paint redPaint;
    private StableFps previewFps;
    private long lastTime;

    private Paint landmarkPaint;
    private byte[] nv21Data;
    private byte[] trackingFrameBuffer;
    private byte[] previewRenderBuffer;
    private FaceTracking multiTrack106;
    private boolean initTrack106;
    private Context context;

    private volatile Bitmap previewBm;
    private volatile NSemaphore trackingSemaphore = new NSemaphore();
    private volatile NSemaphore previewSemaphore = new NSemaphore();

    public void initialize(
            final Context context,
            final Matrix transformMatrix,
            final SurfacePreview surfacePreview) {
        this.context = context;
        this.transformMatrix = transformMatrix;
        this.surfacePreview = surfacePreview;

        previewFps = new StableFps(10);

        redPaint = new Paint();
        redPaint.setColor(Color.RED);
        redPaint.setStrokeWidth(1);
        redPaint.setStyle(Paint.Style.STROKE);

        landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.BLUE);
        landmarkPaint.setStrokeWidth(1);
        landmarkPaint.setStyle(Paint.Style.FILL);

        if (multiTrack106 == null) {
            multiTrack106 = new FaceTracking(
                    Environment.getExternalStorageDirectory().getPath() + "/ZeuseesFaceTracking/models");
        }

        // Init threads
        previewRenderThread = new HandlerThread("PreviewRenderThread");
        previewRenderThread.start();
        previewRenderHandler = new Handler(previewRenderThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == PREVIEW_RENDER_MSG) {
                    long start = System.currentTimeMillis();
                    previewRenderProcess();
                    Log.e(TAG, Thread.currentThread().getId() + " previewRenderHandler: " + (System.currentTimeMillis() - start) + "ms");
                }
            }
        };

        trackingThread = new HandlerThread("TrackingThread");
        trackingThread.start();
        trackingHandler = new Handler(trackingThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == FRAME_DATA_READY_MSG && multiTrack106 != null) {
                    trackingSemaphore.acquire();
                    long start = System.currentTimeMillis();
                    System.arraycopy(nv21Data, 0, trackingFrameBuffer, 0, nv21Data.length);
                    if (!initTrack106) {
                        multiTrack106.faceTrackingInit(trackingFrameBuffer, previewHeight, previewWidth);
                        initTrack106 = true;
                    } else {
                        multiTrack106.faceTrackingUpdate(trackingFrameBuffer, previewHeight, previewWidth);
                    }
                    trackingSemaphore.release();
                    Log.e(TAG, Thread.currentThread().getId() + " trackingHandler: " + (System.currentTimeMillis() - start) + "ms");
                }
            }
        };

    }

    public void deInitialize() {
        if (previewFps != null) {
            previewFps.stop();
        }

        initTrack106 = false;
        if (multiTrack106 != null) {
            multiTrack106 = null;
        }

        try {
            if (previewRenderThread != null) {
                previewRenderThread.quitSafely();
                previewRenderThread.join();
            }
            previewRenderThread = null;
            previewRenderHandler = null;

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
        if (!trackingSemaphore.available()) return;
        long start = System.currentTimeMillis();
        if (!preImageProcess(reader)) {
            return;
        }
        Log.e(TAG, Thread.currentThread().getId() + " onImageAvailable: " + (System.currentTimeMillis() - start) + "ms");
        // Fps for render to ui thread
        if (!previewFps.isStarted()) {
            previewFps.start(fps -> {
                if (previewRenderHandler != null && previewSemaphore.available()) {
                    previewRenderHandler.removeMessages(PREVIEW_RENDER_MSG);
                    previewRenderHandler.sendEmptyMessage(PREVIEW_RENDER_MSG);
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
                trackingFrameBuffer = new byte[previewWidth * previewHeight * 2];
                previewRenderBuffer = new byte[previewWidth * previewHeight * 2];
            }

            byte[] data = STUtils.convertYUV420ToNV21(image);
            System.arraycopy(data, 0, nv21Data, 0, data.length);

            image.close();

            if (trackingHandler != null) {
                trackingHandler.removeMessages(FRAME_DATA_READY_MSG);
                trackingHandler.sendEmptyMessage(FRAME_DATA_READY_MSG);
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

    private synchronized void previewRenderProcess() {
        previewSemaphore.acquire();
        if (multiTrack106 != null) {
            System.arraycopy(nv21Data, 0, previewRenderBuffer, 0, nv21Data.length);
            Face face = multiTrack106.getTrackingInfo();
            Bitmap previewBmTmp = STUtils.NV21ToRGBABitmap(previewRenderBuffer, previewWidth, previewHeight, context);
            Matrix matrix = new Matrix(transformMatrix);
            matrix.postRotate(-90);
            matrix.postTranslate(0, previewBmTmp.getWidth());
            matrix.postScale(-1, 1);
            matrix.postTranslate(previewBmTmp.getHeight(), 0);
//            if (previewBm != null && !previewBm.isRecycled()) {
//                previewBm.recycle();
//                previewBm = null;
//            }
            previewBm = Bitmap.createBitmap(previewBmTmp, 0, 0, previewBmTmp.getWidth(), previewBmTmp.getHeight(), matrix, false);

            // TODO: 2019-06-19 Filter with OpenGLES
//            Bitmap bmFiltered = CGENativeLibrary.filterImage_MultipleEffects(previewBm, Constant.EFFECT_ACTIVE, 1.0f);
            // Show preview
            surfacePreview.maskUpdateLocation(face, previewWidth, previewHeight, transformMatrix);
            // Initialize a new Canvas instance
            Canvas openGLCanvas = new Canvas(previewBm);
            surfacePreview.renderMaskToCanvas(openGLCanvas);

            // Show landmark for debug
            // Draw landmarks to SurfaceView
            if (SHOW_LANDMARK) {
                final String log;
                long endTime = System.currentTimeMillis();
                if (lastTime == 0 || endTime == lastTime) {
                    lastTime = System.currentTimeMillis();
                    log = "--";
                } else {
                    log = String.format(Locale.getDefault(), "Fps: %d", 1000 / (endTime - lastTime));
                    lastTime = endTime;
                }

                openGLCanvas.setMatrix(transformMatrix);
                if (face != null) {
                    Rect rect = new Rect(previewHeight - face.left, face.top, previewHeight - face.right, face.bottom);
                    PointF[] point2Ds = new PointF[106];
                    for (int i = 0; i < 106; i++) {
                        point2Ds[i] = new PointF(face.landmarks[i * 2], face.landmarks[i * 2 + 1]);
                    }
                    float[] visibles = new float[106];
                    for (int i = 0; i < point2Ds.length; i++) {
                        visibles[i] = 1.0f;
                        point2Ds[i].x = previewHeight - point2Ds[i].x;
                    }
                    STUtils.drawFaceRect(openGLCanvas, rect, previewHeight, previewWidth, true);
                    STUtils.drawPoints(openGLCanvas, landmarkPaint, point2Ds, visibles, previewHeight, previewWidth, true);
                }
                openGLCanvas.drawText(log, 10, 30, redPaint);
            }
            if (!surfacePreview.getHolder().getSurface().isValid()) {
                return;
            }
            Canvas canvas = surfacePreview.getHolder().lockCanvas();
            if (canvas == null)
                return;

            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            canvas.drawBitmap(previewBm, 0, 0, null);

            surfacePreview.getHolder().unlockCanvasAndPost(canvas);
        }
        previewSemaphore.release();
    }
}
