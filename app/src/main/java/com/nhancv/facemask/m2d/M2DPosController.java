package com.nhancv.facemask.m2d;

import com.nhancv.facemask.FaceLandmarkListener;
import com.tzutalin.dlib.VisionDetRet;

import java.util.List;

public class M2DPosController implements FaceLandmarkListener {

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
}
