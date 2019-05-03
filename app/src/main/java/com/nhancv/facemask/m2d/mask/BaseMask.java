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
import android.graphics.Camera;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;

import com.nhancv.facemask.pose.Rotation;
import com.nhancv.facemask.pose.Translation;
import com.nhancv.facemask.util.SolvePNP;

import zeusees.tracking.Face;

public abstract class BaseMask implements Mask {

    protected Rect faceRect;
    protected PointF[] point2Ds;

    @Override
    public void init(Context context) {
        faceRect = new Rect();
        point2Ds = new PointF[106];
        for (int i = 0; i < 106; i++) {
            point2Ds[i] = new PointF(0, 0);
        }
    }

    @Override
    public void update(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix, SolvePNP solvePNP) {
        faceRect.set(previewHeight - face.left, face.top, previewHeight - face.right, face.bottom);
        for (int i = 0; i < 106; i++) {
            point2Ds[i].set(face.landmarks[i * 2], face.landmarks[i * 2 + 1]);
        }
        //Solve PNP
        solvePNP.setUpLandmarks(point2Ds);
        try {
            solvePNP.solvePNP();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void transformMat(Matrix inputMt, float centerX, float centerY, float x, float y, Rotation rotation, Translation translation) {
        float[] rotValue = rotation.rotationValue();
        float[] transValue = translation.translationValue();
        inputMt.reset();

        Camera camera = new Camera();
        camera.save();
        camera.rotateX(rotValue[0]);
        camera.rotateY(rotValue[1]);
        camera.rotateZ(rotValue[2]);
        camera.getMatrix(inputMt);
        camera.restore();

        inputMt.preTranslate(-centerX, -centerY);
        inputMt.postTranslate(centerX, centerY);
        inputMt.postTranslate(x, y);
    }
}
