package com.nhancv.facemask.m3d.transformation;

import android.graphics.Point;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;

import java.util.ArrayList;
import java.util.List;

public class RealTimeRotation {

    private RealTimeRotation(){

    }
    private static class LazyHolder{
        private static final RealTimeRotation INSTANCE  = new RealTimeRotation();
    }
    public static RealTimeRotation getInstance(){
        return LazyHolder.INSTANCE;
    }
     MatOfPoint3f objPointsMat = new MatOfPoint3f();
    private float focalLength = 543.45f;
    private Mat camMatrix;
    public MatOfPoint3f setUpWorldPoints(){
        List<Point3> objPoints = new ArrayList<Point3>();
        objPoints.add(new Point3(0.0,0.0,0.0));
        objPoints.add(new Point3(0.0,-330.0,-65.0));
        objPoints.add(new Point3(-225.0, 170.0, -135.0));
        objPoints.add(new Point3(225.0, 170.0, -135.0));
        objPoints.add(new Point3(-150.0, -150.0, -125.0));
        objPoints.add(new Point3(150.0, -150.0, -125.0) );
        objPointsMat.fromList(objPoints);
        return objPointsMat;
    }
    //receive the center point of the frame
    public Mat setUpCamMatrix(Point centerPoint){
        float[] camArray = new float[] {focalLength, 0, centerPoint.x, 0, focalLength, centerPoint.y, 0, 0, 1};
        camMatrix = new Mat(3,3, CvType.CV_32F);
        camMatrix.put(0,0,camArray);
        return camMatrix;
    }

    public float getFocalLength() {
        return focalLength;
    }

    public void setFocalLength(float focalLength) {
        this.focalLength = focalLength;
    }

    public MatOfPoint3f getObjPointsMat() {
        return objPointsMat;
    }

    public Mat getCamMatrix() {
        return camMatrix;
    }
}
