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

package com.nhancv.facemask.util;

import android.graphics.PointF;

import com.nhancv.facemask.m3d.transformation.RealTimeRotation;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;

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
    private MatOfPoint3f objPointMat;
    private Mat camMatrix;
    private MatOfDouble distCoeffs;
    private Mat rotationVector;
    private Mat translationVector;
    private RealTimeRotation realTimeRotation = RealTimeRotation.getInstance();

    public SolvePNP() {

    }

    public void initialize() {
        point2Ds = new PointF[106];
        for (int i = 0; i < 106; i++) {
            point2Ds[i] = new PointF(0, 0);
        }
        setUpDistCoeff();
        camMatrix = realTimeRotation.getCamMatrix();
        objPointMat = realTimeRotation.getObjPointsMat();
    }

    public void setUpLandmarks(PointF[] landmarks) {
        this.point2Ds = landmarks;
    }

    public void releaseMat() {
        if (camMatrix != null) {
            camMatrix.release();
        }
        if (rotationVector != null) {
            rotationVector.release();
        }
        if (translationVector != null) {
            translationVector.release();
        }
        if (objPointMat != null) {
            objPointMat.release();
        }
        if (distCoeffs != null) {
            distCoeffs.release();
        }
    }

    private void setUpDistCoeff() {
        Mat coeffMat = new Mat();
        Mat.zeros(4, 1, CV_64FC1).copyTo(coeffMat);
        distCoeffs = new MatOfDouble(coeffMat);
    }

    private Mat setUpRotM() {
        Mat rotM = new Mat();
        Mat.zeros(3, 3, CV_64FC1).copyTo(rotM);
        return rotM;
    }

    private MatOfPoint2f getValidPoints() {
        List<org.opencv.core.Point> objPoints = new ArrayList<>();
        MatOfPoint2f imagePoints = new MatOfPoint2f();
        objPoints.add(new org.opencv.core.Point(point2Ds[69].x, point2Ds[69].y)); //nose tip
        objPoints.add(new org.opencv.core.Point(point2Ds[0].x, point2Ds[0].y)); //chin
        objPoints.add(new org.opencv.core.Point(point2Ds[94].x, point2Ds[94].y)); //left eye left corner
        objPoints.add(new org.opencv.core.Point(point2Ds[59].x, point2Ds[59].y)); //left eye right corner
        objPoints.add(new org.opencv.core.Point(point2Ds[27].x, point2Ds[27].y)); //right eye left corner
        objPoints.add(new org.opencv.core.Point(point2Ds[20].x, point2Ds[20].y));//right eye right corner
        objPoints.add(new org.opencv.core.Point(point2Ds[45].x, point2Ds[45].y));//left mouth corner
        objPoints.add(new org.opencv.core.Point(point2Ds[50].x, point2Ds[50].y));//right mouth corner
        objPoints.add(new org.opencv.core.Point(point2Ds[38].x, point2Ds[38].y));//top mouth corner
        objPoints.add(new org.opencv.core.Point(point2Ds[32].x, point2Ds[32].y));//bottom mouth corner
        objPoints.add(new org.opencv.core.Point(point2Ds[11].x, point2Ds[11].y));//top left chin
        objPoints.add(new org.opencv.core.Point(point2Ds[13].x, point2Ds[13].y));//top right chin


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
        return rx - 180;
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
