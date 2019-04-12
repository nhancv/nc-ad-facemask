package com.nhancv.facemask;

import java.util.List;

import zeusees.tracking.Face;

public interface FaceLandmarkListener {
    /**
     * Notify result after detect
     * @param visionDetRetList detect results
     * @param bmW from resized bitmap width for detect
     * @param bmH from resized bitmap height for detect
     */
    void landmarkUpdate(List<Face> visionDetRetList, int bmW, int bmH);
}
