package com.nhancv.facemask;

import android.graphics.Bitmap;

import java.util.HashMap;
import java.util.List;

public interface OverlayImageListener {
    public void update(HashMap<String,Bitmap> overlayElements);
}
