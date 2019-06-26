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
import com.nhancv.facemask.util.Constant;

import org.wysaid.nativePort.CGEDeformFilterWrapper;
import org.wysaid.nativePort.CGENativeLibrary;
import org.wysaid.view.ImageGLSurfaceView;

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
    private SurfaceView landmarkPointsView;
    private ImageGLSurfaceView openGlPreview;
    private CGEDeformFilterWrapper mDeformWrapper;
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

    public void initialize(
            final Context context,
            final Matrix transformMatrix,
            final ImageGLSurfaceView openGlPreview,
            final CGEDeformFilterWrapper mDeformWrapper,
            final SurfaceView landmarkPointsView) {
        this.context = context;
        this.transformMatrix = transformMatrix;
        this.openGlPreview = openGlPreview;
        this.mDeformWrapper = mDeformWrapper;
        this.landmarkPointsView = landmarkPointsView;

        previewFps = new StableFps(30);

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
                    previewRenderProcess();
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

        if (!preImageProcess(reader)) return;
        // Fps for render to ui thread
        if (!previewFps.isStarted()) {
            previewFps.start(fps -> {
                if (previewRenderHandler != null) {
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

    private void previewRenderProcess() {
        if (multiTrack106 != null) {
            System.arraycopy(nv21Data, 0, previewRenderBuffer, 0, nv21Data.length);
            Face face = multiTrack106.getTrackingInfo();
            Bitmap previewBmTmp = STUtils.NV21ToRGBABitmap(previewRenderBuffer, previewWidth, previewHeight, context);
            Matrix matrix = new Matrix(transformMatrix);
            matrix.postRotate(-90);
            matrix.postTranslate(0, previewBmTmp.getWidth());
            matrix.postScale(-1, 1);
            matrix.postTranslate(previewBmTmp.getHeight(), 0);
            Bitmap previewBm = Bitmap.createBitmap(previewBmTmp, 0, 0, previewBmTmp.getWidth(), previewBmTmp.getHeight(), matrix, false);
            // TODO: 2019-06-19 Filter with OpenGLES
            Bitmap bmFiltered = CGENativeLibrary.filterImage_MultipleEffects(previewBm, Constant.EFFECT_ACTIVE, 1.0f);
            // Show preview
//            m2dPreview.maskUpdateLocation(bmFiltered, face, previewWidth, previewHeight, transformMatrix);

            // Initialize a new Canvas instance
            Canvas openGLCanvas = new Canvas(bmFiltered);
//            m2dPreview.getMask().draw(openGLCanvas);

            openGlPreview.setImageBitmap(bmFiltered);
            openGlPreview.flush(true, () -> {
                if (mDeformWrapper == null) return;
                mDeformWrapper.bloatDeform(0, 0, previewBmTmp.getWidth(), previewBmTmp.getHeight(), 200, 0.5f);
            });

            // Show landmark for debug
            showDebugLandmarkPoints(face);
        }

    }

    private void showDebugLandmarkPoints(Face face) {
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
            if (!landmarkPointsView.getHolder().getSurface().isValid()) {
                return;
            }
            Canvas canvas = landmarkPointsView.getHolder().lockCanvas();
            if (canvas != null) {
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
                landmarkPointsView.getHolder().unlockCanvasAndPost(canvas);
            }
        }
    }

}
