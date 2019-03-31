package com.nhancv.facemask.m2d;

import android.graphics.Bitmap;

import com.nhancv.facemask.FaceLandmarkListener;
import com.nhancv.facemask.OverlayImageListener;
import com.tzutalin.dlib.VisionDetRet;

import java.util.List;

public class M2DPosController implements FaceLandmarkListener, OverlayImageListener {

    private M2DLandmarkView landmarkView;

    public M2DPosController(M2DLandmarkView landmarkView) {
        this.landmarkView = landmarkView;
    }

    @Override
    public void landmarkUpdate(List<VisionDetRet> visionDetRetList, int bmW, int bmH) {
        //1280x720
        //640x480
        landmarkView.setVisionDetRetList(visionDetRetList, bmW, bmH);
        landmarkView.invalidate();
    }

    @Override
    public void update(Bitmap overlayImg) {
        landmarkView.updateOverlayImage(overlayImg);
    }
}
