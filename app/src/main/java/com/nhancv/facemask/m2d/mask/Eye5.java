package com.nhancv.facemask.m2d.mask;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;

import java.util.List;

public class Eye5 implements IMaskStrategy {
    DistanceHelper distanceHelper = new DistanceHelper();

    @Override
    public Mask definePosition(List<Point> landmarks, float faceWidth, float faceHeight, Bitmap mask) {
        float eye_width = distanceHelper.distance(landmarks.get(2),landmarks.get(0));
        float eye_height = 2*distanceHelper.distance(landmarks.get(2),landmarks.get(3));
        eye_width += distanceHelper.distance(landmarks.get(2),landmarks.get(1));
        Bitmap preMask = distanceHelper.resizeMask(mask,eye_width,eye_height);
        float centerX = ((float)landmarks.get(1).x + (float)landmarks.get(3).x)/2;
        float centerY = ((float)landmarks.get(1).y + (float)landmarks.get(3).y)/2;
        PointF result  = distanceHelper.imagePosition(new PointF(centerX,centerY),eye_width,eye_height);
        Mask maskResult = new MaskBuilder().setBmMask(preMask).setPositionOnFace(result).build();
        return maskResult;
    }
}
