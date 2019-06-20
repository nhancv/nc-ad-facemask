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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;

public class BigMask {
    private static final String TAG = BigMask.class.getSimpleName();
    private static final int SKIP = 3;
    private Bitmap bitmap;
    private int width = 256;
    private int height = 256;

    private int[][] maskIndexs = {
            {1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 0},
            {3, 3, 3, 3, 3, 3, 3, 3},
            {3, 3, 3, 3, 3, 4, 4, 4},
            {4, 5, 5, 5, 5, 5, 5, 6},
            {6, 6, 6, 6, 6, 0, 0, 0}
    };

    private int[][] playIndexs = {
            {0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0}
    };


    public BigMask(Bitmap bitmap) {
        this.bitmap = bitmap; // size 2048x2048, sprite size 256x256
    }

    private Bitmap getSprite(int r, int c, Matrix m) {
        return Bitmap.createBitmap(bitmap, r * width, c * height, width, height, m, false);
    }

    private Bitmap findSprite(int key, Matrix m) {
        // Find sprite
        for (int i = 0; i < maskIndexs.length; i++) {
            for (int j = 0; j < maskIndexs[i].length; j++) {
                if (maskIndexs[i][j] == key && playIndexs[i][j] < SKIP) {
                    playIndexs[i][j]++;
                    return getSprite(j, i, m);
                }
            }
        }

        // Clear play index
        for (int i = 0; i < maskIndexs.length; i++) {
            for (int j = 0; j < maskIndexs[i].length; j++) {
                if (maskIndexs[i][j] == key) {
                    playIndexs[i][j] = 0;
                }
            }
        }

        // Re-find sprite
        for (int i = 0; i < maskIndexs.length; i++) {
            for (int j = 0; j < maskIndexs[i].length; j++) {
                if (maskIndexs[i][j] == key && playIndexs[i][j] < SKIP) {
                    playIndexs[i][j]++;
                    return getSprite(j, i, m);
                }
            }
        }

        // Not found
        return null;
    }

    public Bitmap leftEar() {
        Matrix m = new Matrix();
        m.preScale(-1, 1);
        Bitmap bm = findSprite(1, m);
        if (bm == null) return null;
        Bitmap bmFinal = Bitmap.createBitmap(256, 256 * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmFinal);
        canvas.drawBitmap(bm, 0, 0, null);
        return bmFinal;
    }

    public Bitmap rightEar() {
        Matrix m = new Matrix();
        Bitmap bm = findSprite(1, m);
        if (bm == null) return null;
        Bitmap bmFinal = Bitmap.createBitmap(256, 256 * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmFinal);
        canvas.drawBitmap(bm, 0, 0, null);
        return bmFinal;
    }

    public Bitmap heart() {
        Matrix m = new Matrix();
        return findSprite(3, m);
    }

    public Bitmap nose() {
        Matrix m = new Matrix();
        return findSprite(4, m);
    }

    public Bitmap necklace() {
        Matrix m = new Matrix();
        return findSprite(5, m);
    }
}
