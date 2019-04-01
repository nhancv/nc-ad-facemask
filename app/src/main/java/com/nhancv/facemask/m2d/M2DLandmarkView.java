package com.nhancv.facemask.m2d;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.tzutalin.dlib.VisionDetRet;

import java.util.List;

import hugo.weaving.DebugLog;

public class M2DLandmarkView extends View {

    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    private float offsetX = 0;
    private float offsetY = 0;
    private List<VisionDetRet> visionDetRetList;
    private Paint mFaceLandmarkPaint;
    private Rect bounds;
    private int bmWidth;
    private int bmHeight;
    private int currentWidth;
    private int currentHeight;
    private Bitmap overlayImg = null;

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

        mFaceLandmarkPaint = new Paint();
        mFaceLandmarkPaint.setColor(Color.GREEN);
        mFaceLandmarkPaint.setStrokeWidth(2);
        mFaceLandmarkPaint.setStyle(Paint.Style.STROKE);

        bounds = new Rect();
    }

    public void setVisionDetRetList(List<VisionDetRet> visionDetRetList, int bmWidth, int bmHeight) {
        this.visionDetRetList = visionDetRetList;
        this.bmWidth = bmWidth;
        this.bmHeight = bmHeight;
    }
    public void updateOverlayImage(Bitmap src){
        Log.d("M2DLandmarkView","img load");
        this.overlayImg = src;
    }
    @SuppressLint("LongLogTag")
    @DebugLog
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        currentWidth = MeasureSpec.getSize(widthMeasureSpec);
        currentHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(currentWidth, currentHeight);
        } else {
            if (currentWidth < currentHeight * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(currentWidth, currentWidth * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(currentHeight * mRatioWidth / mRatioHeight, currentHeight);
            }
        }

        offsetX = (currentWidth * 0.5f - mRatioWidth * 0.5f);
        offsetY = (currentHeight * 0.5f - mRatioHeight * 0.5f);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @SuppressLint("LongLogTag")
    @DebugLog
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;

        if (mRatioWidth * 1f / mRatioHeight == currentWidth * 1f / currentHeight) {
            mRatioWidth = currentWidth;
            mRatioHeight = currentHeight;
        }

        requestLayout();
    }

    private float getX(float x) {
        return x / bmWidth * mRatioWidth + offsetX;
    }

    private float getY(float y) {
        return y / bmHeight * mRatioHeight + offsetY;
    }
    private float faceCenterX(int left,int right)
    {
        return (left+right)/2;
    }
    private float faceCenterY(int top, int bottom)
    {
        return (top+bottom)/2;
    }
    @Override
    protected void onDraw(Canvas canvas) {
        if (visionDetRetList == null) return;
        for (final VisionDetRet ret : visionDetRetList) {
            bounds.left = (int) (getX(ret.getLeft()));
            bounds.top = (int) (getY(ret.getTop()));
            bounds.right = (int) getX(ret.getRight());
            bounds.bottom = (int) getY(ret.getBottom());
            //canvas.drawRect(bounds, mFaceLandmarkPaint);
            float centerX = faceCenterX(bounds.left, bounds.right);
            float centerY = faceCenterY(bounds.top,bounds.bottom);
            float faceW = bounds.right - bounds.left;
            float faceH = bounds.bottom - bounds.top;
            if(overlayImg != null){
                resizeMask(faceW,faceH);
                PointF position = maskPosition(centerX,centerY);
                canvas.drawBitmap(overlayImg,position.x,position.y,null);
            }
            // Draw landmark
           /* ArrayList<Point> landmarks = ret.getFaceLandmarks();
            for (Point point : landmarks) {
                int pointX = (int) getX(point.x);
                int pointY = (int) getY(point.y);
                canvas.drawCircle(pointX, pointY, 2, mFaceLandmarkPaint);
            }*/
        }
    }

    private PointF maskPosition(float centerX,float centerY){
        PointF position = new PointF();
        position.x = centerX - overlayImg.getWidth()/2;
        position.y = centerY - overlayImg.getHeight()/2;
        return position;

    }
    private void resizeMask(float faceWidth,float faceHeight){
        this.overlayImg = resizeBitmap(this.overlayImg,faceWidth,faceHeight);
    }
    private Bitmap resizeBitmap(Bitmap bmp,float newWidth,float newHeight){
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        float scaleWidth = (1.5f*newWidth) / width ;
        float scaleHeight = (1.5f* newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bmp, 0, 0, width, height, matrix, false);
        bmp.recycle();
        return resizedBitmap;
    }
}
