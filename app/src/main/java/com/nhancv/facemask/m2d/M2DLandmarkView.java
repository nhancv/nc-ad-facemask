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
import android.graphics.RectF;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.nhancv.facemask.R;
import com.nhancv.facemask.fps.StableFps;
import com.nhancv.facemask.m3d.MyRenderer;
import com.nhancv.facemask.m3d.transformation.Rotation;
import com.nhancv.facemask.m3d.transformation.Translation;
import com.nhancv.facemask.util.ND01ForwardPoint;
import com.nhancv.facemask.util.SolvePNP;

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
    private Bitmap dog;
    private Matrix bmScaleMatrix;

    private StableFps stableFps;
    private HandlerThread handlerThread;
    private Handler handler;

    private MyRenderer myRenderer;

    private SolvePNP solvePNP = new SolvePNP();

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
        dog = BitmapFactory.decodeResource(this.getResources(), R.drawable.dog);

        //start thread
//        handlerThread = new HandlerThread("M2DLM");
//        handlerThread.start();
//        handler = new Handler(handlerThread.getLooper());
        stableFps = new StableFps(25);
    }

    public void onResume() {
        solvePNP.initialize();
    }

    public void onPause() {
        solvePNP.releaseMat();
    }

    @Override
    protected void onDetachedFromWindow() {
        stableFps.stop();
        solvePNP.releaseMat();
        super.onDetachedFromWindow();
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    public void setRenderer(MyRenderer renderer) {
        this.myRenderer = renderer;
    }

    public void setVisionDetRetList(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix) {
        this.face = face;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        this.scaleMatrix = scaleMatrix;

        if (!stableFps.isStarted()) {
            stableFps.start(fps -> {
                postInvalidate();
            });
        }
        if (handler != null)
            handler.post(() -> {
                //init solve pnp variables
                solvePNP.initialize();

                if (face != null) {
                    faceRect.set(previewHeight - face.left, face.top, previewHeight - face.right, face.bottom);
                    for (int i = 0; i < 106; i++) {
//                point2Ds[i].set(face.landmarks[i * 2], face.landmarks[i * 2 + 1]);
                        point2Ds[i].set(face.landmarks[i * 2], face.landmarks[i * 2 + 1]);
                        visibleIndexes[i] = 1.0f;
                    }

                    chinF = point2Ds[0];
                    if (acceptNoise.isEmpty() || !acceptNoise.contains(chinF.x, chinF.y)) {

                        float anchorX = chinF.x;
                        float anchorY = chinF.y;
                        float radius = 1.25f;
                        acceptNoise.set(anchorX - radius, anchorY - radius, anchorX + radius, anchorY + radius);

                        //Buffer coors
                        noseF = new PointF(point2Ds[46].x, point2Ds[46].y);
                        leftEarF = new PointF(point2Ds[29].x, point2Ds[29].y);
                        rightEarF = new PointF(point2Ds[70].x, point2Ds[70].y);

                        //Solve PNP
                        solvePNP.setUpLandmarks(point2Ds);
                        try {
                            solvePNP.solvePNP();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }


                        Rotation rotation = new Rotation(solvePNP.getRx(), solvePNP.getRy(), solvePNP.getRz());
                        Translation translation = new Translation(0, 0, solvePNP.getTz());

                        // TODO: 4/21/19 Comment for 3d testing
                        float ratio = leftEar.getHeight() * 1.0f / leftEar.getWidth();
                        float earW = Math.abs(0.5f * faceRect.width());
                        float earH = earW * ratio;

                        float R = 0.6f * (float) Math.sqrt((noseF.x - leftEarF.x) * (noseF.x - leftEarF.x) + (noseF.y - leftEarF.y) * (noseF.y - leftEarF.y));
                        float Ox = noseF.x, Oy = noseF.y;
                        float Ax = leftEarF.x, Ay = leftEarF.y;
                        ND01ForwardPoint forwardPoint = new ND01ForwardPoint();
                        forwardPoint.solve(Ox, Oy, Ax, Ay, R);

                        leftTmp = Bitmap.createScaledBitmap(leftEar, (int) (earW), (int) (earH), false);
                        leftMt = transformMat(leftTmp.getWidth() / 2, leftTmp.getHeight() / 2, forwardPoint.x - earW / 2, forwardPoint.y - earH / 2, rotation, translation);


                        Ax = rightEarF.x;
                        Ay = rightEarF.y;
                        forwardPoint.solve(Ox, Oy, Ax, Ay, R);

                        float ratio2 = rightEar.getHeight() * 1.0f / rightEar.getWidth();
                        float earW2 = Math.abs(0.5f * faceRect.width());
                        float earH2 = earW2 * ratio2;
                        rightTmp = Bitmap.createScaledBitmap(rightEar, (int) (earW), (int) (earH), false);
                        rightMt = transformMat(rightTmp.getWidth() / 2, rightTmp.getHeight() / 2, forwardPoint.x - earW2 / 2, forwardPoint.y - earH2 / 2, rotation, translation);

                        float nratio = nose.getHeight() * 1.0f / nose.getWidth();
                        float nwidth = Math.abs(0.5f * faceRect.width());
                        float nheight = nwidth * nratio;
                        noseTmp = Bitmap.createScaledBitmap(nose, (int) (nwidth), (int) (nheight), false);
                        noseMt = transformMat(noseTmp.getWidth() / 2, noseTmp.getHeight() / 2, noseF.x - noseTmp.getWidth() / 2f, noseF.y - noseTmp.getHeight() / 2f, rotation, translation);
                    }
                } else {
                    leftTmp = rightTmp = noseTmp = null;
                }
            });
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

    private RectF acceptNoise = new RectF();
    private PointF chinF, noseF, leftEarF, rightEarF;

    Bitmap leftTmp = null, rightTmp = null, noseTmp = null;
    Matrix leftMt = new Matrix();
    Matrix rightMt = new Matrix();
    Matrix noseMt = new Matrix();


    @Override
    protected void onDraw(Canvas canvas) {
        canvas.setMatrix(scaleMatrix);

        // Draw landmarks
//            for (int i = 0; i < 106; i++) {
//                point2Ds[i].x = previewHeight - face.landmarks[i * 2];
//            }
//            STUtils.drawFaceRect(canvas, faceRect, previewHeight, previewWidth, true);
//            STUtils.drawPoints(canvas, faceLandmarkPaint, point2Ds, visibleIndexes, previewHeight, previewWidth, true);

        // Draw 2dMask
//            float scaleW = Math.abs(faceRect.width() / MASK_SIZE_STANDARD_W);
//            float scaleH = Math.abs(faceRect.height() / MASK_SIZE_STANDARD_H);

//            float[] angle = transfomationRenderProcess();
//            Rotation rotation = new Rotation(solvePNP.getRx(), solvePNP.getRy(), solvePNP.getRz());
//            Translation translation = new Translation(0, 0, solvePNP.getTz());

        if (myRenderer != null) {
//                myRenderer.updateRotation(new Vector3(-solvePNP.getRx(), -solvePNP.getRy(), -solvePNP.getRz()));
//                myRenderer.updatePosition(new Vector3(solvePNP.getTx(), solvePNP.getTy(), solvePNP.getTz()));

                /*
                About position, in OpenGL, the axis from (-1 1) for both x and y with (0, 0) is the center.
                In the solvePNP return the position of bitmap, the (0,0) located at top-right and the max-x with max-y
                located at bottom-left. We need to convert from bitmap coordinate to OpenGL coordinate.
                 */

//                myRenderer.updatePosition(new Vector3((previewHeight - 2 * (-solvePNP.getTx())) / previewHeight,
//                        (previewWidth - 2 * solvePNP.getTy()) / previewWidth, -0.1f));
//                int fullScreenAspectRatio = (int) (previewWidth * (CameraFragment.SCREEN_SIZE.y * 1f / CameraFragment.SCREEN_SIZE.x));
//                PointF posF = point2Ds[46];
//                myRenderer.updatePosition(new Vector3((2 * posF.x - previewHeight) / previewHeight,
//                        (fullScreenAspectRatio - 2 * posF.y) / fullScreenAspectRatio, -0.5));
        }


        if (leftTmp != null) canvas.drawBitmap(leftTmp, leftMt, null);
        if (rightTmp != null) canvas.drawBitmap(rightTmp, rightMt, null);
        if (noseTmp != null) canvas.drawBitmap(noseTmp, noseMt, null);

    }


    private Matrix transformMat(float centerX, float centerY, float x, float y, Rotation rotation, Translation translation) {
        float[] rotValue = rotation.rotationValue();
        float[] transValue = translation.translationValue();
        Matrix transMat = new Matrix();

        Camera camera = new Camera();
        camera.save();
        camera.rotateX(rotValue[0]);
        camera.rotateY(rotValue[1]);
        camera.rotateZ(rotValue[2]);
//        camera.translate(transValue[0],transValue[1],transValue[2]);
        camera.getMatrix(transMat);
        camera.restore();

        transMat.preTranslate(-centerX, -centerY);
//        transMat.postRotate(degree);

        transMat.postTranslate(centerX, centerY);
        transMat.postTranslate(x, y);
        return transMat;
    }

    private float[] transfomationRenderProcess() {
        float rotation[] = new float[3];
        float limit = 30f;
        //calculate Z degree
        for (int i = 0; i < 106; i++) {
            point2Ds[i].set(face.landmarks[i * 2], face.landmarks[i * 2 + 1]);
        }
        PointF leftEye = point2Ds[94];
        PointF rightEye = point2Ds[20];
        float hypo = leftEye.y - rightEye.y;
        float side = Math.abs(rightEye.x - leftEye.x);
        //convert radians to degree
        float angle = (float) Math.toDegrees(Math.tan(hypo / side));
        Log.d(TAG, "angleZ: " + angle);
        if (angle > limit) {
            angle = limit;
        } else if (angle < -limit) {
            angle = -limit;
        }
        //Xdegree
        rotation[0] = 0;
        //Zdegree
        rotation[2] = angle;

        //calculat Y degree

        PointF mouth = point2Ds[32];
        float side1 = Math.abs(mouth.x - leftEye.x);
        float side2 = Math.abs(mouth.x - rightEye.x);
        float sideC = mouth.y - point2Ds[21].y;
        //convert radians to degree
        float angle1 = (float) Math.toDegrees(Math.tan(side1 / sideC));
        float angle2 = (float) Math.toDegrees(Math.tan(side2 / sideC));
        angle = angle1 - angle2;
        Log.d(TAG, "angleY: " + angle);

        if (angle > limit) {
            angle = limit;
        } else if (angle < -limit) {
            angle = -limit;
        }
        rotation[1] = angle;
        return rotation;
    }

    private float view2openglX(int x, int width) {
        float centerX = width / 2.0f;
        float t = x - centerX;
        return t / centerX;
    }

    private float view2openglY(int y, int height) {
        float centerY = height / 2.0f;
        float s = centerY - y;
        return s / centerY;
    }

}
