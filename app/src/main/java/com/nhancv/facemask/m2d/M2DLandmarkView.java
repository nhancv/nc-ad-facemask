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

import com.tzutalin.dlib.VisionDetRet;

//import org.opencv.core.CvType;
//import org.opencv.core.Mat;
//import org.opencv.core.MatOfInt;
//import org.opencv.core.MatOfPoint;
//import org.opencv.core.Scalar;
//import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
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
    private List<Bitmap> overlayImages = null;
    private int curOverlayImageIdx;
    //private BitmapConversion bitmapConversion = new BitmapConversion();
    //private Mat curFace; //img1
    private Bitmap curFaceImg;
    private Bitmap curOverayResized;
    //private Mat overlayMat; //img2
    //private Mat curFaceWarped;

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
    public void updateOverlayImage(List<Bitmap> src){
        Log.d("M2DLandmarkView","img load");
        this.overlayImages = src;
        this.curOverlayImageIdx = 0;//start again
        //this.overlayMat = bitmapConversion.convertBitmap2Mat(this.overlayImg);
        //this.curFaceWarped = this.overlayMat.clone(); //image
        //this.curFaceWarped.convertTo(this.curFaceWarped, CvType.CV_32F);//convert the curFaceWarp to


    }
    @SuppressLint("LongLogTag")
    @DebugLog
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
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


        offsetX = (currentWidth*0.5f -  mRatioWidth * 0.5f);
        offsetY = (currentHeight*0.5f -  mRatioHeight * 0.5f);
    }

    @SuppressLint("LongLogTag")
    @DebugLog
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    private float getX(float x) {
        return x/bmWidth * mRatioWidth + offsetX;
    }

    private float getY(float y) {
        return y/bmHeight * mRatioHeight + offsetY;
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
            //curFace = bitmapConversion.convertBitmap2Mat(this.curFaceImg);//convert bitmap face to matrix
            List<Point> landmarks = ret.getFaceLandmarks();
     /*       MatOfPoint points1 = new MatOfPoint();
            points1.fromList(landmarks);
            MatOfInt hullIndex = new MatOfInt();
            points1.fromList();
            Imgproc.convexHull(points1, false);*/
            //
            bounds.left = (int) (getX(ret.getLeft()));
            bounds.top = (int) (getY(ret.getTop()));
            bounds.right = (int) getX(ret.getRight());
            bounds.bottom = (int) getY(ret.getBottom());
            //canvas.drawRect(bounds, mFaceLandmarkPaint);
            float centerX = faceCenterX(bounds.left, bounds.right);
            float centerY = faceCenterY(bounds.top,bounds.bottom);
            float faceW = bounds.right - bounds.left;
            float faceH = bounds.bottom - bounds.top;
            if(overlayImages.get(curOverlayImageIdx) != null){
                curOverayResized = resizeMask(overlayImages.get(curOverlayImageIdx),faceW,faceH);
                PointF position = maskPosition(curOverayResized,centerX,centerY);
                canvas.drawBitmap(curOverayResized,position.x,position.y,null);
            }
            // Draw landmark
            for (Point point : landmarks) {
                int pointX = (int) getX(point.x);
                int pointY = (int) getY(point.y);
                canvas.drawCircle(pointX, pointY, 2, mFaceLandmarkPaint);
            }
        }
        curOverlayImageIdx +=1;
        if(curOverlayImageIdx==overlayImages.size()){
            curOverlayImageIdx = 0;
        }
    }

    private PointF maskPosition(Bitmap overlayImg,float centerX,float centerY){
        PointF position = new PointF();
        position.x = centerX - overlayImg.getWidth()/2;
        position.y = centerY - overlayImg.getHeight()/2;
        return position;

    }
    private Bitmap resizeMask(Bitmap overlayImg, float faceWidth,float faceHeight){
        overlayImg = resizeBitmap(overlayImg,faceWidth,faceHeight);
        return overlayImg;
    }
    private Bitmap resizeBitmap(Bitmap bmp,float newWidth,float newHeight){
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        float scaleWidth = (2*newWidth) / width ;
        float scaleHeight = (2*newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bmp, 0, 0, width, height, matrix, false);
        return resizedBitmap;
    }

}
