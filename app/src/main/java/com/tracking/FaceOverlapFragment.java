package com.tracking;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.View;

import com.nhancv.facemask.tracking.ImageUtil;
import com.nhancv.facemask.util.STUtils;

import java.util.List;

import zeusees.tracking.Face;
import zeusees.tracking.FaceTracking;


public class FaceOverlapFragment extends CameraOverlapFragment {
    private static final String TAG = FaceOverlapFragment.class.getSimpleName();

    private static final int MESSAGE_DRAW_POINTS = 100;

    private FaceTracking mMultiTrack106 = null;
    private HandlerThread mHandlerThread;
    private Handler mHandler;

    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;

    private byte mNv21Data[];
    private byte[] mTmpBuffer;

    private int frameIndex = 0;

    private Paint mPaint;
    private Object lockObj = new Object();
    private boolean mIsPaused = false;

    @SuppressLint("NewApi")
    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mNv21Data = new byte[MAX_PREVIEW_WIDTH * MAX_PREVIEW_HEIGHT * 2];
        mTmpBuffer = new byte[MAX_PREVIEW_WIDTH * MAX_PREVIEW_HEIGHT * 2];
        frameIndex = 0;
        mPaint = new Paint();
        mPaint.setColor(Color.rgb(57, 138, 243));
        int strokeWidth = Math.max(MAX_PREVIEW_HEIGHT / 240, 2);
        mPaint.setStrokeWidth(strokeWidth);
        mPaint.setStyle(Style.FILL);
        mHandlerThread = new HandlerThread("DrawFacePointsThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DRAW_POINTS) {
                    synchronized (lockObj) {
                        if (!mIsPaused) {
                            handleDrawPoints();
                        }
                    }

                }
            }
        };
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) {
            return;
        }
        // Initialize the storage bitmaps once when the resolution is known.
        if (mPreviewWidth != image.getWidth() || mPreviewHeight != image.getHeight()) {
            mPreviewWidth = image.getWidth();
            mPreviewHeight = image.getHeight();
        }

        byte[] data = ImageUtil.YUV_420_888toNV212(image);
        synchronized (mNv21Data) {
            System.arraycopy(data, 0, mNv21Data, 0, data.length);
        }
        image.close();

        mHandler.removeMessages(MESSAGE_DRAW_POINTS);
        mHandler.sendEmptyMessage(MESSAGE_DRAW_POINTS);
    }

    private void handleDrawPoints() {

        synchronized (mNv21Data) {
            System.arraycopy(mNv21Data, 0, mTmpBuffer, 0, mNv21Data.length);
        }

        boolean frontCamera = true;


//        Log.d(TAG, "handleDrawPoints: " + mPreviewSize);
//        Log.d(TAG, "handleDrawPoints: " + mPreviewWidth + " " + mPreviewHeight);
        if (frameIndex == 0) {
            mMultiTrack106.FaceTrackingInit(mTmpBuffer, mPreviewHeight, mPreviewWidth);

        } else {
            mMultiTrack106.Update(mTmpBuffer, mPreviewHeight, mPreviewWidth);
        }
        frameIndex += 1;

        List<Face> faceActions = mMultiTrack106.getTrackingInfo();


        if (faceActions != null) {

            if (!mOverlap.getHolder().getSurface().isValid()) {
                return;
            }

            Canvas canvas = mOverlap.getHolder().lockCanvas();
            if (canvas == null)
                return;

            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            canvas.setMatrix(getMatrix());
            boolean rotate270 = true;
            for (Face r : faceActions) {

                Rect rect = new Rect(mPreviewHeight - r.left, r.top, mPreviewHeight - r.right, r.bottom);

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
                        mPreviewWidth, frontCamera);
                STUtils.drawPoints(canvas, mPaint, points, visibles, mPreviewHeight,
                        mPreviewWidth, frontCamera);

            }
            mOverlap.getHolder().unlockCanvasAndPost(canvas);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        this.mIsPaused = false;
        this.frameIndex = 0;
        if (mMultiTrack106 == null) {
            mMultiTrack106 = new FaceTracking("/sdcard/ZeuseesFaceTracking/models");
        }
    }

    @Override
    public void onPause() {
        mHandler.removeMessages(MESSAGE_DRAW_POINTS);
        mIsPaused = true;
        synchronized (lockObj) {
            if (mMultiTrack106 != null) {
                mMultiTrack106 = null;

            }
        }

        super.onPause();
    }

}
