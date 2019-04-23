package com.nhancv.facemask.m3d.transformation;

import android.graphics.Point;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;

import java.util.ArrayList;
import java.util.List;

public class RealTimeRotation {
    MatOfPoint3f objPointsMat = new MatOfPoint3f();
    private float focalLength = 543.45f;
    private Mat camMatrix;

    private RealTimeRotation() {

    }

    public static RealTimeRotation getInstance() {
        return LazyHolder.INSTANCE;
    }

    public MatOfPoint3f setUpWorldPoints() {
        List<Point3> objPoints = new ArrayList<Point3>();

        objPoints.add(new Point3(36.8301, 78.3185, 52.0345));//nose tip 1879
        objPoints.add(new Point3(46.391205, 121.975700, 36.571663));//v897 -> 44 lm
        objPoints.add(new Point3(26.833687, 121.975700, 36.849419));//v1873 -> 60 lm
        objPoints.add(new Point3(36.6623, 68.8159, 40.2229));//nose tip -> 46 lm
        objPoints.add(new Point3(49.6196, 71.8481, 38.7055));//nose tip -> 93 lm
        objPoints.add(new Point3(23.6672, 71.8481, 39.074));//nose tip -> 31 lm
        objPoints.add(new Point3(36.599148, 109.525101, 35.774132));//v2224 -> 21 lm
        objPoints.add(new Point3(36.547054, 9.838245, 32.105911));//chin 1398 -> 0 lm
        objPoints.add(new Point3(-14.982872, 108.473167, 10.518028));//left eye left corner
        objPoints.add(new Point3(18.656631, 106.811218, 18.971336));//left eye right corner
        objPoints.add(new Point3(54.057266, 106.811218, 18.4685822));//right eye left corner
        objPoints.add(new Point3(87.579941, 109.308273, 8.945969));//right eye right corner
        objPoints.add(new Point3(11.319393, 53.651268, 33.162163));//left mouth corner
        objPoints.add(new Point3(61.794533, 53.651268, 32.445320));//right mouth corner
        objPoints.add(new Point3(-47.058544, 129.762482, -65.171806));// -> 11 lm
        objPoints.add(new Point3(115.961105, 129.078583, -56.371410));// -> 13 lm
        objPoints.add(new Point3(-40.034130, 74.127586, -55.912411));// -> 7 lm
        objPoints.add(new Point3(78.678078, 26.228161, 6.232826));// -> 100 lm
        objPoints.add(new Point3(-6.301723, 26.228161, 7.439697));// -> 57 lm
        objPoints.add(new Point3(110.495117, 74.127586, -58.050194));// -> 16 lm
        objPointsMat.fromList(objPoints);
        return objPointsMat;
    }

    //receive the center point of the frame
    public Mat setUpCamMatrix(Point centerPoint) {
        float[] camArray = new float[]{focalLength, 0, centerPoint.x, 0, focalLength, centerPoint.y, 0, 0, 1};
        camMatrix = new Mat(3, 3, CvType.CV_32F);
        camMatrix.put(0, 0, camArray);
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

    public void releaseMatrix() {
        camMatrix.release();
        objPointsMat.release();

    }

    private static class LazyHolder {
        private static final RealTimeRotation INSTANCE = new RealTimeRotation();
    }
}
