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
import android.view.SurfaceView;

import com.nhancv.facemask.fps.StableFps;
import com.nhancv.facemask.util.STUtils;

import java.util.Locale;

import zeusees.tracking.Face;
import zeusees.tracking.FaceTracking;

public class FaceLandmarkTracking implements OnImageAvailableListener {

    /**
     * Static
     */
    private static final String TAG = FaceLandmarkTracking.class.getSimpleName();
    private static final int FRAME_DATA_READY_MSG = 0x01;
    private static final int RENDER_OVERLAP_MSG = 0x02;
    private static final int RENDER_PREVIEW_MSG = 0x03;
    private static final boolean SHOW_LANDMARK = false;
    /**
     * Thread
     */
    private HandlerThread trackingThread;
    private Handler trackingHandler;

    private HandlerThread uiRenderThread;
    private Handler uiRenderHandler;

    private HandlerThread previewRenderThread;
    private Handler previewRenderHandler;

    /**
     * Inject
     */
    private Matrix transformMatrix;
    private SurfaceView surfacePreview;
    private SurfaceView overlapFaceView;
    private FaceLandmarkListener faceLandmarkListener;

    /**
     * Global vars
     */
    private int previewWidth; //320
    private int previewHeight; //240
    private Paint redPaint;
    private StableFps renderFps;
    private StableFps overlayFps;
    private long lastTime;


    private Paint landmarkPaint;
    private byte[] nv21Data;
    private byte[] trackingFrameBuffer;
    private byte[] previewRenderBuffer;
    private FaceTracking multiTrack106;
    private boolean initTrack106;
    private Context context;
    private PointState pointState;

    public void initialize(
            final Context context,
            final Matrix transformMatrix,
            final SurfaceView overlapFaceView,
            final SurfaceView surfacePreview,
            final FaceLandmarkListener faceLandmarkListener) {
        this.context = context;
        this.transformMatrix = transformMatrix;
        this.overlapFaceView = overlapFaceView;
        this.surfacePreview = surfacePreview;
        this.faceLandmarkListener = faceLandmarkListener;

        pointState = new PointState();

        renderFps = new StableFps(20);
        overlayFps = new StableFps(25);

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
                    Environment.getExternalStorageDirectory().getPath() +
                            "/ZeuseesFaceTracking/models");
        }

        // Init threads
        uiRenderThread = new HandlerThread("UIRenderThread");
        uiRenderThread.start();
        uiRenderHandler = new Handler(uiRenderThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == RENDER_OVERLAP_MSG) {
                    frameOverlapRenderProcess();
                }
            }
        };

        previewRenderThread = new HandlerThread("PreviewRenderThread");
        previewRenderThread.start();
        previewRenderHandler = new Handler(previewRenderThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == RENDER_PREVIEW_MSG) {
                    framePreviewRenderProcess();
                }
            }
        };

        trackingThread = new HandlerThread("TrackingThread");
        trackingThread.start();
        trackingHandler = new Handler(trackingThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == FRAME_DATA_READY_MSG && multiTrack106 != null) {
                    System.arraycopy(nv21Data, 0, trackingFrameBuffer, 0, nv21Data.length);
                    if (!initTrack106) {
                        multiTrack106.faceTrackingInit(trackingFrameBuffer, previewHeight, previewWidth);
                        initTrack106 = true;
                    } else {
                        multiTrack106.faceTrackingUpdate(trackingFrameBuffer, previewHeight, previewWidth);
                    }
                }
            }
        };

    }

    private void framePreviewRenderProcess() {
        System.arraycopy(nv21Data, 0, previewRenderBuffer, 0, nv21Data.length);

        if (surfacePreview != null && surfacePreview.getHolder().getSurface().isValid()) {
            // Draw bm preview
            Bitmap bm = STUtils.NV21ToRGBABitmap(previewRenderBuffer, previewWidth, previewHeight, context);
            Matrix matrix = new Matrix();
            matrix.postRotate(-90);
            matrix.postTranslate(0, bm.getWidth());
            matrix.postScale(-1, 1);
            matrix.postTranslate(bm.getHeight(), 0);

            Canvas canvas = surfacePreview.getHolder().lockCanvas();
            if (canvas != null) {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR);
                canvas.setMatrix(transformMatrix);
                canvas.drawBitmap(bm, matrix, null);
                surfacePreview.getHolder().unlockCanvasAndPost(canvas);
            }
        }
    }

    public void deInitialize() {
        if (renderFps != null) {
            renderFps.stop();
        }

        if (overlayFps != null) {
            overlayFps.stop();
        }

        initTrack106 = false;
        if (multiTrack106 != null) {
            multiTrack106 = null;
        }

        try {
            if (uiRenderThread != null) {
                uiRenderThread.quitSafely();
                uiRenderThread.join();
            }
            uiRenderThread = null;
            uiRenderHandler = null;

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

        if (!preImageProcess(reader)) return;
        // Fps for render to ui thread
        if (!renderFps.isStarted()) {
            renderFps.start(fps -> {
                if (previewRenderHandler != null) {
                    previewRenderHandler.removeMessages(RENDER_PREVIEW_MSG);
                    previewRenderHandler.sendEmptyMessage(RENDER_PREVIEW_MSG);
                }
            });
        }
        if (!overlayFps.isStarted()) {
            overlayFps.start(fps -> {
                if (uiRenderHandler != null) {
                    uiRenderHandler.removeMessages(RENDER_OVERLAP_MSG);
                    uiRenderHandler.sendEmptyMessage(RENDER_OVERLAP_MSG);
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

    private void frameOverlapRenderProcess() {
        if (multiTrack106 != null) {
            Face face = multiTrack106.getTrackingInfo();
            if (face == null) {
                pointState.stabilize(false, null);
            } else {
                float[] landMarkPoints = new float[face.landmarks.length];
                for (int i = 0; i < face.landmarks.length; i++) {
                    landMarkPoints[i] = face.landmarks[i];
                }
                pointState.stabilize(true, landMarkPoints);
            }


            if (faceLandmarkListener != null) {
                faceLandmarkListener.landmarkUpdate(face, previewWidth, previewHeight, transformMatrix);
            }

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

                // Draw landmarks to SurfaceView
                if (!overlapFaceView.getHolder().getSurface().isValid()) {
                    return;
                }
                Canvas canvas = overlapFaceView.getHolder().lockCanvas();
                if (canvas == null)
                    return;
                canvas.drawColor(0, PorterDuff.Mode.CLEAR);
                canvas.setMatrix(transformMatrix);

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

                    STUtils.drawFaceRect(canvas, rect, previewHeight, previewWidth, true);
                    STUtils.drawPoints(canvas, landmarkPaint, point2Ds, visibles, previewHeight, previewWidth, true);
                }
                canvas.drawText(log, 10, 30, redPaint);
                overlapFaceView.getHolder().unlockCanvasAndPost(canvas);
            }
        }

    }

}
