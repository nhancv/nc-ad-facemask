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

package com.nhancv.facemask.m2d.mask.rabbit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;

import com.nhancv.facemask.R;
import com.nhancv.facemask.m2d.mask.BaseMask;
import com.nhancv.facemask.m2d.mask.Mask;
import com.nhancv.facemask.pose.Rotation;
import com.nhancv.facemask.pose.SolvePNP;
import com.nhancv.facemask.pose.Translation;
import com.nhancv.facemask.tracking.NKalmanFilter;

import zeusees.tracking.Face;

public class RabbitMask extends BaseMask implements Mask {
    private static final String TAG = RabbitMask.class.getSimpleName();
    private RabbitSprites rabbitSprites;
    private Bitmap noseBm, heartBm, leftBm, rightBm;
    private Bitmap noseBmTmp, heartBmTmp, leftBmTmp, rightBmTmp;
    private Matrix noseBmMt, heartBmMt, leftBmMt, rightBmMt;

    @Override
    public void init(Context context) {
        super.init(context);
        rabbitSprites = new RabbitSprites(
                BitmapFactory.decodeResource(context.getResources(), R.drawable.mask));

        updateSprite();

        noseBmMt = new Matrix();
        heartBmMt = new Matrix();
        leftBmMt = new Matrix();
        rightBmMt = new Matrix();
    }

    /**
     * Update sprite mask for animation
     */
    private void updateSprite() {
        noseBm = rabbitSprites.nose();
        heartBm = rabbitSprites.heart();
        leftBm = rabbitSprites.leftEar();
        rightBm = rabbitSprites.rightEar();
    }

    private PointF lastNoseF = new PointF();
    private NKalmanFilter kmNoseX = new NKalmanFilter(1, 1, 50f);
    private NKalmanFilter kmNoseY = new NKalmanFilter(1, 1, 50f);
    private PointF lastHeartF = new PointF();
    private NKalmanFilter kmHeartX = new NKalmanFilter(1, 1, 50f);
    private NKalmanFilter kmHeartY = new NKalmanFilter(1, 1, 50f);

    @Override
    public void update(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix, SolvePNP solvePNP) {
        if (face != null && !noseBm.isRecycled() && !heartBm.isRecycled()) {
            // Update next sprite
            updateSprite();

            super.update(face, previewWidth, previewHeight, scaleMatrix, solvePNP);
            //Buffer coors
            int nosePointId = 46;
            int heartPointId = 50;
            PointF noseF = new PointF(point2Ds[nosePointId].x, point2Ds[nosePointId].y);
            PointF heartF = new PointF(point2Ds[heartPointId].x, point2Ds[heartPointId].y);
            denoise(kmNoseX, kmNoseY, lastNoseF, noseF);
            denoise(kmHeartX, kmHeartY, lastHeartF, heartF);

            Rotation rotation = new Rotation(solvePNP.getRx(), solvePNP.getRy(), solvePNP.getRz());
            Translation translation = new Translation(0, 0, solvePNP.getTz());

            float[] scalePts = new float[9];
            scaleMatrix.getValues(scalePts);
            float scaleX = scalePts[0]; // x value
            float scaleY = scalePts[4]; // y value

            float heartRatio = heartBm.getHeight() * 1.0f / heartBm.getWidth();
            float heartW = Math.abs(1f * faceRect.width()) * scaleX;
            float heartH = heartW * heartRatio;

            heartBmTmp = Bitmap.createScaledBitmap(heartBm, (int) (heartW), (int) (heartH), false);
            transformMat(heartBmMt, heartBmTmp.getWidth() / 2f, heartBmTmp.getHeight() / 2f, heartF.x * scaleX - heartBmTmp.getWidth() / 2f,
                    heartF.y * scaleY - heartBmTmp.getHeight() / 2f, rotation, translation);

            PointF leftEarF = new PointF(point2Ds[19].x, point2Ds[19].y);
            PointF rightEarF = new PointF(point2Ds[74].x, point2Ds[74].y);
            float earRatio = leftBm.getHeight() * 1.0f / leftBm.getWidth();
            float earW = Math.abs(1f * faceRect.width()) * scaleX;
            float earH = earW * earRatio;
            leftBmTmp = Bitmap.createScaledBitmap(leftBm, (int) (earW), (int) (earH), false);
            transformMat(leftBmMt, leftBmTmp.getWidth() / 2f, leftBmTmp.getHeight() / 2f, leftEarF.x * scaleX - leftBmTmp.getWidth() / 2f,
                    leftEarF.y * scaleY - leftBmTmp.getHeight() / 2f, rotation, translation);

            rightBmTmp = Bitmap.createScaledBitmap(rightBm, (int) (earW), (int) (earH), false);
            transformMat(rightBmMt, rightBmTmp.getWidth() / 2f, rightBmTmp.getHeight() / 2f, rightEarF.x * scaleX - rightBmTmp.getWidth() / 2f,
                    rightEarF.y * scaleY - rightBmTmp.getHeight() / 2f, rotation, translation);

            float noseRatio = noseBm.getHeight() * 1.0f / noseBm.getWidth();
            float noseW = Math.abs(1f * faceRect.width()) * scaleX;
            float noseH = noseW * noseRatio;
            noseBmTmp = Bitmap.createScaledBitmap(noseBm, (int) (noseW), (int) (noseH), false);
            transformMat(noseBmMt, noseBmTmp.getWidth() / 2f, noseBmTmp.getHeight() / 2f, noseF.x * scaleX - noseBmTmp.getWidth() / 2f,
                    noseF.y * scaleY - noseBmTmp.getHeight() / 2f, rotation, translation);
        } else {
            rightBmTmp = leftBmTmp = heartBmTmp = noseBmTmp = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (noseBmTmp != null && !noseBmTmp.isRecycled()) canvas.drawBitmap(noseBmTmp, noseBmMt, null);
        if (heartBmTmp != null && !heartBmTmp.isRecycled()) canvas.drawBitmap(heartBmTmp, heartBmMt, null);
        if (leftBmTmp != null && !leftBmTmp.isRecycled()) canvas.drawBitmap(leftBmTmp, leftBmMt, null);
        if (rightBmTmp != null && !rightBmTmp.isRecycled()) canvas.drawBitmap(rightBmTmp, rightBmMt, null);
    }

    @Override
    public void release() {
        if (noseBm != null) noseBm.recycle();
        if (heartBm != null) heartBm.recycle();
        if (noseBmTmp != null) noseBmTmp.recycle();
        if (heartBmTmp != null) heartBmTmp.recycle();
        if (leftBm != null) leftBm.recycle();
        if (rightBm != null) rightBm.recycle();
        if (leftBmTmp != null) leftBmTmp.recycle();
        if (rightBmTmp != null) rightBmTmp.recycle();
    }

}
