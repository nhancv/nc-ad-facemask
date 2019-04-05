package com.nhancv.facemask.m2d.mask;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;

import java.util.List;

public class Head5 implements  IMaskStrategy{
    DistanceHelper distanceHelper = new DistanceHelper();

    @Override
    public Mask definePosition(List<Point> landmarks, float faceWidth, float faceHeight, Bitmap mask) {
        //virtual animal ear
        //using width =  3/2 width of face
        //height = width*(height/width)
        //posX =
        //posY = centerEye + 1/3 Face Height + maskHeight/2;
        //float posX =
        float oEarHeight = mask.getHeight();
        float oEarWidth = mask.getWidth();
        float eyeWidth = distanceHelper.distance(landmarks.get(0),landmarks.get(2));
        float maskWidth = 2*eyeWidth;//maskWidth
        float maskHeight = maskWidth*(oEarHeight/oEarWidth);//maskHeight
        float centerEyeX = landmarks.get(4).x;
        float centerEyeY = landmarks.get(3).y;
        float maskY = centerEyeY - eyeWidth/2.0f -maskHeight/2.0f;
        PointF centerMask = new PointF(centerEyeX, maskY);

        Bitmap preMask = distanceHelper.resizeMask(mask,maskWidth,maskHeight);
        PointF result  = distanceHelper.imagePosition(centerMask,maskWidth,maskHeight);

        //PointF result  = distanceHelper.imagePosition(nose,maskWidth,maskHeight);
        Mask maskResult = new MaskBuilder().setBmMask(preMask).setPositionOnFace(result).build();
        return maskResult;
    }
}
