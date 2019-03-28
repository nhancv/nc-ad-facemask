package com.nhancv.facemask.m3d;
import android.graphics.Rect;
import android.os.Handler;
import android.util.Log;

import com.nhancv.facemask.FaceLandmarkListener;
import com.nhancv.facemask.m3d.transformation.*;
import com.tzutalin.dlib.VisionDetRet;

import org.andresoviedo.android_3d_model_engine.model.Camera;

import java.util.ArrayList;
import java.util.List;

public class M3DPosController implements FaceLandmarkListener {

    private final static String TAG = M3DPosController.class.getName();
    private M3DSurfaceView surfaceView;
    private M3DRenderer renderer;
    private List<ObjectTransformation> listObjectTransformation;
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
    }
    private int getHeight(VisionDetRet ret,float resizeRatio)
    {
        int h =(int) (Math.abs(ret.getBottom()-ret.getTop())*resizeRatio);
        return h;
    }
    private int getWidth(VisionDetRet ret,float resizeRatio)
    {
        int w =(int) (Math.abs(ret.getRight()-ret.getLeft())*resizeRatio);
        return w;
    }
    int i = -1;
    int curArea = 22500;
    @Override
    public void landmarkUpdate(List<VisionDetRet> visionDetRetList) {
        Log.d(TAG, "landmarkUpdate: " + visionDetRetList.size());
        //Translation
        float ratio = 1.0f;
        handler = new Handler();
        listObjectTransformation = new ArrayList<>();
        for (final VisionDetRet ret : visionDetRetList) {

            float resizeRatio = 1.0f;
            Rect bounds = new Rect();
            bounds.left = (int) (ret.getLeft() * resizeRatio);
            bounds.top = (int) (ret.getTop() * resizeRatio);
            bounds.right = (int) (ret.getRight() * resizeRatio);
            bounds.bottom = (int) (ret.getBottom() * resizeRatio);
            int h = getHeight(ret,resizeRatio);
            int w = getWidth(ret,resizeRatio);
            //assume: (x,y) : (
            //translateWorld(100,0);

            //Scale(w,h);
            // Draw landmark
          /*  ArrayList<Point> landmarks = ret.getFaceLandmarks();
            for (Point point : landmarks) {
                int pointX = (int) (point.x * resizeRatio);
                int pointY = (int) (point.y * resizeRatio);
            }*/
            ObjectTransformation objectTransformation;
            i = i *-1;
            if(i==1) {
               // Translate(0, 1, 0);
                //degrees
                Rotation rotation = new Rotation(0,1,0);
                Translation translation = new Translation(0,0,0);
                //Scale scale = new Scale(1,1,1);
                //objectTransformation = new ObjectTransformation(rotation,scale,translation);
                //Scale(160,160);
                //Scale(1, 1, 0.9f);
                objectTransformation = new ObjectTransformationBuilder().setRotation(rotation)
                                                                        .setTranslation(translation)
                                                                        .build();
            }
            else{
                //Scale(150,150);
                //Translate(0, -1, 0);
                Rotation rotation = new Rotation(0,-1,0);
                Translation translation = new Translation(0,0,0);
                //Scale scale = new Scale(1,1,1);
                objectTransformation = new ObjectTransformationBuilder().setRotation(rotation)
                        .setTranslation(translation)
                        .build();
                //Scale(1, 1, 1/0.9f);

            }
            if(objectTransformation!=null)
                listObjectTransformation.add( objectTransformation);//add each tranformation for each object
        }
        renderer.setObjectTransformationList(listObjectTransformation);
        requestRender();
    }
     public void Scale(int w, int h) {
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
    }
    public void requestRender() {
        this.surfaceView.requestRender();
    }
/*    public void Translate(float x,float y,float z) {
        renderer.setTranslate(x,y,z);
    }
    public void Rotate(float angle,float x, float y, float z) {
        renderer.setRotate(angle,x,y,z);
    }
    public void Scale(float x, float y, float z) {
        renderer.setScale(x,y,z);
    }*/
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
