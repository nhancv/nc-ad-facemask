/*
 * MIT License
 *
 * Copyright (c) 2019 BeeSight Soft
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * @author Nhan Cao <nhan.cao@beesightsoft.com>
 */

package com.nhancv.facemask.pose;

import android.graphics.Point;
import android.graphics.PointF;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.opencv.core.CvType.CV_32FC1;
import static org.opencv.core.CvType.CV_64FC1;

public class SolvePNP {
    private static final String TAG = SolvePNP.class.getSimpleName();
    private float rx, ry, rz;
    private float tx, ty, tz;
    private PointF[] point2Ds;
    private Mat camMatrix;
    private MatOfPoint3f objPointMat;
    private MatOfDouble distCoeffs;
    private Mat rotationVector;
    private Mat translationVector;

    private float focalLength = 543.45f;
    private boolean initialized = false;

    private SolvePNP() {

    }

    public static SolvePNP getInstance() {
        return SolvePNP.LazyHolder.INSTANCE;
    }

    private static class LazyHolder {
        private static final SolvePNP INSTANCE = new SolvePNP();
    }

    public void initialize(Point centerPoint) {
        if (!initialized) {
            initialized = true;
            point2Ds = new PointF[106];
            for (int i = 0; i < 106; i++) {
                point2Ds[i] = new PointF(0, 0);
            }
            setUpDistCoeff();
            setUpCamMatrix(centerPoint);
            setUpWorldPoints();
        }
    }

    private void setUpDistCoeff() {
        Mat coeffMat = new Mat();
        Mat.zeros(4, 1, CV_64FC1).copyTo(coeffMat);
        distCoeffs = new MatOfDouble(coeffMat);
    }

    private void setUpCamMatrix(Point centerPoint) {
        float[] camArray = new float[]{focalLength, 0, centerPoint.x, 0, focalLength, centerPoint.y, 0, 0, 1};
        camMatrix = new Mat(3, 3, CvType.CV_32F);
        camMatrix.put(0, 0, camArray);
    }

    private void setUpWorldPoints() {
        objPointMat = new MatOfPoint3f();
        List<Point3> objPoints = new ArrayList<>();
        objPoints.add(new Point3(36.8301, 78.3185, 52.0345));//glass tip 1879
        objPoints.add(new Point3(46.391205, 121.975700, 36.571663));//v897 -> 44 lm
        objPoints.add(new Point3(26.833687, 121.975700, 36.849419));//v1873 -> 60 lm
        objPoints.add(new Point3(36.6623, 68.8159, 40.2229));//glass tip -> 46 lm
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
        objPointMat.fromList(objPoints);
    }

    public void setUpLandmarks(PointF[] landmarks) {
        this.point2Ds = new PointF[landmarks.length];
        for (int i = 0; i < landmarks.length; i++) {
            this.point2Ds[i] = landmarks[i];
        }
    }

    private Mat setUpRotM() {
        Mat rotM = new Mat();
        Mat.zeros(3, 3, CV_64FC1).copyTo(rotM);
        return rotM;
    }

    private MatOfPoint2f getValidPoints() {
        List<org.opencv.core.Point> objPoints = new ArrayList<>();
        MatOfPoint2f imagePoints = new MatOfPoint2f();
        objPoints.add(new org.opencv.core.Point(point2Ds[69].x, point2Ds[69].y)); //glass tip
        objPoints.add(new org.opencv.core.Point(point2Ds[44].x, point2Ds[44].y)); //glass tip
        objPoints.add(new org.opencv.core.Point(point2Ds[60].x, point2Ds[60].y)); //glass tip
        objPoints.add(new org.opencv.core.Point(point2Ds[46].x, point2Ds[46].y)); //glass
        objPoints.add(new org.opencv.core.Point(point2Ds[21].x, point2Ds[21].y)); //v2224 -> 21 lm
        objPoints.add(new org.opencv.core.Point(point2Ds[0].x, point2Ds[0].y)); //chin
        objPoints.add(new org.opencv.core.Point(point2Ds[94].x, point2Ds[94].y)); //left eye left corner
        objPoints.add(new org.opencv.core.Point(point2Ds[59].x, point2Ds[59].y)); //left eye right corner
        objPoints.add(new org.opencv.core.Point(point2Ds[27].x, point2Ds[27].y)); //right eye left corner
        objPoints.add(new org.opencv.core.Point(point2Ds[20].x, point2Ds[20].y));//right eye right corner
        objPoints.add(new org.opencv.core.Point(point2Ds[45].x, point2Ds[45].y));//left mouth corner
        objPoints.add(new org.opencv.core.Point(point2Ds[50].x, point2Ds[50].y));//right mouth corner
        objPoints.add(new org.opencv.core.Point(point2Ds[11].x, point2Ds[11].y));//
        objPoints.add(new org.opencv.core.Point(point2Ds[13].x, point2Ds[13].y));//
        objPoints.add(new org.opencv.core.Point(point2Ds[7].x, point2Ds[7].y));//top left chin
        objPoints.add(new org.opencv.core.Point(point2Ds[100].x, point2Ds[100].y));//top right chin
        objPoints.add(new org.opencv.core.Point(point2Ds[57].x, point2Ds[57].y));//top left chin
        objPoints.add(new org.opencv.core.Point(point2Ds[16].x, point2Ds[16].y));//top right chin
        imagePoints.fromList(objPoints);
        return imagePoints;
    }

    private MatOfPoint3f getProjectPoints() {
        MatOfPoint3f projectPoints = new MatOfPoint3f();
        List<org.opencv.core.Point3> objPoints = new ArrayList<>();
        objPoints.add(new org.opencv.core.Point3(0f, 0f, 1000.0f));
        projectPoints.fromList(objPoints);
        return projectPoints;
    }

    public void solvePNP() {
        if (!initialized) throw new AssertionError("Object need to initialize as first.");

        MatOfPoint2f imagePoints = this.getValidPoints();
        this.rotationVector = new Mat();
        this.translationVector = new Mat();

        Calib3d.solvePnP(this.objPointMat, imagePoints, this.camMatrix, this.distCoeffs, this.rotationVector, this.translationVector);
        tx = (float) this.translationVector.get(0, 0)[0];
        ty = (float) this.translationVector.get(1, 0)[0];
        tz = (float) this.translationVector.get(2, 0)[0];
        Mat rotM = setUpRotM();
        //convert from rotation vector to rotM
        Calib3d.Rodrigues(rotationVector, rotM);
        Mat projMatrix = setUpProjMatrix(rotM);
        Mat eav = new Mat();
        Calib3d.decomposeProjectionMatrix(projMatrix, new Mat(), new Mat(), new Mat(), new Mat(), new Mat(), new Mat(), eav);

        rx = (float) eav.get(0, 0)[0];
        ry = (float) eav.get(1, 0)[0];
        rz = (float) eav.get(2, 0)[0];

        rotM.release();
        projMatrix.release();

    }

    /**
     * Input: rotMatrix
     * Output: projection Matrix
     */
    private Mat setUpProjMatrix(Mat rotM) {
        float tv[] = new float[]{0, 0, 1};
        Mat tvMat = new Mat(3, 1, CV_32FC1);
        tvMat.put(0, 0, tv);
        Mat projMat = new Mat();
        List<Mat> src = Arrays.asList(rotM, tvMat);
        Core.hconcat(src, projMat);
        return projMat;
    }

    public float getRx() {
        return rx + 180;
    }

    public float getRy() {
        return -ry;
    }

    public float getRz() {
        return -rz;
    }

    public float getTx() {
        return tx;
    }

    public float getTy() {
        return ty;
    }

    public float getTz() {
        return tz;
    }
}
