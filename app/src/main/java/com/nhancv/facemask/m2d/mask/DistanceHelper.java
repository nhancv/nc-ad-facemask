package com.nhancv.facemask.m2d.mask;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;

public class DistanceHelper {
    public float distance(Point p1, Point p2){
        double d1 = Math.abs(p1.x-p2.x);
        double d2 = Math.abs(p1.y - p2.y);
        return (float)Math.hypot(d1,d2);
    }
    public Bitmap resizeMask(Bitmap overlayImg, float faceWidth, float faceHeight){
        overlayImg = resizeBitmap(overlayImg,faceWidth,faceHeight);
        return overlayImg;
    }
    private Bitmap resizeBitmap(Bitmap bmp,float newWidth,float newHeight){
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        float scaleWidth = (newWidth) / width ;
        float scaleHeight = (newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bmp, 0, 0, width, height, matrix, false);
        return resizedBitmap;
    }
    public PointF imagePosition(Point center, float objWidth, float objHeight){
        float x =center.x - objWidth/2;
        float y = center.y - objHeight/2;
        PointF result = new PointF(x,y);
        return result;
    }
}
