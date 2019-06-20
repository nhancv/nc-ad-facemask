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

package com.nhancv.facemask.m2d.mask;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;

import com.nhancv.facemask.R;
import com.nhancv.facemask.m2d.BigMask;
import com.nhancv.facemask.pose.Rotation;
import com.nhancv.facemask.pose.SolvePNP;
import com.nhancv.facemask.pose.Translation;
import com.nhancv.facemask.tracking.NKalmanFilter;
import com.nhancv.facemask.util.ND01ForwardPoint;

import zeusees.tracking.Face;

public abstract class TwoPointMask extends BaseMask implements Mask {
    private static final String TAG = TwoPointMask.class.getSimpleName();
    private Bitmap anchorBm, nearBm, leftBm, rightBm, necklaceBm, heartBm;
    private Bitmap anchorBmTmp, nearBmTmp, leftBmTmp, rightBmTmp, necklaceBmTmp, heartBmTmp;
    private Matrix anchorBmMt, nearBmMt, leftBmMt, rightBmMt, necklaceBmMt, heartBmMt;
    private ND01ForwardPoint forwardPoint = new ND01ForwardPoint();
    private BigMask bigMask;

    protected abstract AnchorPart anchorPart();

    protected abstract NearPart nearPart();

    @Override
    public void init(Context context) {
        super.init(context);
        bigMask = new BigMask(
                BitmapFactory.decodeResource(context.getResources(), R.drawable.mask));

        anchorBm = BitmapFactory.decodeResource(context.getResources(), anchorPart().bmResId);
        nearBm = BitmapFactory.decodeResource(context.getResources(), nearPart().bmResId);

        nearBmMt = new Matrix();
        leftBmMt = new Matrix();
        rightBmMt = new Matrix();
        necklaceBmMt = new Matrix();
        heartBmMt = new Matrix();

        anchorBmMt = new Matrix();
        forwardPoint = new ND01ForwardPoint();
    }

    private PointF lastNoseF = new PointF();
    private NKalmanFilter kmNoseX = new NKalmanFilter(1, 1, 50f);
    private NKalmanFilter kmNoseY = new NKalmanFilter(1, 1, 50f);
    private PointF lastEarF = new PointF();
    private NKalmanFilter kmEarX = new NKalmanFilter(1, 1, 50f);
    private NKalmanFilter kmEarY = new NKalmanFilter(1, 1, 50f);

    @Override
    public void update(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix, SolvePNP solvePNP) {
        if (face != null && !anchorBm.isRecycled() && !nearBm.isRecycled()) {

            anchorBm = bigMask.nose();
            nearBm = bigMask.heart();
            leftBm = bigMask.leftEar();
            rightBm = bigMask.rightEar();
            necklaceBm = bigMask.necklace();
            heartBm = bigMask.heart();

            super.update(face, previewWidth, previewHeight, scaleMatrix, solvePNP);
            //Buffer coors
            int anchorPointId = anchorPart().anchorPointId;
//            int nearAnchorPointId = nearPart().anchorPointId;
            int nearAnchorPointId = 50;
            PointF noseF = new PointF(point2Ds[anchorPointId].x, point2Ds[anchorPointId].y);
            PointF earF = new PointF(point2Ds[nearAnchorPointId].x, point2Ds[nearAnchorPointId].y);
            denoise(kmNoseX, kmNoseY, lastNoseF, noseF);
            denoise(kmEarX, kmEarY, lastEarF, earF);

            Rotation rotation = new Rotation(solvePNP.getRx(), solvePNP.getRy(), solvePNP.getRz());
            Translation translation = new Translation(0, 0, solvePNP.getTz());

            float[] scalePts = new float[9];
            scaleMatrix.getValues(scalePts);
            float scaleX = scalePts[0];
            float scaleY = scalePts[4];

            float ratio = nearBm.getHeight() * 1.0f / nearBm.getWidth();
            float earW = Math.abs(nearPart().scale * faceRect.width()) * scaleX;
            float earH = earW * ratio;

            float R = nearPart().distanceRate * (float) Math.sqrt((noseF.x - earF.x) * (noseF.x - earF.x) + (noseF.y - earF.y) * (noseF.y - earF.y));
            float Ox = noseF.x, Oy = noseF.y;
            float Ax = earF.x, Ay = earF.y;

//            forwardPoint.solve(Ox, Oy, Ax, Ay, R);

            nearBmTmp = Bitmap.createScaledBitmap(nearBm, (int) (earW), (int) (earH), false);
//            transformMat(nearBmMt, nearBmTmp.getWidth() / 2f, nearBmTmp.getHeight() / 2f, forwardPoint.x * scaleX - earW / 2,
//                    forwardPoint.y * scaleY - earH / 2, rotation, translation);
            transformMat(nearBmMt, nearBmTmp.getWidth() / 2f, nearBmTmp.getHeight() / 2f, earF.x * scaleX - nearBmTmp.getWidth() / 2f,
                    earF.y * scaleY - nearBmTmp.getHeight() / 2f, rotation, translation);

            PointF leftEarF = new PointF(point2Ds[19].x, point2Ds[19].y);
            PointF rightEarF = new PointF(point2Ds[74].x, point2Ds[74].y);
            float ratioEar = leftBm.getHeight() * 1.0f / leftBm.getWidth();
            float earsW = Math.abs(1f * faceRect.width()) * scaleX;
            float earsH = earsW * ratioEar;
            leftBmTmp = Bitmap.createScaledBitmap(leftBm, (int) (earsW), (int) (earsH), false);
            transformMat(leftBmMt, leftBmTmp.getWidth() / 2f, leftBmTmp.getHeight() / 2f, leftEarF.x * scaleX - leftBmTmp.getWidth() / 2f,
                    leftEarF.y * scaleY - leftBmTmp.getHeight() / 2f, rotation, translation);

            rightBmTmp = Bitmap.createScaledBitmap(rightBm, (int) (earsW), (int) (earsH), false);
            transformMat(rightBmMt, rightBmTmp.getWidth() / 2f, rightBmTmp.getHeight() / 2f, rightEarF.x * scaleX - rightBmTmp.getWidth() / 2f,
                    rightEarF.y * scaleY - rightBmTmp.getHeight() / 2f, rotation, translation);

            float nratio = anchorBm.getHeight() * 1.0f / anchorBm.getWidth();
            float nwidth = Math.abs(anchorPart().scale * faceRect.width()) * scaleX;
            float nheight = nwidth * nratio;
            anchorBmTmp = Bitmap.createScaledBitmap(anchorBm, (int) (nwidth), (int) (nheight), false);
            transformMat(anchorBmMt, anchorBmTmp.getWidth() / 2f, anchorBmTmp.getHeight() / 2f, noseF.x * scaleX - anchorBmTmp.getWidth() / 2f,
                    noseF.y * scaleY - anchorBmTmp.getHeight() / 2f, rotation, translation);
        } else {
            rightBmTmp = leftBmTmp = nearBmTmp = anchorBmTmp = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (nearBmTmp != null && !nearBmTmp.isRecycled()) canvas.drawBitmap(nearBmTmp, nearBmMt, null);
        if (anchorBmTmp != null && !anchorBmTmp.isRecycled()) canvas.drawBitmap(anchorBmTmp, anchorBmMt, null);
        if (leftBmTmp != null && !leftBmTmp.isRecycled()) canvas.drawBitmap(leftBmTmp, leftBmMt, null);
        if (rightBmTmp != null && !rightBmTmp.isRecycled()) canvas.drawBitmap(rightBmTmp, rightBmMt, null);
    }

    @Override
    public void release() {
        if (anchorBm != null) anchorBm.recycle();
        if (nearBm != null) nearBm.recycle();
        if (anchorBmTmp != null) anchorBmTmp.recycle();
        if (nearBmTmp != null) nearBmTmp.recycle();
    }

}
