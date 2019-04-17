package com.nhancv.facemask.m3d.transformation;

import android.graphics.Point;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
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
        objPoints.add(new Point3(1.10753, 15.2893, -68.137));//
        objPoints.add(new Point3(0.637927, 75.9671, -51.8616));
        objPoints.add(new Point3(-48.4752, -23.0419, -31.6016));
        objPoints.add(new Point3(49.6329, -23.167, -30.1738));
        objPoints.add(new Point3(-19.1493, 34.4437, -50.5215));
        objPoints.add(new Point3(20.2692, 34.455, -50.0247) );
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
    public void releaseMatrix(){
        camMatrix.release();
        objPointsMat.release();

    }
}
