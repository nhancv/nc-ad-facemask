package com.nhancv.facemask.m2d.mask;

import android.graphics.Bitmap;
import android.graphics.Point;

import java.util.List;

public class Head5 implements  IMaskStrategy{
    @Override
    public Mask definePosition(List<Point> landmarks, float faceWidth, float faceHeight, Bitmap mask) {
        //virtual animal ear
        //using width =  3/2 width of face
        //height = width*(height/width)
        //posX =
        //posY
        return null;
    }
}
