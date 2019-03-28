package com.nhancv.facemask;

import com.tzutalin.dlib.VisionDetRet;

import java.util.List;

public interface FaceLandmarkListener {
    /**
     * Notify result after detect
     * @param visionDetRetList detect results
     * @param bmW from resized bitmap width for detect
     * @param bmH from resized bitmap height for detect
     */
    void landmarkUpdate(List<VisionDetRet> visionDetRetList, int bmW, int bmH);
}
