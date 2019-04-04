package com.nhancv.facemask.m2d.mask;

        import android.graphics.Bitmap;
        import android.graphics.Point;

        import java.util.List;

public interface IMaskStrategy {
    public Mask definePosition( List<Point> landmarks, float faceWidth,float faceHeight, Bitmap mask);
}
