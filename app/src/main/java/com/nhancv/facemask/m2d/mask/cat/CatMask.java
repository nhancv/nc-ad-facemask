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

package com.nhancv.facemask.m2d.mask.cat;

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

public class CatMask extends BaseMask implements Mask {
    private static final String TAG = CatMask.class.getSimpleName();
    private Bitmap mask;
    private CatSprites catSprites;
    private volatile Bitmap noseBm, decoreSkinBm, leftBm, rightBm, eyeBrowLBm, eyeBrowRBm, salivaBm;
    private volatile Bitmap noseBmTmp, decoreSkinBmTmp, leftBmTmp, rightBmTmp, eyeBrowLBmTmp, eyeBrowRBmTmp, salivaBmTmp;
    private Matrix noseBmMt, decoreSkinBmMt, leftBmMt, rightBmMt, eyeBrowLBmMt, eyeBrowRBmMt, salivaBmMt;
    private final int animFrameLimit = 4;
    private int animFrameCounter = 0;
    private boolean mouthActiveAnimation = false;

    @Override
    public void init(Context context) {
        super.init(context);
        if(mask == null) {
            mask = BitmapFactory.decodeResource(context.getResources(), R.drawable.cat_mask);
        }
        catSprites = new CatSprites(mask);

        updateSprite();

        noseBmMt = new Matrix();
        leftBmMt = new Matrix();
        rightBmMt = new Matrix();
        decoreSkinBmMt = new Matrix();
        eyeBrowLBmMt = new Matrix();
        eyeBrowRBmMt = new Matrix();
        salivaBmMt = new Matrix();
    }

    /**
     * Update sprite rabbit_mask for animation
     */
    private void updateSprite() {
//        release();
        noseBm = catSprites.nose();
        leftBm = catSprites.leftEar();
        rightBm = catSprites.rightEar();
        decoreSkinBm = catSprites.decorSkin();
        eyeBrowLBm = catSprites.eyeBrowL();
        eyeBrowRBm = catSprites.eyeBrowR();
        salivaBm = catSprites.saliva();
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
//            float scaleX = scalePts[0]; // x value
//            float scaleY = scalePts[4]; // y value
            float scaleX = 1f;
            float scaleY = 1f;

            if (isMouthOpened || mouthActiveAnimation) {
                int decoreSkinPointId = 22;
                PointF decoreSkinF = new PointF(point2Ds[decoreSkinPointId].x, point2Ds[decoreSkinPointId].y);
                float decoreSkinRatio = decoreSkinBm.getHeight() * 1.0f / decoreSkinBm.getWidth();
                float decoreSkinW = Math.abs(1f * faceRect.width()) * scaleX;
                float decoreSkinH = decoreSkinW * decoreSkinRatio;
                decoreSkinBmTmp = Bitmap.createScaledBitmap(decoreSkinBm, (int) (decoreSkinW), (int) (decoreSkinH), false);
                transformMat(decoreSkinBmMt, decoreSkinBmTmp.getWidth() / 2f, decoreSkinBmTmp.getHeight() / 2f, decoreSkinF.x * scaleX - decoreSkinBmTmp.getWidth() / 2f,
                        decoreSkinF.y * scaleY - decoreSkinBmTmp.getHeight() / 2f, rotation, translation);

                int salivaPointId = 103;
                PointF salivaF = new PointF(point2Ds[salivaPointId].x, point2Ds[salivaPointId].y);
                float salivaRatio = salivaBm.getHeight() * 1.0f / salivaBm.getWidth();
                float salivaW = Math.abs(1.3f * faceRect.width()) * scaleX;
                float salivaH = salivaW * salivaRatio;
                salivaBmTmp = Bitmap.createScaledBitmap(salivaBm, (int) (salivaW), (int) (salivaH), false);
                transformMat(salivaBmMt, salivaBmTmp.getWidth() / 2f, salivaBmTmp.getHeight() / 2f, salivaF.x * scaleX - salivaBmTmp.getWidth() / 2f,
                        salivaF.y * scaleY - salivaBmTmp.getHeight() / 2f, rotation, translation);


                if (!mouthActiveAnimation) mouthActiveAnimation = true;
                if (animFrameCounter < animFrameLimit) {
                    animFrameCounter++;
                } else {
                    mouthActiveAnimation = false;
                    animFrameCounter = 0;
                }
            } else {
                decoreSkinBmTmp = null;
                salivaBmTmp = null;
            }

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

            int nosePointId = 46;
            PointF noseF = new PointF(point2Ds[nosePointId].x, point2Ds[nosePointId].y);
            float noseRatio = noseBm.getHeight() * 1.0f / noseBm.getWidth();
            float noseW = Math.abs(1.3f * faceRect.width()) * scaleX;
            float noseH = noseW * noseRatio;
            noseBmTmp = Bitmap.createScaledBitmap(noseBm, (int) (noseW), (int) (noseH), false);
            transformMat(noseBmMt, noseBmTmp.getWidth() / 2f, noseBmTmp.getHeight() / 2f, noseF.x * scaleX - noseBmTmp.getWidth() / 2f,
                    noseF.y * scaleY - noseBmTmp.getHeight() / 2f, rotation, translation);

            int eyeBrowLPointId = 84;
            PointF eyeBrowLF = new PointF(point2Ds[eyeBrowLPointId].x, point2Ds[eyeBrowLPointId].y);
            float eyeBrowLRatio = eyeBrowLBm.getHeight() * 1.0f / eyeBrowLBm.getWidth();
            float eyeBrowLW = Math.abs(1f * faceRect.width()) * scaleX;
            float eyeBrowLH = eyeBrowLW * eyeBrowLRatio;
            eyeBrowLBmTmp = Bitmap.createScaledBitmap(eyeBrowLBm, (int) (eyeBrowLW), (int) (eyeBrowLH), false);
            transformMat(eyeBrowLBmMt, eyeBrowLBmTmp.getWidth() / 2f, eyeBrowLBmTmp.getHeight() / 2f, eyeBrowLF.x * scaleX - eyeBrowLBmTmp.getWidth() / 2f,
                    eyeBrowLF.y * scaleY - eyeBrowLBmTmp.getHeight() / 2f, rotation, translation);

            int eyeBrowRPointId = 75;
            PointF eyeBrowRF = new PointF(point2Ds[eyeBrowRPointId].x, point2Ds[eyeBrowRPointId].y);
            eyeBrowRBmTmp = Bitmap.createScaledBitmap(eyeBrowRBm, (int) (eyeBrowLW), (int) (eyeBrowLH), false);
            transformMat(eyeBrowRBmMt, eyeBrowRBmTmp.getWidth() / 2f, eyeBrowRBmTmp.getHeight() / 2f, eyeBrowRF.x * scaleX - eyeBrowRBmTmp.getWidth() / 2f,
                    eyeBrowRF.y * scaleY - eyeBrowRBmTmp.getHeight() / 2f, rotation, translation);

        } else {
            rightBmTmp = leftBmTmp = decoreSkinBmTmp = noseBmTmp = eyeBrowLBmTmp = eyeBrowRBmTmp = salivaBmTmp = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (noseBmTmp != null && !noseBmTmp.isRecycled()) canvas.drawBitmap(noseBmTmp, noseBmMt, null);
        if (leftBmTmp != null && !leftBmTmp.isRecycled()) canvas.drawBitmap(leftBmTmp, leftBmMt, null);
        if (rightBmTmp != null && !rightBmTmp.isRecycled()) canvas.drawBitmap(rightBmTmp, rightBmMt, null);
        if (rightBmTmp != null && !rightBmTmp.isRecycled()) canvas.drawBitmap(rightBmTmp, rightBmMt, null);
        if (decoreSkinBmTmp != null && !decoreSkinBmTmp.isRecycled()) canvas.drawBitmap(decoreSkinBmTmp, decoreSkinBmMt, null);
        if (eyeBrowLBmTmp != null && !eyeBrowLBmTmp.isRecycled()) canvas.drawBitmap(eyeBrowLBmTmp, eyeBrowLBmMt, null);
        if (eyeBrowRBmTmp != null && !eyeBrowRBmTmp.isRecycled()) canvas.drawBitmap(eyeBrowRBmTmp, eyeBrowRBmMt, null);
        if (salivaBmTmp != null && !salivaBmTmp.isRecycled()) canvas.drawBitmap(salivaBmTmp, salivaBmMt, null);
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
        if (decoreSkinBm != null) {
            decoreSkinBm.recycle();
            decoreSkinBm = null;
        }
        if (decoreSkinBmTmp != null) {
            decoreSkinBmTmp.recycle();
            decoreSkinBmTmp = null;
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
        if (eyeBrowLBm != null) {
            eyeBrowLBm.recycle();
            eyeBrowLBm = null;
        }
        if (eyeBrowLBmTmp != null) {
            eyeBrowLBmTmp.recycle();
            eyeBrowLBmTmp = null;
        }
        if (eyeBrowRBm != null) {
            eyeBrowRBm.recycle();
            eyeBrowRBm = null;
        }
        if (eyeBrowRBmTmp != null) {
            eyeBrowRBmTmp.recycle();
            eyeBrowRBmTmp = null;
        }
        if (salivaBm != null) {
            salivaBm.recycle();
            salivaBm = null;
        }
        if (salivaBmTmp != null) {
            salivaBmTmp.recycle();
            salivaBmTmp = null;
        }
    }

}
