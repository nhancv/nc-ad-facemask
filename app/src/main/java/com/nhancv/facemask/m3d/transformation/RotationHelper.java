package com.nhancv.facemask.m3d.transformation;

public class RotationHelper {

    public float normalizeRange(float radian, float value) {
        float degree = (float) Math.toDegrees(radian);
        if (degree < -value) {
            degree = -value;
        } else if (degree > value) {
            degree = value;
        }
        return degree;
    }
}
