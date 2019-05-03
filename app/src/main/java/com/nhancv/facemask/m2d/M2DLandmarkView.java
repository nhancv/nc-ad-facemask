package com.nhancv.facemask.m2d;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.nhancv.facemask.fps.StableFps;
import com.nhancv.facemask.m2d.mask.CatMask;
import com.nhancv.facemask.m2d.mask.DogMask;
import com.nhancv.facemask.util.SolvePNP;

import zeusees.tracking.Face;


public class M2DLandmarkView extends View {

    private static final String TAG = M2DLandmarkView.class.getSimpleName();

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

    private CatMask catMask = new CatMask();
    private DogMask dogMask = new DogMask();

    public M2DLandmarkView(Context context) {
        this(context, null, 0, 0);
    }

    public M2DLandmarkView(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public M2DLandmarkView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public M2DLandmarkView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        faceLandmarkPaint = new Paint();
        faceLandmarkPaint.setColor(Color.GREEN);
        faceLandmarkPaint.setStrokeWidth(1);
        faceLandmarkPaint.setStyle(Paint.Style.FILL);

        //start thread
        stableFps = new StableFps(20);

        catMask.init(getContext());
        dogMask.init(getContext());
    }

    public void initPNP() {
        solvePNP.initialize();
    }

    public void releasePNP() {
        solvePNP.releaseMat();
    }

    @Override
    protected void onDetachedFromWindow() {
        stableFps.stop();
        solvePNP.releaseMat();
        catMask.release();
        dogMask.release();
        super.onDetachedFromWindow();
    }

    public void setVisionDetRetList(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix) {
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        this.scaleMatrix = scaleMatrix;
        solvePNP.initialize();
//        catMask.update(face, previewWidth, previewHeight, scaleMatrix, solvePNP);
        dogMask.update(face, previewWidth, previewHeight, scaleMatrix, solvePNP);
        postInvalidate();
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
        canvas.setMatrix(scaleMatrix);

        // Draw 2dMask
//        catMask.draw(canvas);
        dogMask.draw(canvas);

    }


    private float getX(float x) {
        return x / previewWidth * ratioWidth + offsetX;
    }

    private float getY(float y) {
        return y / previewHeight * ratioHeight + offsetY;
    }


}
