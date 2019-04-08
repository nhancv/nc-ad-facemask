package com.nhancv.facemask.m2d.mask;

import android.graphics.Bitmap;
import android.graphics.PointF;

public class Mask {
    Bitmap bmMask;
    int width;
    int height;
    PointF positionOnFace;

    public Bitmap getBmMask() {
        return bmMask;
    }

    public void setBmMask(Bitmap bmMask) {
        this.bmMask = bmMask;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public PointF getPositionOnFace() {
        return positionOnFace;
    }

    public void setPositionOnFace(PointF positionOnFace) {
        this.positionOnFace = positionOnFace;
    }

    public Mask(Bitmap bmMask, PointF positionOnFace) {
        this.bmMask = bmMask;
        this.positionOnFace = positionOnFace;
    }
}
