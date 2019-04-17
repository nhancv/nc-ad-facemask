package com.nhancv.facemask.m3d.transformation;
/*
Define the rotation value along x-y-z axis
 */
public class Rotation {
    float x = 0;
    float y = 0;
    float z = 0;

    public Rotation( float x, float y, float z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float[] rotationValue()
    {
        return new float[]{x,y,z};
    }
}
