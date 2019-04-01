package com.nhancv.facemask;

import android.graphics.Bitmap;

import java.util.List;

public interface OverlayImageListener {
    public void update(List<Bitmap> overlayImg);
}
