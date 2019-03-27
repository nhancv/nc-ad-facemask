package com.nhancv.facemask;

import com.tzutalin.dlib.VisionDetRet;

import java.util.List;

public interface FaceLandmarkListener {
    void landmarkUpdate(List<VisionDetRet> visionDetRetList);
}
