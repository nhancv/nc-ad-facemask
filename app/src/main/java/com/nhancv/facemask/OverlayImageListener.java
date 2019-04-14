package com.nhancv.facemask;

import android.graphics.Bitmap;

import java.util.HashMap;

public interface OverlayImageListener {
    void update(HashMap<String,Bitmap> overlayElements);
}
