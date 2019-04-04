package com.nhancv.facemask.m2d.mask;

import android.graphics.Bitmap;
import android.graphics.Point;

import java.util.List;

public class MaskController {
    IMaskStrategy maskStrategy;
    public MaskController( IMaskStrategy maskStrategy){
        this.maskStrategy = maskStrategy;
    }
    public Mask defineMask(List<Point> landmarks, Bitmap mask){
        return this.maskStrategy.definePosition(landmarks,mask);
    }
    public void setMaskStrategy(IMaskStrategy iMaskStrategy){
        this.maskStrategy = iMaskStrategy;
    }
}
