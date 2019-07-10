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

package com.nhancv.facemask.m2d.mask.dog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.Log;

import com.nhancv.facemask.R;
import com.nhancv.facemask.m2d.mask.BaseMask;
import com.nhancv.facemask.m2d.mask.Mask;
import com.nhancv.facemask.pose.Rotation;
import com.nhancv.facemask.pose.SolvePNP;
import com.nhancv.facemask.pose.Translation;
import com.nhancv.facemask.tracking.NKalmanFilter;

import zeusees.tracking.Face;

public class DogMask extends BaseMask implements Mask {
    private static final String TAG = DogMask.class.getSimpleName();
    private DogSprites dogSprites;
    private Bitmap noseBm, boneBm, leftBm, rightBm;
    private Bitmap noseBmTmp, boneBmTmp, leftBmTmp, rightBmTmp;
    private Matrix noseBmMt, boneBmMt, leftBmMt, rightBmMt;
    private final int animFrameLimit = 15;
    private int animFrameCounter = 0;
    private boolean mouthActiveAnimation = false;

    @Override
    public void init(Context context) {
        super.init(context);
        dogSprites = new DogSprites(
                BitmapFactory.decodeResource(context.getResources(), R.drawable.dog_mask));

        updateSprite();

        noseBmMt = new Matrix();
        boneBmMt = new Matrix();
        leftBmMt = new Matrix();
        rightBmMt = new Matrix();
    }

    /**
     * Update sprite rabbit_mask for animation
     */
    private void updateSprite() {
        release();
        noseBm = dogSprites.nose();
        leftBm = dogSprites.leftEar();
        rightBm = dogSprites.rightEar();
        boneBm = dogSprites.bone();
    }

    private PointF lastNoseF = new PointF();
    private NKalmanFilter kmNoseX = new NKalmanFilter(1, 1, 50f);
    private NKalmanFilter kmNoseY = new NKalmanFilter(1, 1, 50f);

    @Override
    public void update(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix, SolvePNP solvePNP) {
        if (face != null && !noseBm.isRecycled() && !boneBm.isRecycled()) {
            // Update next sprite
            updateSprite();

            super.update(face, previewWidth, previewHeight, scaleMatrix, solvePNP);
            //Buffer coors
            int nosePointId = 46;
            int bonePointId = 32;
            PointF noseF = new PointF(point2Ds[nosePointId].x, point2Ds[nosePointId].y);
            PointF boneF = new PointF(point2Ds[bonePointId].x, point2Ds[bonePointId].y);
            denoise(kmNoseX, kmNoseY, lastNoseF, noseF);

            Rotation rotation = new Rotation(solvePNP.getRx(), solvePNP.getRy(), solvePNP.getRz());
            Translation translation = new Translation(0, 0, solvePNP.getTz());
            float[] scalePts = new float[9];
            scaleMatrix.getValues(scalePts);
            float scaleX = scalePts[0]; // x value
            float scaleY = scalePts[4]; // y value

            if (isMouthOpened || mouthActiveAnimation) {
                float boneRatio = boneBm.getHeight() * 1.0f / boneBm.getWidth();
                float boneW = Math.abs(1f * faceRect.width()) * scaleX;
                float boneH = boneW * boneRatio;
                boneBmTmp = Bitmap.createScaledBitmap(boneBm, (int) (boneW), (int) (boneH), false);
                transformMat(boneBmMt, boneBmTmp.getWidth() / 2f, boneBmTmp.getHeight() / 2f, boneF.x * scaleX - boneBmTmp.getWidth() / 2f,
                        boneF.y * scaleY - boneBmTmp.getHeight() / 2f, rotation, translation);

                if (!mouthActiveAnimation) mouthActiveAnimation = true;
                if (animFrameCounter < animFrameLimit) {
                    animFrameCounter++;
                } else {
                    mouthActiveAnimation = false;
                    animFrameCounter = 0;
                }
            } else {
                boneBmTmp = null;
            }

            PointF leftEarF = new PointF(point2Ds[11].x, point2Ds[11].y);
            PointF rightEarF = new PointF(point2Ds[13].x, point2Ds[13].y);
            float earRatio = leftBm.getHeight() * 1.0f / leftBm.getWidth();
            float earW = Math.abs(1f * faceRect.width()) * scaleX;
            float earH = earW * earRatio;
            if (rotation.y > -10) {
                leftBmTmp = Bitmap.createScaledBitmap(leftBm, (int) (earW), (int) (earH), false);
                transformMat(leftBmMt, leftBmTmp.getWidth() / 2f, leftBmTmp.getHeight() / 2f, leftEarF.x * scaleX - leftBmTmp.getWidth() / 2f,
                        leftEarF.y * scaleY - leftBmTmp.getHeight() / 2f, rotation, translation);
            } else {
                leftBmTmp = null;
            }

            if (rotation.y < 10) {
                rightBmTmp = Bitmap.createScaledBitmap(rightBm, (int) (earW), (int) (earH), false);
                transformMat(rightBmMt, rightBmTmp.getWidth() / 2f, rightBmTmp.getHeight() / 2f, rightEarF.x * scaleX - rightBmTmp.getWidth() / 2f,
                        rightEarF.y * scaleY - rightBmTmp.getHeight() / 2f, rotation, translation);
            } else {
                rightBmTmp = null;
            }

            float noseRatio = noseBm.getHeight() * 1.0f / noseBm.getWidth();
            float noseW = Math.abs(1f * faceRect.width()) * scaleX;
            float noseH = noseW * noseRatio;
            noseBmTmp = Bitmap.createScaledBitmap(noseBm, (int) (noseW), (int) (noseH), false);
            transformMat(noseBmMt, noseBmTmp.getWidth() / 2f, noseBmTmp.getHeight() / 2f, noseF.x * scaleX - noseBmTmp.getWidth() / 2f,
                    noseF.y * scaleY - noseBmTmp.getHeight() / 2f, rotation, translation);
        } else {
            rightBmTmp = leftBmTmp = boneBmTmp = noseBmTmp = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (noseBmTmp != null && !noseBmTmp.isRecycled()) canvas.drawBitmap(noseBmTmp, noseBmMt, null);
        if (boneBmTmp != null && !boneBmTmp.isRecycled()) canvas.drawBitmap(boneBmTmp, boneBmMt, null);
        if (leftBmTmp != null && !leftBmTmp.isRecycled()) canvas.drawBitmap(leftBmTmp, leftBmMt, null);
        if (rightBmTmp != null && !rightBmTmp.isRecycled()) canvas.drawBitmap(rightBmTmp, rightBmMt, null);
    }

    @Override
    public void release() {
        if (noseBm != null) {
            noseBm.recycle();
            noseBm = null;
        }
        if (boneBm != null) {
            boneBm.recycle();
            boneBm = null;
        }
        if (noseBmTmp != null) {
            noseBmTmp.recycle();
            noseBmTmp = null;
        }
        if (boneBmTmp != null) {
            boneBmTmp.recycle();
            boneBmTmp = null;
        }
        if (leftBm != null) {
            leftBm.recycle();
            leftBm = null;
        }
        if (rightBm != null) {
            rightBm.recycle();
            rightBm = null;
        }
        if (leftBmTmp != null) {
            leftBmTmp.recycle();
            leftBmTmp = null;
        }
        if (rightBmTmp != null) {
            rightBmTmp.recycle();
            rightBmTmp = null;
        }
    }

}
