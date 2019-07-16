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

package com.nhancv.facemask.m2d.mask.nerd;

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

public class NerdMask extends BaseMask implements Mask {
    private static final String TAG = NerdMask.class.getSimpleName();
    private Bitmap mask;
    private NerdSprites nerdSprites;
    private volatile Bitmap glassBm, decorSkinBm, hatBm, decorFlowerBm, eyeBrowLBm, eyeBrowRBm, salivaBm;
    private volatile Bitmap glassBmTmp, decorSkinBmTmp, hatBmTmp, decorFlowerBmTmp, eyeBrowLBmTmp, eyeBrowRBmTmp, salivaBmTmp;
    private Matrix glassBmMt, decorSkinBmMt, hatBmMt, decorFlowerBmMt, eyeBrowLBmMt, eyeBrowRBmMt, salivaBmMt;
    private final int animFrameLimit = 4;
    private int animFrameCounter = 0;
    private boolean mouthActiveAnimation = false;

    @Override
    public void init(Context context) {
        super.init(context);
        if (mask == null) {
            mask = BitmapFactory.decodeResource(context.getResources(), R.drawable.nerd_mask);
        }
        nerdSprites = new NerdSprites(mask);

        updateSprite();

        glassBmMt = new Matrix();
        decorSkinBmMt = new Matrix();
        hatBmMt = new Matrix();
        decorFlowerBmMt = new Matrix();
        eyeBrowLBmMt = new Matrix();
        eyeBrowRBmMt = new Matrix();
        salivaBmMt = new Matrix();
    }

    /**
     * Update sprite rabbit_mask for animation
     */
    private void updateSprite() {
//        release();
        glassBm = nerdSprites.glass();
        hatBm = nerdSprites.hat();
        decorFlowerBm = nerdSprites.decorFlower();
        decorSkinBm = nerdSprites.decorSkin();
        eyeBrowLBm = nerdSprites.eyeBrowL();
        eyeBrowRBm = nerdSprites.eyeBrowR();
        salivaBm = nerdSprites.saliva();
    }

    @Override
    public void update(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix, SolvePNP solvePNP) {
        if (face != null) {
            // Update next sprite
            updateSprite();

            super.update(face, previewWidth, previewHeight, scaleMatrix, solvePNP);

            Rotation rotation = new Rotation(solvePNP.getRx(), solvePNP.getRy(), solvePNP.getRz());
            Translation translation = new Translation(0, 0, solvePNP.getTz());
            float[] scalePts = new float[9];
            scaleMatrix.getValues(scalePts);
//            float scaleX = scalePts[0]; // x value
//            float scaleY = scalePts[4]; // y value
            float scaleX = 1f;
            float scaleY = 1f;

            if (isMouthOpened || mouthActiveAnimation) {
                int decorSkinPointId = 69;
                PointF decorSkinF = new PointF(point2Ds[decorSkinPointId].x, point2Ds[decorSkinPointId].y);

                float decorSkinRatio = decorSkinBm.getHeight() * 1.0f / decorSkinBm.getWidth();
                float decorSkinW = Math.abs(1.5f * faceRect.width()) * scaleX;
                float decorSkinH = decorSkinW * decorSkinRatio;
                decorSkinBmTmp = Bitmap.createScaledBitmap(decorSkinBm, (int) (decorSkinW), (int) (decorSkinH), false);
                transformMat(decorSkinBmMt, decorSkinBmTmp.getWidth() / 2f, decorSkinBmTmp.getHeight() / 2f, decorSkinF.x * scaleX - decorSkinBmTmp.getWidth() / 2f,
                        decorSkinF.y * scaleY - decorSkinBmTmp.getHeight() / 2f, rotation, translation);

                if (!mouthActiveAnimation) mouthActiveAnimation = true;
                if (animFrameCounter < animFrameLimit) {
                    animFrameCounter++;
                } else {
                    mouthActiveAnimation = false;
                    animFrameCounter = 0;
                }
            } else {
                decorSkinBmTmp = null;
                salivaBmTmp = null;
            }

            int hatPointId = 79;
            PointF hatF = new PointF(point2Ds[hatPointId].x, point2Ds[hatPointId].y);
            float hatRatio = hatBm.getHeight() * 1.0f / hatBm.getWidth();
            float hatW = Math.abs(1.2f * faceRect.width()) * scaleX;
            float hatH = hatW * hatRatio;
            hatBmTmp = Bitmap.createScaledBitmap(hatBm, (int) (hatW), (int) (hatH), false);
            transformMat(hatBmMt, hatBmTmp.getWidth() / 2f, hatBmTmp.getHeight() / 2f, hatF.x * scaleX - hatBmTmp.getWidth() / 2f,
                    hatF.y * scaleY - hatBmTmp.getHeight() / 2f, rotation, translation);

            int decorFlowerPointId = 75;
            PointF decorFlowerF = new PointF(point2Ds[decorFlowerPointId].x, point2Ds[decorFlowerPointId].y);
            float decorFlowerRatio = decorFlowerBm.getHeight() * 1.0f / decorFlowerBm.getWidth();
            float decorFlowerW = Math.abs(1f * faceRect.width()) * scaleX;
            float decorFlowerH = decorFlowerW * decorFlowerRatio;
            decorFlowerBmTmp = Bitmap.createScaledBitmap(decorFlowerBm, (int) (decorFlowerW), (int) (decorFlowerH), false);
            transformMat(decorFlowerBmMt, decorFlowerBmTmp.getWidth() / 2f, decorFlowerBmTmp.getHeight() / 2f, decorFlowerF.x * scaleX - decorFlowerBmTmp.getWidth() / 2f,
                    decorFlowerF.y * scaleY - decorFlowerBmTmp.getHeight() / 2f, rotation, translation);

            int glassPointId = 23;
            PointF glassF = new PointF(point2Ds[glassPointId].x, point2Ds[glassPointId].y);
            float glassRatio = glassBm.getHeight() * 1.0f / glassBm.getWidth();
            float glassW = Math.abs(1.3f * faceRect.width()) * scaleX;
            float glassH = glassW * glassRatio;
            glassBmTmp = Bitmap.createScaledBitmap(glassBm, (int) (glassW), (int) (glassH), false);
            transformMat(glassBmMt, glassBmTmp.getWidth() / 2f, glassBmTmp.getHeight() / 2f, glassF.x * scaleX - glassBmTmp.getWidth() / 2f,
                    glassF.y * scaleY - glassBmTmp.getHeight() / 2f, rotation, translation);

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

            int salivaPointId = 88;
            PointF salivaF = new PointF(point2Ds[salivaPointId].x, point2Ds[salivaPointId].y);
            float salivaRatio = salivaBm.getHeight() * 1.0f / salivaBm.getWidth();
            float salivaW = Math.abs(1f * faceRect.width()) * scaleX;
            float salivaH = salivaW * salivaRatio;
            salivaBmTmp = Bitmap.createScaledBitmap(salivaBm, (int) (salivaW), (int) (salivaH), false);
            transformMat(salivaBmMt, salivaBmTmp.getWidth() / 2f, salivaBmTmp.getHeight() / 2f, salivaF.x * scaleX - salivaBmTmp.getWidth() / 2f,
                    salivaF.y * scaleY - salivaBmTmp.getHeight() / 2f, rotation, translation);

        } else {
            decorFlowerBmTmp = hatBmTmp = glassBmTmp = decorSkinBmTmp = eyeBrowLBmTmp = eyeBrowRBmTmp = salivaBmTmp = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (glassBmTmp != null && !glassBmTmp.isRecycled()) canvas.drawBitmap(glassBmTmp, glassBmMt, null);
        if (hatBmTmp != null && !hatBmTmp.isRecycled()) canvas.drawBitmap(hatBmTmp, hatBmMt, null);
        if (decorFlowerBmTmp != null && !decorFlowerBmTmp.isRecycled())
            canvas.drawBitmap(decorFlowerBmTmp, decorFlowerBmMt, null);
        if (decorSkinBmTmp != null && !decorSkinBmTmp.isRecycled())
            canvas.drawBitmap(decorSkinBmTmp, decorSkinBmMt, null);
        if (eyeBrowLBmTmp != null && !eyeBrowLBmTmp.isRecycled()) canvas.drawBitmap(eyeBrowLBmTmp, eyeBrowLBmMt, null);
        if (eyeBrowRBmTmp != null && !eyeBrowRBmTmp.isRecycled()) canvas.drawBitmap(eyeBrowRBmTmp, eyeBrowRBmMt, null);
        if (salivaBmTmp != null && !salivaBmTmp.isRecycled()) canvas.drawBitmap(salivaBmTmp, salivaBmMt, null);
    }

    @Override
    public void release() {
        if (glassBm != null) {
            glassBm.recycle();
            glassBm = null;
        }
        if (glassBmTmp != null) {
            glassBmTmp.recycle();
            glassBmTmp = null;
        }
        if (decorSkinBm != null) {
            decorSkinBm.recycle();
            decorSkinBm = null;
        }
        if (decorSkinBmTmp != null) {
            decorSkinBmTmp.recycle();
            decorSkinBmTmp = null;
        }
        if (hatBm != null) {
            hatBm.recycle();
            hatBm = null;
        }
        if (hatBmTmp != null) {
            hatBmTmp.recycle();
            hatBmTmp = null;
        }
        if (decorFlowerBm != null) {
            decorFlowerBm.recycle();
            decorFlowerBm = null;
        }
        if (decorFlowerBmTmp != null) {
            decorFlowerBmTmp.recycle();
            decorFlowerBmTmp = null;
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
