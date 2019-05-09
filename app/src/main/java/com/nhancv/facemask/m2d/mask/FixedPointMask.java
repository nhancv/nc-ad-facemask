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
import android.util.Log;

import com.nhancv.facemask.pose.Rotation;
import com.nhancv.facemask.pose.Translation;
import com.nhancv.facemask.tracking.KalmanFilter;
import com.nhancv.facemask.util.SolvePNP;

import zeusees.tracking.Face;

public abstract class FixedPointMask extends BaseMask implements Mask {
    private static final String TAG = FixedPointMask.class.getSimpleName();
    private Bitmap pointBm;
    private Bitmap pointBmTmp;
    private Matrix pointBmMt;

    private PointF lastNoseF = new PointF();
    private KalmanFilter kmNoseX = new KalmanFilter(1,1, 100f);
    private KalmanFilter kmNoseY = new KalmanFilter(1,1, 100f);

    protected abstract AnchorPart anchorPart();

    @Override
    public void init(Context context) {
        super.init(context);
        pointBm = BitmapFactory.decodeResource(context.getResources(), anchorPart().bmResId);

        pointBmMt = new Matrix();
    }

    @Override
    public void update(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix, SolvePNP solvePNP) {
        if (face != null && !pointBm.isRecycled()) {
            super.update(face, previewWidth, previewHeight, scaleMatrix, solvePNP);
            //Buffer coors
            int anchorPointId = anchorPart().anchorPointId;
            PointF noseF = new PointF(point2Ds[anchorPointId].x, point2Ds[anchorPointId].y);
            denoise(kmNoseX, kmNoseY, lastNoseF, noseF);

            Rotation rotation = new Rotation(solvePNP.getRx(), solvePNP.getRy(), solvePNP.getRz());
            Translation translation = new Translation(0, 0, solvePNP.getTz());

            float[] scalePts = new float[9];
            scaleMatrix.getValues(scalePts);
            float scaleX = scalePts[0];
            float scaleY = scalePts[4];

            float nratio = pointBm.getHeight() * 1.0f / pointBm.getWidth();
            float nwidth = Math.abs(anchorPart().scale * faceRect.width()) * scaleX;
            float nheight = nwidth * nratio;
            pointBmTmp = Bitmap.createScaledBitmap(pointBm, (int) (nwidth), (int) (nheight), false);
            transformMat(pointBmMt, pointBmTmp.getWidth() / 2f, pointBmTmp.getHeight() / 2f, (noseF.x * scaleX - pointBmTmp.getWidth() / 2f),
                    (noseF.y * scaleY - pointBmTmp.getHeight() / 2f), rotation, translation);
        } else {
            pointBmTmp = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (pointBmTmp != null && !pointBmTmp.isRecycled()) canvas.drawBitmap(pointBmTmp, pointBmMt, null);
    }

    @Override
    public void release() {
        if (pointBm != null) pointBm.recycle();
        if (pointBmTmp != null) pointBmTmp.recycle();
    }

}
