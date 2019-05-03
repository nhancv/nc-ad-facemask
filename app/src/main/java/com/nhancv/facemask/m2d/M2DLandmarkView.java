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
import android.view.View;

import com.nhancv.facemask.R;
import com.nhancv.facemask.fps.StableFps;
import com.nhancv.facemask.m2d.mask.CatMask;
import com.nhancv.facemask.pose.Rotation;
import com.nhancv.facemask.pose.Translation;
import com.nhancv.facemask.util.ND01ForwardPoint;
import com.nhancv.facemask.util.SolvePNP;

import zeusees.tracking.Face;


public class M2DLandmarkView extends View {

    private static final String TAG = M2DLandmarkView.class.getSimpleName();

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

    private Bitmap nose;
    private Bitmap ear;


    private RectF acceptNoise = new RectF();
    private PointF chinF, noseF, earF;

    private Bitmap earTmp = null, noseTmp = null;
    private Matrix earMt = new Matrix();
    private Matrix noseMt = new Matrix();

    private StableFps stableFps;
    private HandlerThread handlerThread;
    private Handler handler;

    private SolvePNP solvePNP = new SolvePNP();

    private CatMask catMask = new CatMask();

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
        nose = BitmapFactory.decodeResource(this.getResources(), R.drawable.cat_nose);
        ear = BitmapFactory.decodeResource(this.getResources(), R.drawable.cat_ear);

        //start thread
//        handlerThread = new HandlerThread("M2DLM");
//        handlerThread.start();
//        handler = new Handler(handlerThread.getLooper());
        stableFps = new StableFps(25);

        catMask.init(getContext());
    }

    public void initPNP() {
        solvePNP.initialize();
    }

    public void releasePNP() {
        solvePNP.releaseMat();
    }

    @Override
    protected void onDetachedFromWindow() {
        stableFps.stop();
        solvePNP.releaseMat();
        catMask.release();
        super.onDetachedFromWindow();
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
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
                        earF = new PointF(point2Ds[21].x, point2Ds[21].y);

                        //Solve PNP
                        solvePNP.setUpLandmarks(point2Ds);
                        try {
                            solvePNP.solvePNP();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        catMask.update(face, previewWidth, previewHeight, scaleMatrix, solvePNP);

                        Rotation rotation = new Rotation(solvePNP.getRx(), solvePNP.getRy(), solvePNP.getRz());
                        Translation translation = new Translation(0, 0, solvePNP.getTz());

                        // TODO: 4/21/19 Comment for 3d testing
                        float ratio = ear.getHeight() * 1.0f / ear.getWidth();
                        float earW = Math.abs(1.2f * faceRect.width());
                        float earH = earW * ratio;

                        float R = 1.5f * (float) Math.sqrt((noseF.x - earF.x) * (noseF.x - earF.x) + (noseF.y - earF.y) * (noseF.y - earF.y));
                        float Ox = noseF.x, Oy = noseF.y;
                        float Ax = earF.x, Ay = earF.y;
                        ND01ForwardPoint forwardPoint = new ND01ForwardPoint();
                        forwardPoint.solve(Ox, Oy, Ax, Ay, R);

                        earTmp = Bitmap.createScaledBitmap(ear, (int) (earW), (int) (earH), false);
                        if (forwardPoint.isValid()) {
                            earMt = transformMat(earTmp.getWidth() / 2f, earTmp.getHeight() / 2f, forwardPoint.x - earW / 2, forwardPoint.y - earH / 2, rotation, translation);
                        }

                        float nratio = nose.getHeight() * 1.0f / nose.getWidth();
                        float nwidth = Math.abs(1f * faceRect.width());
                        float nheight = nwidth * nratio;
                        noseTmp = Bitmap.createScaledBitmap(nose, (int) (nwidth), (int) (nheight), false);
                        noseMt = transformMat(noseTmp.getWidth() / 2f, noseTmp.getHeight() / 2f, noseF.x - noseTmp.getWidth() / 2f, noseF.y - noseTmp.getHeight() / 2f, rotation, translation);
                    }
                } else {
                    earTmp = noseTmp = null;
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

        catMask.draw(canvas);
//        if (earTmp != null) canvas.drawBitmap(earTmp, earMt, null);
//        if (noseTmp != null) canvas.drawBitmap(noseTmp, noseMt, null);

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
        camera.getMatrix(transMat);
        camera.restore();

        transMat.preTranslate(-centerX, -centerY);

        transMat.postTranslate(centerX, centerY);
        transMat.postTranslate(x, y);
        return transMat;
    }

}
