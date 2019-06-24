package com.nhancv.facemask.m2d;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.nhancv.facemask.fps.StableFps;
import com.nhancv.facemask.m2d.mask.Mask;
import com.nhancv.facemask.m2d.mask.imp.CatMask;
import com.nhancv.facemask.pose.SolvePNP;

import zeusees.tracking.Face;


public class M2dPreview extends View {

    private static final String TAG = M2dPreview.class.getSimpleName();

    private int ratioWidth = 0;
    private int ratioHeight = 0;
    private float offsetX = 0;
    private float offsetY = 0;
    private Paint faceLandmarkPaint;

    private int previewWidth;
    private int previewHeight;
    private int currentWidth;
    private int currentHeight;
    private Matrix scaleMatrix;
    private StableFps stableFps;
    private SolvePNP solvePNP = new SolvePNP();
    private Mask mask;
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

        faceLandmarkPaint = new Paint();
        faceLandmarkPaint.setColor(Color.GREEN);
        faceLandmarkPaint.setStrokeWidth(1);
        faceLandmarkPaint.setStyle(Paint.Style.FILL);

        //start thread
        stableFps = new StableFps(20);

        //init mask
        mask = new CatMask();
        mask.init(getContext());
    }

    public void initPNP() {
        solvePNP.initialize();
    }

    public void releasePNP() {
        solvePNP.releaseMat();
    }

    public void setVisionDetRetList(Bitmap previewBm, Face face, int previewWidth, int previewHeight, Matrix scaleMatrix) {
        this.previewBm = previewBm;

        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        this.scaleMatrix = scaleMatrix;
        try {
            solvePNP.initialize();
            if (mask != null) mask.update(face, previewWidth, previewHeight, scaleMatrix, solvePNP);
            postInvalidate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stableFps.stop();
        solvePNP.releaseMat();
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
        if (mask != null) mask.draw(canvas);

    }


    private float getX(float x) {
        return x / previewWidth * ratioWidth + offsetX;
    }

    private float getY(float y) {
        return y / previewHeight * ratioHeight + offsetY;
    }


}
