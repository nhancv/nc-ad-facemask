package com.nhancv.facemask.m2d.mask;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;

import java.util.List;

public class Mustache implements IMaskStrategy {
    DistanceHelper distanceHelper = new DistanceHelper();
    @Override
    public Mask definePosition(List<Point> landmarks, float faceWidth,float faceHeight, Bitmap mask) {
        float originHeight = mask.getHeight();
        float originWidth = mask.getWidth();
        float noseWidth = distanceHelper.distance(landmarks.get(35),landmarks.get(31));
        float noseHeight = distanceHelper.distance(landmarks.get(33),landmarks.get(30));
        float mustacheWidth = 3*noseWidth;
        float mustacheHeight = mustacheWidth*originHeight/originWidth;
        float noseX = landmarks.get(31).x;
        float noseY = landmarks.get(30).y;
        float x1 =  (noseX-mustacheWidth/4);
        float x2 = (noseX + noseWidth + mustacheWidth/4);
        float y1 =  (noseY + noseHeight - mustacheHeight/2);
        float y2 = (noseY + noseHeight + mustacheHeight/2);
        mustacheWidth = x2 - x1;
        mustacheHeight = y2 - y1;
        Bitmap preMask = distanceHelper.resizeMask(mask,mustacheWidth,mustacheHeight);
        PointF position = distanceHelper.imagePosition(new PointF(landmarks.get(33).x,landmarks.get(33).y),mustacheWidth,mustacheHeight);
        Mask maskResult = new MaskBuilder().setBmMask(preMask).setPositionOnFace(position).build();

        return maskResult;
    }
}
