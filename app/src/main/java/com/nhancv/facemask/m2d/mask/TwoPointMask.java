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

import com.nhancv.facemask.pose.Rotation;
import com.nhancv.facemask.pose.Translation;
import com.nhancv.facemask.util.ND01ForwardPoint;
import com.nhancv.facemask.util.SolvePNP;

import zeusees.tracking.Face;

public abstract class TwoPointMask extends BaseMask implements Mask {
    private static final String TAG = TwoPointMask.class.getSimpleName();
    private Bitmap anchorBm, nearBm;
    private Bitmap anchorBmTmp, nearBmTmp;
    private Matrix anchorBmMt, nearBmMt;
    private ND01ForwardPoint forwardPoint = new ND01ForwardPoint();

    protected abstract AnchorPart anchorPart();

    protected abstract NearPart nearPart();

    @Override
    public void init(Context context) {
        super.init(context);
        anchorBm = BitmapFactory.decodeResource(context.getResources(), anchorPart().bmResId);
        nearBm = BitmapFactory.decodeResource(context.getResources(), nearPart().bmResId);

        nearBmMt = new Matrix();
        anchorBmMt = new Matrix();
        forwardPoint = new ND01ForwardPoint();
    }

    @Override
    public void update(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix, SolvePNP solvePNP) {
        if (face != null && !anchorBm.isRecycled() && !nearBm.isRecycled()) {
            super.update(face, previewWidth, previewHeight, scaleMatrix, solvePNP);
            //Buffer coors
            int anchorPointId = anchorPart().anchorPointId;
            int nearAnchorPointId = nearPart().anchorPointId;
            PointF noseF = new PointF(point2Ds[anchorPointId].x, point2Ds[anchorPointId].y);
            PointF earF = new PointF(point2Ds[nearAnchorPointId].x, point2Ds[nearAnchorPointId].y);

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

            forwardPoint.solve(Ox, Oy, Ax, Ay, R);

            nearBmTmp = Bitmap.createScaledBitmap(nearBm, (int) (earW), (int) (earH), false);
            if (forwardPoint.isValid()) {
                transformMat(nearBmMt, nearBmTmp.getWidth() / 2f, nearBmTmp.getHeight() / 2f, forwardPoint.x * scaleX - earW / 2,
                        forwardPoint.y * scaleY - earH / 2, rotation, translation);
            }

            float nratio = anchorBm.getHeight() * 1.0f / anchorBm.getWidth();
            float nwidth = Math.abs(anchorPart().scale * faceRect.width()) * scaleX;
            float nheight = nwidth * nratio;
            anchorBmTmp = Bitmap.createScaledBitmap(anchorBm, (int) (nwidth), (int) (nheight), false);
            transformMat(anchorBmMt, anchorBmTmp.getWidth() / 2f, anchorBmTmp.getHeight() / 2f, noseF.x * scaleX - anchorBmTmp.getWidth() / 2f,
                    noseF.y * scaleY - anchorBmTmp.getHeight() / 2f, rotation, translation);
        } else {
            nearBmTmp = anchorBmTmp = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (nearBmTmp != null && !nearBmTmp.isRecycled()) canvas.drawBitmap(nearBmTmp, nearBmMt, null);
        if (anchorBmTmp != null && !anchorBmTmp.isRecycled()) canvas.drawBitmap(anchorBmTmp, anchorBmMt, null);
    }

    @Override
    public void release() {
        if (anchorBm != null) anchorBm.recycle();
        if (nearBm != null) nearBm.recycle();
        if (anchorBmTmp != null) anchorBmTmp.recycle();
        if (nearBmTmp != null) nearBmTmp.recycle();
    }

}
