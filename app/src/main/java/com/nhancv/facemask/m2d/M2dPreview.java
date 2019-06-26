package com.nhancv.facemask.m2d;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.View;

import com.nhancv.facemask.m2d.mask.MaskUpdater;

import zeusees.tracking.Face;


public class M2dPreview extends View {

    private static final String TAG = M2dPreview.class.getSimpleName();

    private int ratioWidth = 0;
    private int ratioHeight = 0;
    private float offsetX = 0;
    private float offsetY = 0;

    private int previewWidth;
    private int previewHeight;
    private int currentWidth;
    private int currentHeight;

    private MaskUpdater maskUpdater;
    private Bitmap previewBm;

    public M2dPreview(Context context) {
        this(context, null, 0, 0);
    }

    public M2dPreview(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public M2dPreview(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public M2dPreview(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        maskUpdater = new MaskUpdater(context);
    }

    public void maskUpdateLocation(Bitmap previewBm, Face face, int previewWidth, int previewHeight, Matrix scaleMatrix) {
        this.previewBm = previewBm;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;

        this.maskUpdater.maskUpdateLocation(face, previewWidth, previewHeight, scaleMatrix);
        postInvalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        this.maskUpdater.onStop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        currentWidth = MeasureSpec.getSize(widthMeasureSpec);
        currentHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == ratioWidth || 0 == ratioHeight) {
            setMeasuredDimension(currentWidth, currentHeight);
        } else {
            if (currentWidth < currentHeight * ratioWidth / ratioHeight) {
                setMeasuredDimension(currentWidth, currentWidth * ratioHeight / ratioWidth);
            } else {
                setMeasuredDimension(currentHeight * ratioWidth / ratioHeight, currentHeight);
            }
        }

        offsetX = (currentWidth * 0.5f - ratioWidth * 0.5f);
        offsetY = (currentHeight * 0.5f - ratioHeight * 0.5f);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    // Fix to Camera preview rate
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        ratioWidth = width;
        ratioHeight = height;

        if (ratioWidth * 1f / ratioHeight == currentWidth * 1f / currentHeight) {
            ratioWidth = currentWidth;
            ratioHeight = currentHeight;
        }

        requestLayout();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (previewBm != null && !previewBm.isRecycled()) {
            canvas.drawBitmap(previewBm, 0, 0, null);
        }
        // Draw 2dMask
        this.maskUpdater.onDraw(canvas);
    }

    private float getX(float x) {
        return x / previewWidth * ratioWidth + offsetX;
    }

    private float getY(float y) {
        return y / previewHeight * ratioHeight + offsetY;
    }


}
