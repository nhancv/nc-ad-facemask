package com.nhancv.facemask.m2d;

import android.graphics.Matrix;

import com.nhancv.facemask.tracking.FaceLandmarkListener;

import zeusees.tracking.Face;

public class M2DPosController implements FaceLandmarkListener {

    private M2DLandmarkView landmarkView;

    public M2DPosController(M2DLandmarkView landmarkView) {
        this.landmarkView = landmarkView;
    }

    @Override
    public void landmarkUpdate(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix) {
        //1280x720
        //640x480
        landmarkView.setVisionDetRetList(face, previewWidth, previewHeight, scaleMatrix);
        landmarkView.invalidate();
    }
}
