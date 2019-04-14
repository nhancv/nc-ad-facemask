package com.nhancv.facemask.m2d;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import com.nhancv.facemask.FaceLandmarkListener;
import com.nhancv.facemask.OverlayImageListener;

import java.util.HashMap;

import zeusees.tracking.Face;

public class M2DPosController implements FaceLandmarkListener, OverlayImageListener {

    private M2DLandmarkView landmarkView;

    public M2DPosController(M2DLandmarkView landmarkView) {
        this.landmarkView = landmarkView;
    }

    @Override
    public void landmarkUpdate(Face face, int bmW, int bmH, Matrix scaleMatrix) {
        //1280x720
        //640x480
        landmarkView.setVisionDetRetList(face, bmW, bmH, scaleMatrix);
        landmarkView.invalidate();
    }

    @Override
    public void update(HashMap<String, Bitmap> overlayElements) {
        landmarkView.updateOverlayImage(overlayElements);
    }
}
