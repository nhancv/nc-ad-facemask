package com.nhancv.facemask.m2d;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import com.nhancv.facemask.tracking.FaceLandmarkListener;

import zeusees.tracking.Face;

public class M2dController implements FaceLandmarkListener {

    private M2dPreview m2dPreview;

    public M2dController(M2dPreview m2dPreview) {
        this.m2dPreview = m2dPreview;
    }

    @Override
    public void landmarkUpdate(Bitmap previewBm, Face face, int previewWidth, int previewHeight, Matrix scaleMatrix) {
        //1280x720
        //640x480
        m2dPreview.setVisionDetRetList(previewBm, face, previewWidth, previewHeight, scaleMatrix);
    }
}
