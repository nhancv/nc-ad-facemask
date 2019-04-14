package com.nhancv.facemask.tracking;

import android.graphics.Matrix;

import zeusees.tracking.Face;

public interface FaceLandmarkListener {
    /**
     * Notify result after detect
     * @param bmW from resized bitmap width for detect
     * @param bmH from resized bitmap height for detect
     */
    void landmarkUpdate(Face face, int bmW, int bmH, Matrix scaleMatrix);
}
