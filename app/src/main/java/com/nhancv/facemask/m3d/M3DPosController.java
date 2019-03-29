package com.nhancv.facemask.m3d;
import android.annotation.SuppressLint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import com.nhancv.facemask.FaceLandmarkListener;
import com.nhancv.facemask.m3d.transformation.*;
import com.tzutalin.dlib.VisionDetRet;

import org.andresoviedo.android_3d_model_engine.model.Camera;

import java.util.ArrayList;
import java.util.List;

import hugo.weaving.DebugLog;

public class M3DPosController implements FaceLandmarkListener {

    private final static String TAG = M3DPosController.class.getName();
    private M3DSurfaceView surfaceView;
    private M3DRenderer renderer;
    private List<VisionDetRet> visionDetRetList;
    private Rect bounds;
    private int bmWidth;
    private int bmHeight;
    private List<ObjectTransformation> listObjectTransformation;
    private int surfaceWidth;
    private int surfaceHeight;
    private int centerX;
    private int centerY;
    //assume value
    private float focal_length;
    private float knownWidth = 5.52f;//inch
    private float default_distance = 40f; //default distance for view with a width 5.52inch

    public float distance_to_camera(float knownWidth, float focalLength,float perWidth)
    {
        return knownWidth*focalLength/perWidth;
    }
/*    private float previousX1 = 0;
    private float previousY1 = 0;
    private float dx1 = 0;
    private float dy1 = 0;
    */
 /* final Runnable r = new Runnable() {
                @Override
                public void run() {

                    i = i *-1;
                    if(i==1) {
                        //Translate(3, 0, 0);
                        //degrees
                        // Rotate(3, 0, 1, 0);
                        Scale(160,160);

                        //Scale(0.9f, 1, 1);
                    }
                    else{
                        Scale(150,150);
                        //Translate(-3, 0, 0);
                        //Rotate(-3, 0, 1, 0);

                    }
                    requestRender();
                    handler.postDelayed(this, 1000);
                }
                //Scale(1/0.9f, 1, 1);
            };
            handler.postDelayed(r,1000);*/
    Handler handler = new Handler();
    public M3DPosController(M3DSurfaceView surfaceView) {
        this.surfaceView = surfaceView;//receive the current surface view
        this.renderer = surfaceView.getModelRenderer();
        this.listObjectTransformation = new ArrayList<ObjectTransformation>();
        this.bounds = new Rect();
        this.focal_length = 543.45f;//pixels
    }
    private int getHeight(VisionDetRet ret)
    {
        int h =(int) (Math.abs(ret.getBottom()-ret.getTop()));
        return h;
    }
    private int getWidth(VisionDetRet ret)
    {
        int w =(int) (Math.abs(ret.getRight()-ret.getLeft()));
        return w;
    }


    private float getX(float x) {
        return x/bmWidth * this.surfaceWidth;
    }
    private float faceCenterX(int left,int right)
    {
        return (left+right)/2;
    }
    private float faceCenterY(int top, int bottom)
    {
        return (top+bottom)/2;
    }
    private float objX(int centerX, float x){
        return x - centerX; //x coord is normal
    }
    private float objY(int centerY, float y){

        return centerY - y; //y coord is opposite
    }

    private float getY(float y) {
        return y/bmHeight * this.surfaceHeight;
    }
    private float ratioDepth(float depth)
    {
        float ratio = (default_distance - depth)/default_distance;
        return ratio;
        //return default_distance/depth;
    }

    private float headWidth(ArrayList<Point> landmarks){
        float dx = Math.abs(landmarks.get(16).x - landmarks.get(0).x);
        float dy = Math.abs(landmarks.get(16).y-landmarks.get(0).y);
        float result = (float)Math.hypot(dx,dy);
        return result;
    }
    @Override
    public void landmarkUpdate(List<VisionDetRet> visionDetRetList, int bmW, int bmH) {
        this.visionDetRetList = visionDetRetList;
        this.bmWidth = bmW;
        this.bmHeight = bmH;
        this.surfaceWidth = surfaceView.getCurrentWidth();
        this.surfaceHeight = surfaceView.getCurrentHeight();
        this.centerX = this.surfaceWidth/2;
        this.centerY = this.surfaceHeight/2;

        if (visionDetRetList == null) return;

        Log.d(TAG, "landmarkUpdate: " + visionDetRetList.size());
        //Translation
        float ratio = 1.0f;
        handler = new Handler();
        listObjectTransformation = new ArrayList<>();

        renderer.setObjectVisible(false);

        for (final VisionDetRet ret : visionDetRetList) {
            ArrayList<Point> landmarks = ret.getFaceLandmarks();
            renderer.setObjectVisible(true);

            bounds.left = (int) (getX(ret.getLeft()));
            bounds.top = (int) (getY(ret.getTop()));
            bounds.right = (int) getX(ret.getRight());
            bounds.bottom = (int) getY(ret.getBottom());
            //get face width and height

            //get the center of face
            float centerFaceX = faceCenterX(bounds.left,bounds.right);
            float centerFaceY = faceCenterY(bounds.top,bounds.bottom);
            //convert from coord arcooding to center
            //the current position we get is pixels
            float objX = objX(centerX,centerFaceX)/1000;
            float objY = objY(centerY,centerFaceY)/1000;
            float distance = headWidth(landmarks);

            float head_distance = distance_to_camera(knownWidth,focal_length,distance);//calculate the head distance

            //the ratio of our detected face and the default distancnce
            float objZ = ratioDepth(head_distance);

            Log.d("M3DPos",""+head_distance);
            ObjectTransformation objectTransformation;
            //
            Rotation rotation = new Rotation(0,0,0);
            Log.d("M3DPositionController","x"+objX+ ",y"+objY+",z"+objZ);
            //
            Translation translation = new Translation(objX,objY,objZ);//translate back our scale will base on z
            Scale scale = new Scale(5,5,5);//scale obj model

            //Scale scale = new Scale(1,1,1);
            //objectTransformation = new ObjectTransformation(rotation,scale,translation);
            //Scale(160,160);
            //Scale(1, 1, 0.9f);
            objectTransformation = new ObjectTransformationBuilder().setRotation(rotation)
                                                                    .setTranslation(translation).setScale(scale).build();
            //Scale scale = new Scale()
            if(objectTransformation!=null)
                listObjectTransformation.add( objectTransformation);//add each tranformation for each object
        }
        renderer.setObjectTransformationList(listObjectTransformation);
        requestRender();
    }

   /*  public void Scale(int w, int h) {
        //zoom factor is the ratio of previous face and current detected face
        int max = Math.max(this.renderer.getWidth(), this.renderer.getHeight()); //max of render vaule
        M3DSceneLoader scene = this.surfaceView.getScene(); //get current scene
        Camera camera = scene.getCamera();
        float preArea = curArea;
        curArea = w*h;
        float zoomFactor = 1 ;
        Log.d("M3DPosController", ""+zoomFactor);
        camera.MoveCameraZ(zoomFactor);
        //this.surfaceView.requestRender();
    }*/
    public void requestRender() {
        this.surfaceView.requestRender();
    }

/*    public void translateWorld(float x, float y){
        dx1 = x - previousX1;
        dy1 = y - previousY1;
        int max = Math.max(this.renderer.getWidth(), this.renderer.getHeight());

        M3DSceneLoader scene = this.surfaceView.getScene(); //get current scene
        Camera camera = scene.getCamera();
        //camera.translateCameraImpl(100, 100);
        dx1 = (float)(dx1 / max * Math.PI * 2);
        dy1 = (float)(dy1 / max * Math.PI * 2);
        camera.translateCamera(dx1,dy1);

        this.surfaceView.requestRender();
    }*/




}
