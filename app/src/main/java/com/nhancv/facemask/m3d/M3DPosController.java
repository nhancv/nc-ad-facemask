package com.nhancv.facemask.m3d;

import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;

import com.nhancv.facemask.m2d.SolvePNP;
import com.nhancv.facemask.m3d.transformation.ObjectTransformation;
import com.nhancv.facemask.m3d.transformation.RealTimeRotation;
import com.nhancv.facemask.tracking.FaceLandmarkListener;

import org.opencv.core.MatOfPoint3f;

import java.util.ArrayList;
import java.util.List;

import zeusees.tracking.Face;


public class M3DPosController implements FaceLandmarkListener {

    private final static String TAG = M3DPosController.class.getName();
    private M3DSurfaceView surfaceView;
    private M3DRenderer renderer;
    private Face face;
    private Rect bounds;
    private int bmWidth;
    private int bmHeight;
    private List<ObjectTransformation> listObjectTransformation;
    private int surfaceWidth;
    private int surfaceHeight;
    private int centerX;
    private int centerY;
    RealTimeRotation realTimeRotation = RealTimeRotation.getInstance();
    //assume value
    private float focal_length;
    private float knownWidth = 5.52f;//inch
    private float default_distance = 40f; //default distance for view with a width 5.52inch
    MatOfPoint3f objPointMat;


    //solvePNP object
    SolvePNP solvePNP;

    public float distance_to_camera(float knownWidth, float focalLength, float perWidth) {
        return knownWidth * focalLength / perWidth;
    }

    Handler handler = new Handler();

    public M3DPosController(M3DSurfaceView surfaceView) {
        this.surfaceView = surfaceView;//receive the current surface view
        this.renderer = surfaceView.getModelRenderer();
        this.listObjectTransformation = new ArrayList<ObjectTransformation>();
        this.bounds = new Rect();
        this.focal_length = realTimeRotation.getFocalLength();

    }


    private float getX(float x) {
        return x / bmWidth * this.surfaceWidth;
    }

    private float faceCenterX(int left, int right) {
        return (left + right) / 2;
    }

    private float faceCenterY(int top, int bottom) {
        return (top + bottom) / 2;
    }

    private float objX(int centerX, float x) {
        return x - centerX; //x coord is normal
    }

    private float objY(int centerY, float y) {

        return centerY - y; //y coord is opposite
    }

    private float getY(float y) {
        return y / bmHeight * this.surfaceHeight;
    }

    private float ratioDepth(float depth) {
        float ratio = (default_distance - depth) / default_distance;
        return ratio;
        //return default_distance/depth;
    }

    private float headWidth(ArrayList<Point> landmarks) {
        float dx = Math.abs(landmarks.get(16).x - landmarks.get(0).x);
        float dy = Math.abs(landmarks.get(16).y - landmarks.get(0).y);
        float result = (float) Math.hypot(dx, dy);
        return result;
    }

    @Override
    public void landmarkUpdate(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix) {
        this.face = face;
        this.bmWidth = previewWidth;
        this.bmHeight = previewHeight;
        this.surfaceWidth = surfaceView.getCurrentWidth();
        this.surfaceHeight = surfaceView.getCurrentHeight();
        this.centerX = this.surfaceWidth / 2;
        this.centerY = this.surfaceHeight / 2;

        if (face == null) return;

        //Translation
        float ratio = 1.0f;
        handler = new Handler();
        listObjectTransformation = new ArrayList<>();

        renderer.setObjectVisible(false);

//        for (final Face ret : visionDetRetList) {
//            // TODO: 4/13/19 Need convert from ret.landmarks
//            ArrayList<Point> landmarks = new ArrayList<>();
//
//            renderer.setObjectVisible(true);
//
//            bounds.left = (int) (getX(ret.left));
//            bounds.top = (int) (getY(ret.top));
//            bounds.right = (int) getX(ret.right);
//            bounds.bottom = (int) getY(ret.bottom);
//            //get face width and height
//
//            //get the center of face
//            float centerFaceX = faceCenterX(bounds.left, bounds.right);
//            float centerFaceY = faceCenterY(bounds.top, bounds.bottom);
//            //convert from coord arcooding to center
//            //the current position we get is pixels
//            float objX = objX(centerX, centerFaceX) / 1000;
//            float objY = objY(centerY, centerFaceY) / 1000;
//            float distance = headWidth(landmarks);
//
//            float head_distance = distance_to_camera(knownWidth, focal_length, distance);//calculate the head distance
//
//            //the ratio of our detected face and the default distancnce
//            float objZ = ratioDepth(head_distance);
//
//            Log.d("M3DPos", "" + head_distance);
//            ObjectTransformation objectTransformation;
//            //Using Sovle PNP
//            MatOfPoint2f imagePoints = this.get5ValidPoint(landmarks);
//            this.rotationVector = new Mat();
//            this.translationVector = new Mat();
//            Calib3d.solvePnP(this.objPointMat, imagePoints, this.camMatrix, this.distCoeffs, this.rotationVector, this.translationVector);
//
//            double[] rx = this.rotationVector.get(0, 0);
//            double[] ry = this.rotationVector.get(1, 0);
//            double[] rz = this.rotationVector.get(2, 0);
//            Log.d(TAG, "Radian" + rx + "," + ry + "," + rz);
//            float dx = rotationHelper.normalizeRange((float) rx[0], 10);
//            float dy = rotationHelper.normalizeRange((float) ry[0], 45);
//            float dz = rotationHelper.normalizeRange((float) rz[0], 45);
//            Log.d(TAG, "Degree" + dx + "," + dy + "," + dz);
//
//            Rotation rotation = new Rotation(dx, dy, dz);//rotationValues[curRotationIdx][0],rotationValues[curRotationIdx][1],rotationValues[curRotationIdx][2]);
//            Log.d("M3DPositionController", "x" + objX + ",y" + objY + ",z" + objZ);
//            //
//            MatOfPoint3f projectPoints = this.getProjectPoints();
//            MatOfPoint2f noseEndPoints = new MatOfPoint2f();
//            Mat jacobian = new Mat();
//            Calib3d.projectPoints(projectPoints, rotationVector, translationVector, camMatrix, distCoeffs, noseEndPoints, jacobian);
//
//            Translation translation = new Translation(objX, objY, objZ);//translate back our scale will base on z
//            Scale scale = new Scale(5, 5, 3);//scale obj model
//
//
//            objectTransformation = new ObjectTransformationBuilder().setRotation(rotation)
//                    .setTranslation(translation).setScale(scale).build();
//
//            //Scale scale = new Scale()
//            if (objectTransformation != null)
//                listObjectTransformation.add(objectTransformation);//add each tranformation for each object
//            this.rotationVector.release();//release rotation vector
//            this.translationVector.release();//release translation vector
//        }

        renderer.setObjectTransformationList(listObjectTransformation);

        requestRender();
    }


    public void requestRender() {
        this.surfaceView.requestRender();
    }


}
