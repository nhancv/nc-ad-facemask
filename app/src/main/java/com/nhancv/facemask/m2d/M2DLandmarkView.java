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

import com.nhancv.facemask.m2d.mask.Eye;
import com.nhancv.facemask.m2d.mask.Eye5;
import com.nhancv.facemask.m2d.mask.Head5;
import com.nhancv.facemask.m2d.mask.IMaskStrategy;
import com.nhancv.facemask.m2d.mask.Mask;
import com.nhancv.facemask.m2d.mask.MaskController;
import com.nhancv.facemask.m2d.mask.Mustache;
import com.nhancv.facemask.m2d.mask.Mustache5;
import com.nhancv.facemask.m2d.mask.Nose5;
import com.tzutalin.dlib.VisionDetRet;
import org.opencv.core.Algorithm;
import org.opencv.objdetect.CascadeClassifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hugo.weaving.DebugLog;

//import org.opencv.core.CvType;
//import org.opencv.core.Mat;
//import org.opencv.core.MatOfInt;
//import org.opencv.core.MatOfPoint;
//import org.opencv.core.Scalar;
//import org.opencv.imgproc.Imgproc;

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
    private HashMap<String,Bitmap> overlayElements;
    private int curOverlayImageIdx = 0;

    private Bitmap curFaceImg;
    private Bitmap curOverlayResized;
    //private Mat overlayMat; //img2
    //private Mat curFaceWarped;
    private Mask curMask;
    private MaskController maskController;
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
        maskController = new MaskController(new Head5());
        this.curOverlayImageIdx = 0;//start again
        //this.overlayMat = bitmapConversion.convertBitmap2Mat(this.overlayImg);
        //this.curFaceWarped = this.overlayMat.clone(); //image
        //this.curFaceWarped.convertTo(this.curFaceWarped, CvType.CV_32F);//convert the curFaceWarp to
    }
    public void updateOverlayImage(HashMap<String,Bitmap> elements){
        Log.d("M2DLandmarkView","img load");
        this.overlayElements = elements;
//        maskController = new MaskController(new Head5());
        maskController = new MaskController();
        //this.curOverlayImageIdx = 0;//start again
        //this.overlayMat = bitmapConversion.convertBitmap2Mat(this.overlayImg);
        //this.curFaceWarped = this.overlayMat.clone(); //image
        //this.curFaceWarped.convertTo(this.curFaceWarped, CvType.CV_32F);//convert the curFaceWarp to
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
    private List<Point> normalizePoint(List<Point> landmarks){
        List<Point> normLandmarks = new ArrayList<>();
        for (Point point : landmarks) {
            int pointX = (int) getX(point.x);
            int pointY = (int) getY(point.y);
            normLandmarks.add(new Point(pointX,pointY));
        }
        return normLandmarks;
    }
    @Override
    protected void onDraw(Canvas canvas) {

        if (visionDetRetList == null) return;
        for (final VisionDetRet ret : visionDetRetList) {
            //curFace = bitmapConversion.convertBitmap2Mat(this.curFaceImg);//convert bitmap face to matrix
            List<Point> landmarks = ret.getFaceLandmarks();
            List<Point> normLandmark = this.normalizePoint(landmarks);
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
            canvas.drawRect(bounds, mFaceLandmarkPaint);
            float centerX = faceCenterX(bounds.left, bounds.right);
            float centerY = faceCenterY(bounds.top,bounds.bottom);
            float faceW = bounds.right - bounds.left;
            float faceH = bounds.bottom - bounds.top;
//            if(overlayImages!=null) {
//                if (overlayImages.get(curOverlayImageIdx) != null) {
//                    //curOverlayResized = resizeMask(overlayImages.get(curOverlayImageIdx), faceW, faceH);
//
//                    curMask = maskController.defineMask(normLandmark,faceW,faceH,overlayImages.get(curOverlayImageIdx)); //get Mask Position info
//                    //PointF position = maskPosition(curOverlayResized, centerX, centerY);
//                    PointF position = curMask.getPositionOnFace();
//                    curOverlayResized = curMask.getBmMask();
//                    canvas.drawBitmap(curOverlayResized, position.x, position.y, null);
//                }
//            }
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
                    curOverlayResized = curMask.getBmMask();
                    canvas.drawBitmap(curOverlayResized, position.x, position.y, null);
                }
            }
            //canvas.drawLine(f);
            // Draw landmark
           for (Point point : landmarks) {
                int pointX = (int) getX(point.x);
                int pointY = (int) getY(point.y);
                canvas.drawCircle(pointX, pointY, 2, mFaceLandmarkPaint);
            }
        }
//        curOverlayImageIdx +=1;
//        if(curOverlayImageIdx==overlayImages.size()){
//            curOverlayImageIdx = 0;
//        }
    }

    private PointF maskPosition(Bitmap overlayImg,float centerX,float centerY){
        PointF position = new PointF();
        position.x = centerX - overlayImg.getWidth()/2f;
        position.y = centerY - overlayImg.getHeight()/2f;
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
