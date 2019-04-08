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
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
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
    RealTimeRotation realTimeRotation = RealTimeRotation.getInstance();
    //assume value
    private float focal_length;
    private float knownWidth = 5.52f;//inch
    private float default_distance = 40f; //default distance for view with a width 5.52inch
    //private float[][] rotationValues = new float[][]{{2.0f,-8.0f,-8.0f},{-2.0f,-8.0f,-8.0f},{-2.0f,-6.98916693837733f,-8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{2.0f,-8.0f,-8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{-2.0f,-7.264556224554855f,-8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{2.0f,-8.0f,-8.0f},{-2.0f,8.0f,8.0f},{2.0f,-8.0f,-8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{2.0f,-3.0871686980904296f,8.0f},{2.0f,1.7011402330623662f,8.0f},{-2.0f,-1.8391627149889804f,-8.0f},{-2.0f,8.0f,8.0f},{-2.0f,-3.909168825358995f,-8.0f},{-2.0f,-0.47076291487443556f,-8.0f},{-2.0f,-1.6972481483928397f,-8.0f},{-2.0f,-0.888752382697103f,2.0204232196734426f},{0.09294320784496717f,8.0f,-8.0f},{0.5185352743723719f,8.0f,-3.013068984482941f},{1.5432515637469228f,8.0f,-1.7924925544614636f},{0.9459607065008026f,8.0f,1.8783751174301568f},{0.18049941569904734f,8.0f,-1.10965537483482f},{-2.0f,8.0f,-5.012257280556295f},{-1.2225007094931188f,8.0f,-1.5297730562458616f},{2.0f,-8.0f,8.0f},{2.0f,-8.0f,8.0f},{2.0f,-8.0f,8.0f},{-2.0f,8.0f,-8.0f},{2.0f,-8.0f,8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,8.0f,-8.0f},{2.0f,-8.0f,8.0f},{-2.0f,8.0f,1.0806436635924463f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,2.4858384377934306f},{-2.0f,8.0f,-5.238692282342317f},{-2.0f,8.0f,-8.0f},{-2.0f,-1.3734690207702684f,-8.0f},{-2.0f,-0.5970904300361904f,-8.0f},{-1.7149085169661618f,8.0f,8.0f},{-2.0f,3.635767728051853f,-8.0f},{-2.0f,6.560222006717219f,-8.0f},{-2.0f,6.35514417824226f,-8.0f},{-2.0f,3.1091406595337565f,-8.0f},{-2.0f,3.2213111651346447f,-8.0f},{-2.0f,3.1829107231596456f,-8.0f},{1.381905601327157f,8.0f,8.0f},{2.0f,-1.0683878624394558f,8.0f},{-2.0f,0.9910363944425216f,-8.0f},{-2.0f,0.031717450350935386f,-8.0f},{-2.0f,1.200698669818641f,-8.0f},{-2.0f,-8.0f,-8.0f},{1.1110276275612583f,8.0f,8.0f},{-1.2028870633195967f,8.0f,8.0f},{-2.0f,-3.990102964970794f,-8.0f},{2.0f,5.206227453003833f,8.0f},{0.32069785778618415f,-8.0f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,3.9804868068056223f,-8.0f},{2.0f,2.3639647228813376f,-1.0719896824186128f},{2.0f,5.456104143258348f,8.0f},{2.0f,4.2905125525835786f,8.0f},{2.0f,-0.19317012035615452f,8.0f},{2.0f,1.5075387469286063f,8.0f},{-2.0f,-2.4551119815128146f,-8.0f},{1.2711326096422222f,-8.0f,-8.0f},{-1.7195708894707473f,-8.0f,-8.0f},{1.2828797106216767f,-8.0f,-8.0f},{-2.0f,8.0f,8.0f},{1.3685401167203772f,-8.0f,-8.0f},{2.0f,8.0f,8.0f},{-2.0f,-4.176164725490096f,-8.0f},{-2.0f,-8.0f,-8.0f},{-2.0f,-0.5441530153701247f,-8.0f},{2.0f,2.123626146257871f,8.0f},{-2.0f,-1.2097425581512666f,-8.0f},{-2.0f,-0.2969791372255661f,-8.0f},{-2.0f,-0.9215856767461675f,-8.0f},{-2.0f,-1.6272817043742953f,-8.0f},{-2.0f,0.7449242234746406f,-8.0f},{-2.0f,0.8073578015526386f,-8.0f},{0.4057040970365224f,8.0f,4.394132023302076f},{-2.0f,8.0f,-8.0f},{-2.0f,7.248601672027229f,-8.0f},{-2.0f,0.8312738443951229f,-8.0f},{-2.0f,6.237087630224134f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,-8.0f,-7.988582750863942f},{-2.0f,3.117413886315151f,-8.0f},{-2.0f,-8.0f,-8.0f},{-2.0f,0.7658355962733719f,-8.0f},{-2.0f,0.9727840843491128f,-8.0f},{-2.0f,1.0028207585179005f,-8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{-2.0f,-2.6131472032183622f,-8.0f},{-2.0f,-3.592840655363776f,-8.0f},{-2.0f,-4.2114791241695615f,-8.0f},{-2.0f,8.0f,8.0f},{0.7858116042812607f,-8.0f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,-3.978454515630202f,-8.0f},{-2.0f,-2.823632871415966f,-8.0f},{-2.0f,-5.111404875809552f,-8.0f},{2.0f,8.0f,8.0f},{-2.0f,8.0f,5.44570853203194f},{-2.0f,-8.0f,-3.1344821441797044f},{-2.0f,8.0f,5.199295856841565f},{-2.0f,8.0f,4.760343127852119f},{-2.0f,8.0f,6.723818359955355f},{-2.0f,8.0f,-4.905242497557879f},{-2.0f,8.0f,-2.5308229956679074f},{-2.0f,8.0f,1.4630540340605374f},{-2.0f,8.0f,7.215133566424465f},{0.2538362732093479f,8.0f,4.385960572435208f},{-2.0f,8.0f,-8.0f},{2.0f,-8.0f,8.0f},{2.0f,-8.0f,8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,8.0f,-8.0f},{2.0f,-8.0f,8.0f},{1.477882592707767f,8.0f,-0.3693805956497251f},{-1.3112829784748306f,8.0f,-8.0f},{-0.8739344638513163f,8.0f,-8.0f},{-2.0f,1.1236507851880233f,2.979235289616052f},{-2.0f,-0.7393369798238604f,-8.0f},{-2.0f,4.88594736287391f,-8.0f},{-2.0f,-8.0f,-8.0f},{2.0f,-5.016779465672125f,8.0f},{2.0f,8.0f,8.0f},{2.0f,8.0f,8.0f},{-2.0f,2.583936565458074f,-8.0f},{0.7815303354172467f,8.0f,8.0f},{1.7572666268171924f,8.0f,8.0f},{-2.0f,-0.920922828491573f,-8.0f},{-0.6586968080639573f,8.0f,8.0f},{-2.0f,1.4295813131489814f,-8.0f},{0.12493309105148998f,8.0f,8.0f},{-2.0f,0.716512828349051f,-8.0f},{0.7955789713084414f,8.0f,8.0f},{0.10885857240765841f,8.0f,8.0f},{-1.8727365446853879f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{-2.0f,-6.279259949485959f,-8.0f},{-2.0f,8.0f,6.189186037465528f},{-2.0f,8.0f,6.018519307362721f},{-2.0f,8.0f,3.209239098096519f},{-2.0f,8.0f,3.654110959754226f},{-2.0f,8.0f,2.7656237586296313f},{-2.0f,8.0f,2.3965243183158185f},{-2.0f,8.0f,2.522231668673871f},{-2.0f,8.0f,2.1022094596413297f},{-2.0f,8.0f,3.8412269384745654f},{-2.0f,8.0f,3.1215887457898113f},{-2.0f,8.0f,1.548010228333149f},{-2.0f,8.0f,1.6686802720902156f},{-2.0f,8.0f,1.697027312940421f},{-2.0f,8.0f,4.6196528363996245f},{-2.0f,-7.35349447046689f,-3.39141868015207f},{-2.0f,8.0f,8.0f},{-2.0f,-3.4845541781993137f,-8.0f},{2.0f,1.9017159673771271f,5.5328283440645505f},{2.0f,8.0f,8.0f},{2.0f,6.734680516619816f,8.0f},{-2.0f,-6.852099424024109f,-8.0f},{2.0f,0.5325991250271306f,3.7565405662750306f},{-2.0f,0.1827427385585769f,-7.169970262216096f},{-2.0f,-0.09673116253834005f,-8.0f},{-2.0f,-5.661855760007758f,-8.0f},{-2.0f,-5.39797115854703f,-8.0f},{-2.0f,-4.431363421961753f,-8.0f},{2.0f,-0.7226020107130378f,5.570939027847648f},{2.0f,0.02451842745216556f,6.486068210506345f},{-1.653198514954562f,-8.0f,-8.0f},{2.0f,-1.7321923193335578f,8.0f},{1.288055165969684f,-8.0f,-5.875718164225886f},{-2.0f,-6.725942734405469f,-8.0f},{-2.0f,-6.237793169925512f,-8.0f},{-2.0f,-5.784356422281522f,-8.0f},{-2.0f,-6.648853309207393f,-8.0f},{-2.0f,-7.191018209866615f,-8.0f},{-2.0f,-4.061994822435804f,-8.0f},{2.0f,-1.269026968558412f,8.0f},{-0.3741230781283716f,-8.0f,-8.0f},{-2.0f,1.9504420101993811f,-8.0f},{-2.0f,8.0f,8.0f},{2.0f,3.8814999439773334f,8.0f},{-2.0f,-3.246881407451331f,-8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{0.7531176383369133f,-8.0f,-8.0f},{-2.0f,8.0f,7.816952711637256f},{-2.0f,0.5721280713473537f,-8.0f},{-2.0f,-8.0f,-4.8516835011626185f},{-2.0f,0.10104851820591194f,-8.0f},{-2.0f,1.1517325954030826f,-8.0f},{-1.5448588127318512f,-8.0f,-8.0f},{2.0f,-7.741308078590128f,8.0f},{-2.0f,7.5649929483941705f,-8.0f},{-2.0f,4.432751143007755f,-8.0f},{-2.0f,3.4889170894331722f,-8.0f},{-2.0f,2.5753818969419253f,-8.0f},{-2.0f,3.8800839296979737f,-8.0f},{-2.0f,2.9852056253232044f,-8.0f},{-2.0f,3.39016492746404f,-8.0f},{-2.0f,2.334645801179314f,-8.0f},{-2.0f,3.7227532296829953f,-8.0f},{-2.0f,6.39089222714356f,-8.0f},{-2.0f,5.87518275003219f,-8.0f},{-2.0f,3.5616748043442623f,-8.0f},{-2.0f,5.2140149332894845f,-8.0f},{2.0f,8.0f,6.806695506537729f},{-2.0f,6.664208372501647f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,0.11786351994601472f,-8.0f},{-2.0f,-1.5464931471998435f,-8.0f},{-2.0f,-1.069004551036138f,-8.0f},{-2.0f,8.0f,8.0f},{2.0f,-8.0f,-8.0f},{2.0f,-8.0f,-8.0f},{1.7969932490370604f,-8.0f,-8.0f},{2.0f,-8.0f,-8.0f},{2.0f,-8.0f,-8.0f},{2.0f,-8.0f,-8.0f},{2.0f,1.6672428942192905f,8.0f},{-2.0f,-6.27523107531775f,-8.0f},{-2.0f,-8.0f,-8.0f},{-2.0f,-8.0f,-8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,3.4046680845960395f},{-2.0f,8.0f,5.408427606082087f},{-2.0f,8.0f,5.353435693726394f},{-2.0f,8.0f,3.948932143594581f},{-2.0f,8.0f,5.759727642902459f},{-2.0f,8.0f,6.059121950924585f},{-2.0f,-8.0f,-8.0f},{-2.0f,-8.0f,6.394525085760247f},{-2.0f,-8.0f,8.0f},{-2.0f,-8.0f,8.0f},{-2.0f,8.0f,-1.8306952763705067f},{-2.0f,-8.0f,8.0f},{-2.0f,8.0f,-0.6547939038340232f},{-2.0f,8.0f,5.153637547804668f},{-2.0f,8.0f,6.148429033387529f},{-2.0f,8.0f,-2.038453845972041f},{-2.0f,8.0f,0.8466079631267334f},{-2.0f,8.0f,7.059695927185671f},{-2.0f,8.0f,4.400980256542969f},{-2.0f,8.0f,3.8175307347459237f},{-2.0f,8.0f,1.2248303174166504f},{-2.0f,8.0f,4.659151780588488f},{-2.0f,8.0f,4.113570413820135f},{-2.0f,8.0f,6.511165888522446f},{-2.0f,8.0f,3.827940953542105f},{-2.0f,8.0f,0.31312035394452276f},{-2.0f,8.0f,4.659436918236376f},{-2.0f,8.0f,-2.09887500586748f},{-2.0f,8.0f,-7.090336871398167f},{-2.0f,8.0f,4.175023209060915f},{-2.0f,-6.33205370901053f,-8.0f},{-2.0f,-7.570769290401844f,-8.0f},{-2.0f,-2.8553456458777857f,-8.0f},{-2.0f,0.739457568975147f,-8.0f},{-2.0f,1.2359022904808643f,-8.0f},{-2.0f,2.6713640174961295f,-8.0f},{0.5259826334486944f,8.0f,8.0f},{-2.0f,0.9067004087337064f,-8.0f},{2.0f,8.0f,8.0f},{2.0f,8.0f,8.0f},{2.0f,8.0f,8.0f},{-2.0f,-8.0f,-8.0f},{2.0f,8.0f,8.0f},{2.0f,8.0f,8.0f},{-1.9411338917939824f,-8.0f,-8.0f},{-1.9411338917939824f,-8.0f,-8.0f},{2.0f,8.0f,8.0f},{2.0f,8.0f,8.0f},{-1.5201429886668167f,-8.0f,-8.0f},{2.0f,8.0f,8.0f},{2.0f,8.0f,8.0f},{2.0f,8.0f,8.0f},{1.6304292979235553f,8.0f,8.0f},{0.8683705353850222f,8.0f,8.0f},{-0.23584828917995487f,8.0f,8.0f},{-2.0f,-0.9478898345104082f,-8.0f},{0.7777856263478083f,8.0f,8.0f},{-0.09929567909253503f,-8.0f,-8.0f},{-0.2759938822498333f,8.0f,8.0f},{0.6298681147922269f,8.0f,8.0f},{-2.0f,-0.2935258592156712f,-8.0f},{-2.0f,8.0f,8.0f},{-2.0f,-1.687885319405325f,-8.0f},{-2.0f,-2.0620121553860438f,-8.0f},{-0.9226494496155326f,-8.0f,-8.0f},{-2.0f,-0.24708601101810346f,-8.0f},{-2.0f,1.78561667815133f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,6.106626555188881f,-8.0f},{-2.0f,7.8662643643105135f,-8.0f},{-2.0f,5.024289762213308f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,5.131446286526402f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,6.154479205738817f,-8.0f},{-2.0f,3.9746451874588526f,-8.0f},{-2.0f,2.1039089732604985f,-8.0f},{-2.0f,8.0f,-8.0f},{-2.0f,7.19034594849474f,-8.0f},{-2.0f,3.594011263294903f,-8.0f},{-2.0f,0.6292687506259786f,-8.0f},{2.0f,-8.0f,-8.0f},{0.22609130803699135f,-8.0f,-8.0f},{2.0f,-8.0f,8.0f},{-2.0f,-1.912818954171422f,-8.0f},{-2.0f,-3.6251074879162344f,-8.0f},{-2.0f,-4.769779873306646f,-8.0f},{-2.0f,-3.099383968178697f,-8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{2.0f,-8.0f,-8.0f},{2.0f,-8.0f,-8.0f},{-2.0f,-4.351639675776721f,-8.0f},{-2.0f,8.0f,8.0f},{2.0f,-8.0f,-8.0f},{2.0f,-8.0f,-8.0f},{-2.0f,-7.452318589167493f,-8.0f},{2.0f,-2.2190828342821134f,8.0f},{2.0f,1.0257854560784065f,8.0f},{2.0f,0.8599172225284067f,8.0f},{2.0f,2.433005380497849f,8.0f},{2.0f,4.1583246845940325f,8.0f},{-2.0f,-8.0f,-8.0f},{-2.0f,-7.070537822511677f,-8.0f},{-2.0f,-8.0f,-8.0f},{-2.0f,-4.789861097582709f,-8.0f},{-2.0f,-3.5194473240457964f,-8.0f},{-2.0f,-4.2812227889017755f,-8.0f},{2.0f,-8.0f,-8.0f},{2.0f,-8.0f,-8.0f},{-2.0f,8.0f,8.0f},{-2.0f,8.0f,8.0f},{2.0f,-8.0f,-8.0f}};
    //private int curRotationIdx = 0;
    MatOfPoint3f objPointMat;
    private Mat camMatrix;
    private MatOfDouble distCoeffs;
    private Mat rotationVector;
    private Mat translationVector;
    private RotationHelper rotationHelper = new RotationHelper();
    public void setUpDistCoeff() {
        Mat coeffMat =new Mat();
        Mat.zeros(4,1,CvType.CV_64FC1).copyTo(coeffMat);
        distCoeffs = new MatOfDouble(coeffMat);
    }
    public void releaseMat(){
        if(camMatrix!=null){
            camMatrix.release();
        }
        if(rotationVector!=null){
            rotationVector.release();
        }
        if(translationVector!=null){
            translationVector.release();
        }
        if(objPointMat!=null)
        {
            objPointMat.release();
        }
        if(distCoeffs!=null){
            distCoeffs.release();
        }
    }
    public float distance_to_camera(float knownWidth, float focalLength,float perWidth)
    {
        return knownWidth*focalLength/perWidth;
    }

    Handler handler = new Handler();
    public M3DPosController(M3DSurfaceView surfaceView) {
        this.surfaceView = surfaceView;//receive the current surface view
        this.renderer = surfaceView.getModelRenderer();
        this.listObjectTransformation = new ArrayList<ObjectTransformation>();
        this.bounds = new Rect();
        setUpDistCoeff();
        this.focal_length = realTimeRotation.getFocalLength();

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
    private MatOfPoint2f get5ValidPoint( ArrayList<Point> landmarks ){
        List<org.opencv.core.Point> objPoints = new ArrayList<org.opencv.core.Point>();
        MatOfPoint2f imagePoints = new MatOfPoint2f();
        objPoints.add(new org.opencv.core.Point(landmarks.get(30).x,landmarks.get(30).y));
        objPoints.add(new org.opencv.core.Point(landmarks.get(8).x,landmarks.get(8).y));
        objPoints.add(new org.opencv.core.Point(landmarks.get(36).x,landmarks.get(36).y));
        objPoints.add(new org.opencv.core.Point(landmarks.get(46).x,landmarks.get(46).y));
        objPoints.add(new org.opencv.core.Point(landmarks.get(48).x,landmarks.get(48).y));
        objPoints.add(new org.opencv.core.Point(landmarks.get(54).x,landmarks.get(54).y));
        imagePoints.fromList(objPoints);
        return imagePoints;
    }
    private MatOfPoint3f getProjectPoints(){
        MatOfPoint3f projectPoints = new MatOfPoint3f();
        List<org.opencv.core.Point3> objPoints = new ArrayList<org.opencv.core.Point3>();
        objPoints.add(new org.opencv.core.Point3(0f,0f,1000.0f) );
        projectPoints.fromList(objPoints);
        return projectPoints;
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
            //Using Sovle PNP
            MatOfPoint2f imagePoints = this.get5ValidPoint(landmarks);
            this.rotationVector = new Mat();
            this.translationVector = new Mat();
            Calib3d.solvePnP(this.objPointMat,imagePoints,this.camMatrix,this.distCoeffs,this.rotationVector,this.translationVector);

            double[] rx =this.rotationVector.get(0,0);
            double[] ry = this.rotationVector.get(1,0);
            double[] rz = this.rotationVector.get(2,0);
            Log.d(TAG,"Radian"+rx+","+ry+","+rz);
            float dx = rotationHelper.normalizeRange((float)rx[0],10);
            float dy = rotationHelper.normalizeRange((float) ry[0],45);
            float dz = rotationHelper.normalizeRange((float) rz[0],45);
            Log.d(TAG,"Degree"+dx+","+dy+","+dz);

            Rotation rotation = new Rotation(dx, dy, dz);//rotationValues[curRotationIdx][0],rotationValues[curRotationIdx][1],rotationValues[curRotationIdx][2]);
            Log.d("M3DPositionController","x"+objX+ ",y"+objY+",z"+objZ);
            //
            MatOfPoint3f projectPoints = this.getProjectPoints();
            MatOfPoint2f noseEndPoints = new MatOfPoint2f();
            Mat jacobian = new Mat();
            Calib3d.projectPoints(projectPoints,rotationVector,translationVector,camMatrix,distCoeffs,noseEndPoints,jacobian);

            Translation translation = new Translation(objX,objY,objZ);//translate back our scale will base on z
            Scale scale = new Scale(5,5,3);//scale obj model


            objectTransformation = new ObjectTransformationBuilder().setRotation(rotation)
                                                                    .setTranslation(translation).setScale(scale).build();

            //Scale scale = new Scale()
            if(objectTransformation!=null)
                listObjectTransformation.add( objectTransformation);//add each tranformation for each object
            this.rotationVector.release();//release rotation vector
            this.translationVector.release();//release translation vector
        }

        renderer.setObjectTransformationList(listObjectTransformation);

        requestRender();
    }


    public void requestRender() {
        this.surfaceView.requestRender();
    }


}
