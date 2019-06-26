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

package com.nhancv.facemask.m2d;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;

import com.nhancv.facemask.m2d.mask.MaskUpdater;

import org.wysaid.view.ImageGLSurfaceView;

import zeusees.tracking.Face;


public class OpenGLPreview extends ImageGLSurfaceView {

    private static final String TAG = OpenGLPreview.class.getSimpleName();

    private int ratioWidth = 0;
    private int ratioHeight = 0;
    private float offsetX = 0;
    private float offsetY = 0;

    private int previewWidth;
    private int previewHeight;
    private int currentWidth;
    private int currentHeight;

    private MaskUpdater maskUpdater;
    private Bitmap previewBm;

    public OpenGLPreview(Context context) {
        this(context, null);
    }

    public OpenGLPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        maskUpdater = new MaskUpdater(context);
    }

    public void maskUpdateLocation(Bitmap previewBm, Face face, int previewWidth, int previewHeight, Matrix scaleMatrix) {
        this.previewBm = previewBm;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;

        this.maskUpdater.maskUpdateLocation(face, previewWidth, previewHeight, scaleMatrix);
        postInvalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        this.maskUpdater.stopUpdatingFps();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        currentWidth = MeasureSpec.getSize(widthMeasureSpec);
        currentHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == ratioWidth || 0 == ratioHeight) {
            setMeasuredDimension(currentWidth, currentHeight);
        } else {
            if (currentWidth < currentHeight * ratioWidth / ratioHeight) {
                setMeasuredDimension(currentWidth, currentWidth * ratioHeight / ratioWidth);
            } else {
                setMeasuredDimension(currentHeight * ratioWidth / ratioHeight, currentHeight);
            }
        }

        offsetX = (currentWidth * 0.5f - ratioWidth * 0.5f);
        offsetY = (currentHeight * 0.5f - ratioHeight * 0.5f);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    // Fix to Camera preview rate
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        ratioWidth = width;
        ratioHeight = height;

        if (ratioWidth * 1f / ratioHeight == currentWidth * 1f / currentHeight) {
            ratioWidth = currentWidth;
            ratioHeight = currentHeight;
        }

        requestLayout();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (previewBm != null && !previewBm.isRecycled()) {
            canvas.drawBitmap(previewBm, 0, 0, null);
        }
        // Draw 2dMask
        this.maskUpdater.onDraw(canvas);
    }

    private float getX(float x) {
        return x / previewWidth * ratioWidth + offsetX;
    }

    private float getY(float y) {
        return y / previewHeight * ratioHeight + offsetY;
    }


}
