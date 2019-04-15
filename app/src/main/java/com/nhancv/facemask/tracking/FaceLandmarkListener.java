package com.nhancv.facemask.tracking;

import android.graphics.Matrix;

import zeusees.tracking.Face;

public interface FaceLandmarkListener {
    /**
     * Notify result after detect
     * @param previewWidth from resized bitmap width for detect
     * @param previewHeight from resized bitmap height for detect
     */
    void landmarkUpdate(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix);
}
