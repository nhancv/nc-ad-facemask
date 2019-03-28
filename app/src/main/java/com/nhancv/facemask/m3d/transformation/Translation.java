package com.nhancv.facemask.m3d.transformation;
/*
* Dfine Translation value along x ,y ,z
* */
public class Translation {
    float x = 0;
    float y = 0;
    float z = 0;
    public Translation(float x, float y, float z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public float[] translationValue()
    {
        return new float[]{x,y,z};
    }
}
