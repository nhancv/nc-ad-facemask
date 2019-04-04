package com.nhancv.facemask.m2d.mask;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;

import java.util.List;

public class Eye implements IMaskStrategy {
    DistanceHelper distanceHelper = new DistanceHelper();
    @Override
    public Mask definePosition(List<Point> landmarks, Bitmap mask) {
        float eye_width = distanceHelper.distance(landmarks.get(16),landmarks.get(0));
        float eye_height = distanceHelper.distance(landmarks.get(41),landmarks.get(19));
        Bitmap preMask = distanceHelper.resizeMask(mask,eye_width,eye_height);
        PointF result  = distanceHelper.imagePosition(landmarks.get(27),eye_width,eye_height);
        Mask maskResult = new MaskBuilder().setBmMask(preMask).setPositionOnFace(result).build();
        return maskResult;
    }
}
