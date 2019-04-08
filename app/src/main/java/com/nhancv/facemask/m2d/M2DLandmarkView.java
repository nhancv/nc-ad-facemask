package com.nhancv.facemask.m2d;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.nhancv.facemask.m2d.mask.DistanceHelper;
import com.nhancv.facemask.m2d.mask.Eye5;
import com.nhancv.facemask.m2d.mask.Head5;
import com.nhancv.facemask.m2d.mask.IMaskStrategy;
import com.nhancv.facemask.m2d.mask.Mask;
import com.nhancv.facemask.m2d.mask.MaskController;
import com.nhancv.facemask.m2d.mask.Nose5;
import com.tzutalin.dlib.VisionDetRet;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hugo.weaving.DebugLog;



public class M2DLandmarkView extends View {

    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    private float offsetX = 0;
    private float offsetY = 0;
    private List<VisionDetRet> visionDetRetList;
    //private Paint mFaceLandmarkPaint;
    private Rect bounds;
    private int bmWidth;
    private int bmHeight;
    private int currentWidth;
    private int currentHeight;
    private HashMap<String,Bitmap> overlayElements;
    private DistanceHelper distanceHelper = new DistanceHelper();
    private Bitmap curOverlayResized;

    private Mask curMask;
    private MaskController maskController;
    private List<Point> oldLandMarks = new ArrayList<>(); //landmarks that have been normalized
    float radius = 30; //radius distance difference of new and old points

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


        bounds = new Rect();
    }

    public void setVisionDetRetList(List<VisionDetRet> visionDetRetList, int bmWidth, int bmHeight) {
        this.visionDetRetList = visionDetRetList;
        this.bmWidth = bmWidth;
        this.bmHeight = bmHeight;
    }
    //update overlay image with id - each bitmap
    public void updateOverlayImage(HashMap<String,Bitmap> elements){
        Log.d("M2DLandmarkView","img load");
        this.overlayElements = elements;
        maskController = new MaskController();
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
        return (left+right)/2f;
    }
    private float faceCenterY(int top, int bottom)
    {
        return (top+bottom)/2f;
    }

    private boolean isPointValid(Point point1, Point point2){
        float distance = distanceHelper.distance(point1,point2);
        if(distance<radius)
            return true;
        return false;
    }

    private List<Point> normalizePoint(List<Point> landmarks){
        List<Point> normLandmarks = new ArrayList<>();
        //the first frame detect with a empty oldLandMarks
        if(oldLandMarks.isEmpty()) {
            for (Point point : landmarks) {
                int pointX = (int) getX(point.x);
                int pointY = (int) getY(point.y);
                normLandmarks.add(new Point(pointX,pointY)); //norm Value
            }
        }
       else{
            int i = 0;
            for (Point point : landmarks) {
                int pointX = (int) getX(point.x);
                int pointY = (int) getY(point.y);
                Point newPoint = new Point(pointX,pointY);
                //If the new point is not noise
                if(!isPointValid(newPoint,oldLandMarks.get(i))){
                    normLandmarks.add(newPoint);
                }
                //if the new point is noise
                else{
                    normLandmarks.add(oldLandMarks.get(i));
                }
                i+=1;
            }
        }
        oldLandMarks = normLandmarks;
        return normLandmarks;
    }

    @Override
    protected void onDraw(Canvas canvas) {

        if (visionDetRetList == null) return;
        for (final VisionDetRet ret : visionDetRetList) {
            List<Point> landmarks = ret.getFaceLandmarks();
            List<Point> normLandmark = this.normalizePoint(landmarks);

            bounds.left = (int) (getX(ret.getLeft()));
            bounds.top = (int) (getY(ret.getTop()));
            bounds.right = (int) getX(ret.getRight());
            bounds.bottom = (int) getY(ret.getBottom());
            float centerX = faceCenterX(bounds.left, bounds.right);
            float centerY = faceCenterY(bounds.top,bounds.bottom);
            float faceW = bounds.right - bounds.left;
            float faceH = bounds.bottom - bounds.top;

            if(overlayElements!=null)
            {
                for (Map.Entry<String,Bitmap> entry: overlayElements.entrySet()){
                    String elements= entry.getKey();
                    switch (elements){
                        case "head":
                            maskController.setMaskStrategy(new Head5());
                            break;
                        case "nose":
                            maskController.setMaskStrategy(new Nose5());
                            break;
                        case "eye":
                            maskController.setMaskStrategy(new Eye5());
                    }
                    curMask = maskController.defineMask(normLandmark,faceW,faceH,entry.getValue());
                    PointF position = curMask.getPositionOnFace();
                    Log.d("M2DLandmarkView",""+position);
                    curOverlayResized = curMask.getBmMask();
                    canvas.drawBitmap(curOverlayResized, position.x, position.y, null);
                }
            }
            //canvas.drawLine(f);
        }
    }
}
