package com.nhancv.facemask.m2d.mask;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;

import java.util.List;

public class Mustache5 implements IMaskStrategy {
    DistanceHelper distanceHelper = new DistanceHelper();
    @Override
    public Mask definePosition(List<Point> landmarks, float faceWidth, float faceHeight, Bitmap mask) {
        float originHeight = mask.getHeight();
        float originWidth = mask.getWidth();
        float mustacheWidth = distanceHelper.distance(landmarks.get(0),landmarks.get(2));
        float mustacheHeight = mustacheWidth*originHeight/originWidth;
        Bitmap preMask = distanceHelper.resizeMask(mask,mustacheWidth,mustacheHeight);
        PointF position = distanceHelper.imagePosition(new PointF(landmarks.get(4).x,landmarks.get(4).y),mustacheWidth,mustacheHeight);
        Mask maskResult = new MaskBuilder().setBmMask(preMask).setPositionOnFace(position).build();

        return maskResult;
    }
}
