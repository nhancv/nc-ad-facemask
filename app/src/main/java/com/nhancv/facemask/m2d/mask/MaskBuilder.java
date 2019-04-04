package com.nhancv.facemask.m2d.mask;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;

public class MaskBuilder {
    Bitmap bmMask;
    float width;
    float height;
    PointF positionOnFace;

    public MaskBuilder setBmMask(Bitmap bmMask) {
        this.bmMask = bmMask;
        return this;
    }

    public MaskBuilder setWidth(float width) {
        this.width = width;
        return this;
    }

    public MaskBuilder setHeight(float height) {
        this.height = height;
        return this;
    }

    public MaskBuilder setPositionOnFace(PointF positionOnFace) {
        this.positionOnFace = positionOnFace;
        return this;
    }
    public Mask build(){
        return new Mask(this.bmMask,this.positionOnFace);
    }
}
