package com.nhancv.facemask.m3d.transformation;

public class Scale {
    float x = 1;
    float y = 1;
    float z = 1;
    public Scale( float x, float y, float z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public float[] scaleValue()
    {
        return new float[]{x,y,z};
    }
}
