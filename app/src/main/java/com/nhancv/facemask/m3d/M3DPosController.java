package com.nhancv.facemask.m3d;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.util.Log;

import com.nhancv.facemask.m2d.SolvePNP;
import com.nhancv.facemask.m3d.transformation.ObjectTransformation;
import com.nhancv.facemask.m3d.transformation.ObjectTransformationBuilder;
import com.nhancv.facemask.m3d.transformation.RealTimeRotation;
import com.nhancv.facemask.m3d.transformation.Rotation;
import com.nhancv.facemask.m3d.transformation.Scale;
import com.nhancv.facemask.m3d.transformation.Translation;
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
    private Rect faceRect;
    private PointF[] point2Ds;
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
    SolvePNP solvePNP = new SolvePNP();

    public float distance_to_camera(float knownWidth, float focalLength, float perWidth) {
        return knownWidth * focalLength / perWidth;
    }

    Handler handler = new Handler();

    public M3DPosController(M3DSurfaceView surfaceView) {
        this.surfaceView = surfaceView;//receive the current surface view
        this.renderer = surfaceView.getModelRenderer();
        this.listObjectTransformation = new ArrayList<ObjectTransformation>();
        this.faceRect = new Rect();
        this.point2Ds = new PointF[106];

        for (int i = 0; i < 106; i++) {
            this.point2Ds[i] = new PointF(0, 0);
        }

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


    @Override
    public void landmarkUpdate(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix) {
        this.solvePNP.initialize();

        this.face = face;
        this.bmWidth = previewWidth; //320
        this.bmHeight = previewHeight;//
        this.surfaceWidth = surfaceView.getCurrentWidth();
        this.surfaceHeight = surfaceView.getCurrentHeight();
        //Object center position
        this.centerX = this.surfaceWidth/2;
        this.centerY = this.surfaceHeight/2;
        float hRatio = this.surfaceHeight*1.0f/this.bmHeight;
        float wRatio = this.surfaceWidth*1.0f/this.bmWidth;
        Log.d(TAG,"w,h"+this.bmWidth+","+bmHeight);
        renderer.setObjectVisible(false);

        if (face == null) return;
        renderer.setObjectVisible(true);

        //Translation
        float ratio = 1.0f;
        handler = new Handler();
        listObjectTransformation = new ArrayList<>();

        faceRect.set(previewHeight - face.left, face.top, previewHeight - face.right, face.bottom);
        for (int i = 0; i < 106; i++) {
            point2Ds[i].set(face.landmarks[i * 2], face.landmarks[i * 2 + 1]);
        }
        solvePNP.setUpLandmarks(point2Ds);

        solvePNP.solvePNP();

        Rotation rotation = new Rotation(solvePNP.getRx(),solvePNP.getRy(),solvePNP.getRz());
        Log.d(TAG,"Rotation values: "+rotation.toString());
//        Translation translation = new Translation(0,0,solvePNP.getTz());



        //get the center of face
        float centerFaceX =faceCenterX(faceRect.left,faceRect.right)*wRatio;
        float centerFaceY =faceCenterY(faceRect.bottom,faceRect.top)*hRatio;


        Log.d(TAG,"Point"+"("+centerFaceX+","+centerFaceY+")");
        //convert from coord arcooding to center
        //the current position we get is pixels
        float objX = objX(centerX, centerFaceX)/ 1000;
        float objY = objY(centerY, centerFaceY)/ 1000;
        float distance = face.width;

        float head_distance = distance_to_camera(knownWidth, focal_length, distance);//calculate the head distance
//
        //the ratio of our detected face and the default distancnce
        float objZ = ratioDepth(head_distance);
//
        Log.d("M3DPos", "" + head_distance);
        ObjectTransformation objectTransformation;
        Log.d("M3DPositionController","x"+objX+ ",y"+objY+",z"+objZ);
        Log.d(TAG,"translation tx:"+solvePNP.getTx()+","+solvePNP.getTy()+","+solvePNP.getTz());

        //translate object to the face origina
        //using
        float tx = -objX;//solvePNP.getTx()/1000;
        float ty = 1+objY;
        float tz = 0;//(2000-solvePNP.getTz())/2000;
        Log.d(TAG,"Translation Value"+tx+","+ty+","+tz);
        Translation translation = new Translation(tx,ty,tz);

        Scale scale = new Scale(5, 5, 1);//scale obj model
        //create an object transformation matrix
        objectTransformation = new ObjectTransformationBuilder().setRotation(rotation)
                    .setTranslation(translation).setScale(scale).build();

        if (objectTransformation != null)
            listObjectTransformation.add(objectTransformation);
        //update tranformation list to render
        renderer.setObjectTransformationList(listObjectTransformation);

        requestRender();
    }


    public void requestRender() {
        this.surfaceView.requestRender();
    }


}
