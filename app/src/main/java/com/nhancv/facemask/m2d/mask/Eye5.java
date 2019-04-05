package com.nhancv.facemask.m2d.mask;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;

import java.util.List;

public class Eye5 implements IMaskStrategy {
    DistanceHelper distanceHelper = new DistanceHelper();

    @Override
    public Mask definePosition(List<Point> landmarks, float faceWidth, float faceHeight, Bitmap mask) {
        float oMaskWidth = mask.getWidth();
        float oMaskHeight = mask.getHeight();
        float eye_width = distanceHelper.distance(landmarks.get(2),landmarks.get(0));
        //float eye_height = 2*distanceHelper.distance(landmarks.get(2),landmarks.get(3));
        /*
        * Eye width = the current EyeWidth + Width of 1 eye
        * */
        eye_width += distanceHelper.distance(landmarks.get(2),landmarks.get(1));
        float eye_height = eye_width*oMaskHeight/oMaskWidth;
        Bitmap preMask = distanceHelper.resizeMask(mask,eye_width,eye_height);
        float centerX = ((float)landmarks.get(1).x + (float)landmarks.get(3).x)/2f;
        float centerY = landmarks.get(1).y;
        PointF result  = distanceHelper.imagePosition(new PointF(centerX,centerY),eye_width,eye_height);
        Mask maskResult = new MaskBuilder().setBmMask(preMask).setPositionOnFace(result).build();
        return maskResult;
    }
}
