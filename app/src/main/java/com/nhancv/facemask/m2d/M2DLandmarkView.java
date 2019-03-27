package com.nhancv.facemask.m2d;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.tzutalin.dlib.VisionDetRet;

import java.util.ArrayList;
import java.util.List;

public class M2DLandmarkView extends View {

    private List<VisionDetRet> visionDetRetList;
    private Paint mFaceLandmarkPaint;
    private Rect bounds;
    private int previewWidth;
    private int previewHeight;
    private int currentWidth;
    private int currentHeight;

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

    public void setVisionDetRetList(List<VisionDetRet> visionDetRetList, int previewWidth, int previewHeight) {
        this.visionDetRetList = visionDetRetList;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        currentWidth = MeasureSpec.getSize(widthMeasureSpec);
        currentHeight = MeasureSpec.getSize(heightMeasureSpec);
        this.setMeasuredDimension(currentWidth, currentHeight);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (visionDetRetList == null) return;
        for (final VisionDetRet ret : visionDetRetList) {
            float resizeRatio = currentWidth * 1f /150;
            bounds.left = (int) (ret.getLeft() * resizeRatio);
            bounds.top = (int) (ret.getTop() * resizeRatio);
            bounds.right = (int) (ret.getRight() * resizeRatio);
            bounds.bottom = (int) (ret.getBottom() * resizeRatio);
            canvas.drawRect(bounds, mFaceLandmarkPaint);

            // Draw landmark
            ArrayList<Point> landmarks = ret.getFaceLandmarks();
            for (Point point : landmarks) {
                int pointX = (int) (point.x * resizeRatio);
                int pointY = (int) (point.y * resizeRatio);
                canvas.drawCircle(pointX, pointY, 2, mFaceLandmarkPaint);
            }
        }
    }
}
