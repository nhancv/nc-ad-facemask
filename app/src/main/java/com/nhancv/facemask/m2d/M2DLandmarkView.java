package com.nhancv.facemask.m2d;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
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
    private Matrix bmScaleMatrix;


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
        leftEar = BitmapFactory.decodeResource(this.getResources(), R.drawable.left_ear);
        rightEar = BitmapFactory.decodeResource(this.getResources(), R.drawable.right_ear);
        nose = BitmapFactory.decodeResource(this.getResources(), R.drawable.nose);
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

            PointF leftEarF = point2Ds[29];
            Bitmap leftTmp = Bitmap.createScaledBitmap(leftEar, (int) (leftEar.getWidth() * scaleW), (int) (leftEar.getHeight() * scaleH), false);
            canvas.drawBitmap(leftTmp, leftEarF.x - 130 * scaleW, leftEarF.y - 130 * scaleH, null);

            PointF rightEarF = point2Ds[70];
            Bitmap rightTmp = Bitmap.createScaledBitmap(rightEar, (int) (rightEar.getWidth() * scaleW), (int) (rightEar.getHeight() * scaleH), false);
            canvas.drawBitmap(rightTmp, rightEarF.x + scaleW, rightEarF.y - 130 * scaleH, null);

            PointF noseF = point2Ds[46];
            Bitmap noseTmp = Bitmap.createScaledBitmap(nose, (int) (nose.getWidth() * scaleW), (int) (nose.getHeight() * scaleH), false);
            canvas.drawBitmap(noseTmp, noseF.x - noseTmp.getWidth() / 2f, noseF.y - noseTmp.getHeight() / 2f, null);

        }
    }
}
