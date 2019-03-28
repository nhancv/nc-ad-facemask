package com.nhancv.facemask.m3d;

import android.util.Log;

import com.nhancv.facemask.FaceLandmarkListener;
import com.tzutalin.dlib.VisionDetRet;

import java.util.List;

public class M3DPosController implements FaceLandmarkListener {

    private final static String TAG = M3DPosController.class.getName();
    private M3DSurfaceView surfaceView;
    private M3DRenderer renderer;

    public M3DPosController(M3DSurfaceView surfaceView) {
        this.surfaceView = surfaceView;
        this.renderer = surfaceView.getModelRenderer();
    }

    @Override
    public void landmarkUpdate(List<VisionDetRet> visionDetRetList, int bmW, int bmH) {
        Log.d(TAG, "landmarkUpdate: " + visionDetRetList.size());

    }
}
