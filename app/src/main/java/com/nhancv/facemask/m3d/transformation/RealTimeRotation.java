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
//        objPoints.add(new Point3(1.10753, 15.2893, -68.137));//nose tip
//        objPoints.add(new Point3(0.637927, 75.9671, -51.8616));//chin
//        objPoints.add(new Point3(-48.4752, -23.0419, -31.6016));//left eye left corner
//        objPoints.add(new Point3(49.6329, -23.167, -30.1738));//right eye right corner
//        objPoints.add(new Point3(-19.1493, 34.4437, -50.5215));//left mouth corner
//        objPoints.add(new Point3(20.2692, 34.455, -50.0247) );//right mouth corner

        objPoints.add(new Point3(36.8301,78.3185,52.0345));//nose tip 1879
        objPoints.add(new Point3(36.547054, 9.838245, 32.105911));//chin 1398
        objPoints.add(new Point3(-12.574795, 108.580139, 11.793648));//left eye left corner 1853
        objPoints.add(new Point3(18.656631, 106.811218, 18.971336));//left eye right corner 1698
        objPoints.add(new Point3(54.057266, 106.811218, 18.468582));//right eye  left corner 837
        objPoints.add(new Point3(86.058228, 109.108177, 10.279884));//right eye right corner 846
        objPoints.add(new Point3(14.8498,51.0115,30.2378));//left mouth corner 1502
        objPoints.add(new Point3(58.1825,51.0115,29.6224));//right mouth corner 695
        objPoints.add(new Point3(36.708527, 59.016479, 43.475983));//top mouth corner 1881
        objPoints.add(new Point3(36.649536, 43.362301, 39.322105));//bottom mouth corner 2225
        objPoints.add(new Point3(-29.307529, 117.203842, 1.900108));//top left chin 1764
        objPoints.add(new Point3(101.509781, 117.203842, 0.042267));//top right chin 908
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
