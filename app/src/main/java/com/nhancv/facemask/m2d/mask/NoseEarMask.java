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
import com.nhancv.facemask.pose.Rotation;
import com.nhancv.facemask.pose.Translation;
import com.nhancv.facemask.util.ND01ForwardPoint;
import com.nhancv.facemask.util.SolvePNP;

import zeusees.tracking.Face;

public abstract class NoseEarMask extends BaseMask implements Mask {
    private static final String TAG = NoseEarMask.class.getSimpleName();
    private Bitmap nose, ear;
    private Bitmap earTmp, noseTmp;
    private Matrix earMt, noseMt;
    private ND01ForwardPoint forwardPoint = new ND01ForwardPoint();

    protected abstract int getNoseBmResId();

    protected abstract int getEarBmResId();

    @Override
    public void init(Context context) {
        super.init(context);
        nose = BitmapFactory.decodeResource(context.getResources(), getNoseBmResId());
        ear = BitmapFactory.decodeResource(context.getResources(), getEarBmResId());

        earMt = new Matrix();
        noseMt = new Matrix();
        forwardPoint = new ND01ForwardPoint();
    }

    @Override
    public void update(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix, SolvePNP solvePNP) {
        if (face != null) {
            super.update(face, previewWidth, previewHeight, scaleMatrix, solvePNP);
            //Buffer coors
            PointF noseF = new PointF(point2Ds[46].x, point2Ds[46].y);
            PointF earF = new PointF(point2Ds[21].x, point2Ds[21].y);

            Rotation rotation = new Rotation(solvePNP.getRx(), solvePNP.getRy(), solvePNP.getRz());
            Translation translation = new Translation(0, 0, solvePNP.getTz());

            // TODO: 4/21/19 Comment for 3d testing
            float ratio = ear.getHeight() * 1.0f / ear.getWidth();
            float earW = Math.abs(1.2f * faceRect.width());
            float earH = earW * ratio;

            float R = 1.5f * (float) Math.sqrt((noseF.x - earF.x) * (noseF.x - earF.x) + (noseF.y - earF.y) * (noseF.y - earF.y));
            float Ox = noseF.x, Oy = noseF.y;
            float Ax = earF.x, Ay = earF.y;

            forwardPoint.solve(Ox, Oy, Ax, Ay, R);

            earTmp = Bitmap.createScaledBitmap(ear, (int) (earW), (int) (earH), false);
            if (forwardPoint.isValid()) {
                transformMat(earMt, earTmp.getWidth() / 2f, earTmp.getHeight() / 2f, forwardPoint.x - earW / 2, forwardPoint.y - earH / 2, rotation, translation);
            }

            float nratio = nose.getHeight() * 1.0f / nose.getWidth();
            float nwidth = Math.abs(1f * faceRect.width());
            float nheight = nwidth * nratio;
            noseTmp = Bitmap.createScaledBitmap(nose, (int) (nwidth), (int) (nheight), false);
            transformMat(noseMt, noseTmp.getWidth() / 2f, noseTmp.getHeight() / 2f, noseF.x - noseTmp.getWidth() / 2f, noseF.y - noseTmp.getHeight() / 2f, rotation, translation);
        } else {
            earTmp = noseTmp = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (earTmp != null) canvas.drawBitmap(earTmp, earMt, null);
        if (noseTmp != null) canvas.drawBitmap(noseTmp, noseMt, null);
    }

    @Override
    public void release() {
        if (nose != null) nose.recycle();
        if (ear != null) ear.recycle();
        if (noseTmp != null) noseTmp.recycle();
        if (earTmp != null) earTmp.recycle();
    }

}
