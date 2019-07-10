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
import android.graphics.Canvas;
import android.graphics.Matrix;

import com.nhancv.facemask.fps.StableFps;
import com.nhancv.facemask.m2d.mask.dog.DogMask;
import com.nhancv.facemask.m2d.mask.rabbit.RabbitMask;
import com.nhancv.facemask.pose.SolvePNP;

import zeusees.tracking.Face;


public class MaskUpdater {

    private static final String TAG = MaskUpdater.class.getSimpleName();

    private Mask mask;
    private SolvePNP solvePNP;

    public MaskUpdater(Context context) {
        //start thread
        solvePNP = SolvePNP.getInstance();
        //init rabbit_mask
//        mask = new RabbitMask();
        mask = new DogMask();
        mask.init(context);
    }

    public void maskUpdateLocation(Face face, int previewWidth, int previewHeight, Matrix scaleMatrix) {
        if (mask != null) mask.update(face, previewWidth, previewHeight, scaleMatrix, solvePNP);
    }

    // Update preview after
    public void onDraw(Canvas canvas) {
        if (mask != null) mask.draw(canvas);

    }

}
