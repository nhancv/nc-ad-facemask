package com.nhancv.facemask.m2d.mask;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;

import java.util.List;

public class Nose5 implements IMaskStrategy{
    DistanceHelper distanceHelper = new DistanceHelper();

    @Override
    public Mask definePosition(List<Point> landmarks, float faceWidth, float faceHeight, Bitmap mask) {
        float eyeCenterX = landmarks.get(4).x;
        float eyeCenterY = landmarks.get(3).y;
        //eye center
        PointF eyeCenter = new PointF(eyeCenterX,eyeCenterY);
        //nose center
        float noseCenterX = eyeCenter.x;
        float noseCenterY = (landmarks.get(4).y + eyeCenterY)/2;

        float noseY = (noseCenterY+ eyeCenterY)/2;
        float nosePosY = (noseCenterY+landmarks.get(4).y)/2;
        PointF nose = new PointF(noseCenterX,nosePosY);

        float oMaskWidth = mask.getWidth();
        float oMaskHeight = mask.getHeight();
        float maskHeight = landmarks.get(4).y - noseY;
        float maskWidth = maskHeight* oMaskWidth/oMaskHeight;

        Bitmap preMask = distanceHelper.resizeMask(mask,maskWidth,maskHeight);

        PointF result  = distanceHelper.imagePosition(nose,maskWidth,maskHeight);
        Mask maskResult = new MaskBuilder().setBmMask(preMask).setPositionOnFace(result).build();
        return maskResult;
    }
}
