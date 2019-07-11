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

package com.nhancv.facemask.m2d.mask.hamster;

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

import zeusees.tracking.Face;

public class HamsterMask extends BaseMask implements Mask {
    private static final String TAG = HamsterMask.class.getSimpleName();
    private Bitmap mask;
    private HamsterSprites hamsterSprites;
    private volatile Bitmap noseBm, beanBm, leftBm, rightBm;
    private volatile Bitmap noseBmTmp, beanBmTmp, leftBmTmp, rightBmTmp;
    private Matrix noseBmMt, beanBmMt, leftBmMt, rightBmMt;
    private final int animFrameLimit = 15;
    private int animFrameCounter = 0;
    private boolean mouthActiveAnimation = false;

    @Override
    public void init(Context context) {
        super.init(context);
        if (mask == null) {
            mask = BitmapFactory.decodeResource(context.getResources(), R.drawable.hamster_mask);
        }
        hamsterSprites = new HamsterSprites(mask);

        updateSprite();

        noseBmMt = new Matrix();
        beanBmMt = new Matrix();
        leftBmMt = new Matrix();
        rightBmMt = new Matrix();
    }

    /**
     * Update sprite rabbit_mask for animation
     */
    private void updateSprite() {
//        release();
        noseBm = hamsterSprites.nose();
        leftBm = hamsterSprites.leftEar();
        rightBm = hamsterSprites.rightEar();
        beanBm = hamsterSprites.bean();
    }

    @Override
    public void update(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix, SolvePNP solvePNP) {
        if (face != null) {
            // Update next sprite
            updateSprite();

            super.update(face, previewWidth, previewHeight, scaleMatrix, solvePNP);
            //Buffer coors
            Rotation rotation = new Rotation(solvePNP.getRx(), solvePNP.getRy(), solvePNP.getRz());
            Translation translation = new Translation(0, 0, solvePNP.getTz());
            float[] scalePts = new float[9];
            scaleMatrix.getValues(scalePts);
            float scaleX = scalePts[0]; // x value
            float scaleY = scalePts[4]; // y value

            if (isMouthOpened || mouthActiveAnimation) {
                int beanPointId = 0;
                PointF beanF = new PointF(point2Ds[beanPointId].x, point2Ds[beanPointId].y);

                float beanRatio = beanBm.getHeight() * 1.0f / beanBm.getWidth();
                float beanW = Math.abs(1f * faceRect.width()) * scaleX;
                float beanH = beanW * beanRatio;
                beanBmTmp = Bitmap.createScaledBitmap(beanBm, (int) (beanW), (int) (beanH), false);
                transformMat(beanBmMt, beanBmTmp.getWidth() / 2f, beanBmTmp.getHeight() / 2f, beanF.x * scaleX - beanBmTmp.getWidth() / 2f,
                        beanF.y * scaleY - beanBmTmp.getHeight() / 2f, rotation, translation);

                if (!mouthActiveAnimation) mouthActiveAnimation = true;
                if (animFrameCounter < animFrameLimit) {
                    animFrameCounter++;
                } else {
                    mouthActiveAnimation = false;
                    animFrameCounter = 0;
                }
            } else {
                beanBmTmp = null;
            }

            PointF leftEarF = new PointF(point2Ds[29].x, point2Ds[29].y);
            PointF rightEarF = new PointF(point2Ds[70].x, point2Ds[70].y);
            float earRatio = leftBm.getHeight() * 1.0f / leftBm.getWidth();
            float earW = Math.abs(1f * faceRect.width()) * scaleX;
            float earH = earW * earRatio;
            leftBmTmp = Bitmap.createScaledBitmap(leftBm, (int) (earW), (int) (earH), false);
            transformMat(leftBmMt, leftBmTmp.getWidth() / 2f, leftBmTmp.getHeight() / 2f, leftEarF.x * scaleX - leftBmTmp.getWidth() / 2f,
                    leftEarF.y * scaleY - leftBmTmp.getHeight() / 2f, rotation, translation);

            rightBmTmp = Bitmap.createScaledBitmap(rightBm, (int) (earW), (int) (earH), false);
            transformMat(rightBmMt, rightBmTmp.getWidth() / 2f, rightBmTmp.getHeight() / 2f, rightEarF.x * scaleX - rightBmTmp.getWidth() / 2f,
                    rightEarF.y * scaleY - rightBmTmp.getHeight() / 2f, rotation, translation);

            int nosePointId = 46;
            PointF noseF = new PointF(point2Ds[nosePointId].x, point2Ds[nosePointId].y);
            float noseRatio = noseBm.getHeight() * 1.0f / noseBm.getWidth();
            float noseW = Math.abs(1.7f * faceRect.width()) * scaleX;
            float noseH = noseW * noseRatio;
            noseBmTmp = Bitmap.createScaledBitmap(noseBm, (int) (noseW), (int) (noseH), false);
            transformMat(noseBmMt, noseBmTmp.getWidth() / 2f, noseBmTmp.getHeight() / 2f, noseF.x * scaleX - noseBmTmp.getWidth() / 2f,
                    noseF.y * scaleY - noseBmTmp.getHeight() / 2f, rotation, translation);
        } else {
            rightBmTmp = leftBmTmp = beanBmTmp = noseBmTmp = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (noseBmTmp != null && !noseBmTmp.isRecycled()) canvas.drawBitmap(noseBmTmp, noseBmMt, null);
        if (beanBmTmp != null && !beanBmTmp.isRecycled()) canvas.drawBitmap(beanBmTmp, beanBmMt, null);
        if (leftBmTmp != null && !leftBmTmp.isRecycled()) canvas.drawBitmap(leftBmTmp, leftBmMt, null);
        if (rightBmTmp != null && !rightBmTmp.isRecycled()) canvas.drawBitmap(rightBmTmp, rightBmMt, null);
    }

    @Override
    public void release() {
        if (noseBm != null) {
            noseBm.recycle();
            noseBm = null;
        }
        if (noseBmTmp != null) {
            noseBmTmp.recycle();
            noseBmTmp = null;
        }
        if (beanBm != null) {
            beanBm.recycle();
            beanBm = null;
        }
        if (beanBmTmp != null) {
            beanBmTmp.recycle();
            beanBmTmp = null;
        }
        if (leftBm != null) {
            leftBm.recycle();
            leftBm = null;
        }
        if (leftBmTmp != null) {
            leftBmTmp.recycle();
            leftBmTmp = null;
        }
        if (rightBm != null) {
            rightBm.recycle();
            rightBm = null;
        }
        if (rightBmTmp != null) {
            rightBmTmp.recycle();
            rightBmTmp = null;
        }
    }

}
