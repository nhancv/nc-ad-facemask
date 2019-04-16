package com.nhancv.facemask.m2d;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.nhancv.facemask.R;

import zeusees.tracking.Face;


public class M2DLandmarkView extends View {

    private static final String TAG = M2DLandmarkView.class.getSimpleName();

    private static final float MASK_SIZE_STANDARD_W = 300f;
    private static final float MASK_SIZE_STANDARD_H = 305f;

    private int ratioWidth = 0;
    private int ratioHeight = 0;
    private float offsetX = 0;
    private float offsetY = 0;
    private Face face;
    private Rect faceRect;
    private PointF[] point2Ds;
    private float[] visibleIndexes;
    private Paint faceLandmarkPaint;

    private int previewWidth;
    private int previewHeight;
    private int currentWidth;
    private int currentHeight;
    private Matrix scaleMatrix;

    private Bitmap leftEar;
    private Bitmap rightEar;
    private Bitmap nose;
    private Bitmap ear;
    private Matrix bmScaleMatrix;

    private float rx,ry,rz;

    public M2DLandmarkView(Context context) {
        this(context, null, 0, 0);
    }

    public M2DLandmarkView(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public M2DLandmarkView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public M2DLandmarkView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        faceLandmarkPaint = new Paint();
        faceLandmarkPaint.setColor(Color.GREEN);
        faceLandmarkPaint.setStrokeWidth(1);
        faceLandmarkPaint.setStyle(Paint.Style.FILL);

        faceRect = new Rect();
        point2Ds = new PointF[106];
        visibleIndexes = new float[106];
        for (int i = 0; i < 106; i++) {
            point2Ds[i] = new PointF(0, 0);
        }

        bmScaleMatrix = new Matrix();
        leftEar = BitmapFactory.decodeResource(this.getResources(), R.drawable.leftear);
        rightEar = BitmapFactory.decodeResource(this.getResources(), R.drawable.rightear);
        nose = BitmapFactory.decodeResource(this.getResources(), R.drawable.dog_nose);
        ear = BitmapFactory.decodeResource(this.getResources(), R.drawable.ear);

    }

    public void setVisionDetRetList(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix) {
        this.face = face;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        this.scaleMatrix = scaleMatrix;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        currentWidth = MeasureSpec.getSize(widthMeasureSpec);
        currentHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == ratioWidth || 0 == ratioHeight) {
            setMeasuredDimension(currentWidth, currentHeight);
        } else {
            if (currentWidth < currentHeight * ratioWidth / ratioHeight) {
                setMeasuredDimension(currentWidth, currentWidth * ratioHeight / ratioWidth);
            } else {
                setMeasuredDimension(currentHeight * ratioWidth / ratioHeight, currentHeight);
            }
        }

        offsetX = (currentWidth * 0.5f - ratioWidth * 0.5f);
        offsetY = (currentHeight * 0.5f - ratioHeight * 0.5f);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    // Fix to Camera preview rate
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        ratioWidth = width;
        ratioHeight = height;

        if (ratioWidth * 1f / ratioHeight == currentWidth * 1f / currentHeight) {
            ratioWidth = currentWidth;
            ratioHeight = currentHeight;
        }

        requestLayout();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.setMatrix(scaleMatrix);
        if (face != null) {
            faceRect.set(previewHeight - face.left, face.top, previewHeight - face.right, face.bottom);
            for (int i = 0; i < 106; i++) {
                point2Ds[i].set(face.landmarks[i * 2], face.landmarks[i * 2 + 1]);
                visibleIndexes[i] = 1.0f;
            }

            // Draw landmarks
//            for (int i = 0; i < 106; i++) {
//                point2Ds[i].x = previewHeight - face.landmarks[i * 2];
//            }
//            STUtils.drawFaceRect(canvas, faceRect, previewHeight, previewWidth, true);
//            STUtils.drawPoints(canvas, faceLandmarkPaint, point2Ds, visibleIndexes, previewHeight, previewWidth, true);

            // Draw 2dMask
            float scaleW = Math.abs(faceRect.width() / MASK_SIZE_STANDARD_W);
            float scaleH = Math.abs(faceRect.height() / MASK_SIZE_STANDARD_H);

            float[] angle = transfomationRenderProcess();
            Matrix transformationMatrix = new Matrix();

            PointF leftEarF = point2Ds[29];
            //leftEarF.x - 130 * scaleW, leftEarF.y - 130 * scaleH,
            float ratio = leftEar.getHeight()*1.0f/leftEar.getWidth();
            float earW = Math.abs(0.5f*faceRect.width());
            float earH = earW*ratio;
            Bitmap leftTmp = Bitmap.createScaledBitmap(leftEar, (int) (earW), (int) (earH), false);
            transformationMatrix = transformMat(leftTmp.getWidth()/2,leftTmp.getHeight()/2,leftEarF.x -earW, leftEarF.y - earH,angle[0],angle[1],angle[2]);

            canvas.drawBitmap(leftTmp, transformationMatrix, null);

            PointF rightEarF = point2Ds[70];

            Bitmap rightTmp = Bitmap.createScaledBitmap(rightEar, (int) (earW), (int) (earH), false);
            transformationMatrix = transformMat(rightTmp.getWidth()/2,rightTmp.getHeight()/2,rightEarF.x , rightEarF.y - earH,angle[0],angle[1],angle[2]);

            canvas.drawBitmap(rightTmp, transformationMatrix, null);
//            float ratio = ear.getHeight()*1.0f/ear.getWidth();
//            float earW = 1.5f*Math.abs(faceRect.width());
//            Log.d(TAG,"Face Width: "+faceRect.width());
//            Log.d(TAG,"Ratio: "+ratio);
//
//            float earH = earW*ratio;
//            PointF centerEyeF = point2Ds[21];
//            float maskX =centerEyeF.x - earW/2f;
//            float maskY = centerEyeF.y - earH*2;
//            Log.d(TAG,"maskX"+maskX+"maskY"+maskY);
//            Bitmap earTmp = Bitmap.createScaledBitmap(ear, (int) earW, (int) earH, false);
//            transformationMatrix = transformMat(earTmp.getWidth()/2,earTmp.getHeight()/2,maskX,maskY,angle[0],angle[1],angle[2]);
//
//            canvas.drawBitmap(earTmp, transformationMatrix,null);

            PointF noseF = point2Ds[46];
            float nratio = nose.getHeight()*1.0f/nose.getWidth();
            float nwidth = 2*(point2Ds[93].x-point2Ds[31].x);
            float nheight = nwidth*nratio;
            Bitmap noseTmp = Bitmap.createScaledBitmap(nose, (int) (nwidth), (int) (nheight), false);
            transformationMatrix = transformMat(noseTmp.getWidth()/2,noseTmp.getHeight()/2,noseF.x - noseTmp.getWidth() / 2f, noseF.y - noseTmp.getHeight() / 2f,angle[0],angle[1],angle[2]);

            canvas.drawBitmap(noseTmp, transformationMatrix,null);


        }
    }

    private Matrix transformMat(float centerX, float centerY, float x, float y, float degreeX, float degreeY, float degreeZ){
        Matrix transMat = new Matrix();

        Camera camera = new Camera();
        camera.save();
        camera.rotateX(degreeX);
        camera.rotateY(degreeY);
        camera.rotateZ(degreeZ);
        camera.getMatrix(transMat);
        camera.restore();

        transMat.preTranslate(-centerX,-centerY);
//        transMat.postRotate(degree);

        transMat.postTranslate(centerX,centerY);
        transMat.postTranslate(x,y);
        return transMat;
    }
    private float[] transfomationRenderProcess(){
        float rotation[] = new float[3];
        float limit = 30f;
        //calculate Z degree
        for (int i = 0; i < 106; i++) {
            point2Ds[i].set(face.landmarks[i * 2], face.landmarks[i * 2 + 1]);
        }
        PointF leftEye = point2Ds[94];
        PointF rightEye = point2Ds[20];
        float hypo = leftEye.y - rightEye.y;
        float side =  Math.abs(rightEye.x - leftEye.x);
        //convert radians to degree
        float angle = (float) Math.toDegrees(Math.tan(hypo/side));
        Log.d(TAG,"angleZ: "+angle);
        if(angle>limit){
            angle = limit;
        }else if(angle<-limit) {
            angle = -limit;
        }
        //Xdegree
        rotation[0]=0;
        //Zdegree
        rotation[2] = angle;

        //calculat Y degree

        PointF mouth = point2Ds[32];
        float side1 = Math.abs(mouth.x - leftEye.x);
        float side2 = Math.abs(mouth.x - rightEye.x);
        float sideC = mouth.y - point2Ds[21].y;
        //convert radians to degree
        float angle1 = (float) Math.toDegrees(Math.tan(side1/sideC));
        float angle2 =(float) Math.toDegrees(Math.tan(side2/sideC));
        angle = angle1 - angle2;
        Log.d(TAG,"angleY: "+angle);

        if(angle>limit){
            angle = limit;
        }else if(angle<-limit) {
            angle = -limit;
        }
        rotation[1] = angle;
        return rotation;
    }

}
