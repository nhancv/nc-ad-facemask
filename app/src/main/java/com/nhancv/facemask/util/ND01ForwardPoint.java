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

package com.nhancv.facemask.util;

import android.util.Log;

/**
 * NDefine 01: Forward Point
 * Assume: In Oxy space; O(0, 0), A (1, 2). Find B where:
 * + Length AB^2 = 1.25
 * + B placed on OA line.
 * + By >= Ay
 * <p>
 * Usage:
 * float R = (float) Math.sqrt(1.25);
 * float Ox = 0, Oy = 0;
 * float Ax = 1, Ay = 2;
 * ND01ForwardPoint forwardPoint = new ND01ForwardPoint();
 * forwardPoint.solve(Ox, Oy, Ax, Ay, R);
 * System.out.println(String.format("x = %f, y = %f", forwardPoint.x, forwardPoint.y));
 */

public class ND01ForwardPoint {
    private static final String TAG = ND01ForwardPoint.class.getSimpleName();
    public float x = Integer.MIN_VALUE, y = Integer.MIN_VALUE;

    public ND01ForwardPoint() {
    }

    public void solve(float Ox, float Oy, float Ax, float Ay, float AB) {
        // Line equation: y = ax + b
        float a = (Oy - Ay) / (Ox - Ax);
        float b = Ay - a * Ax;

        // Circle equation with center A(x, y) and radius R: R^2 = (x-Ax)^2 + (y-Ay)^2
        // Combine Line with Circle equation to get new function:
        // x^2*(a^2+1) + x*2*(a*(b-Ay)-Ax) - R^2 + Ax^2 + (b-Ay)^2 = 0

        float R = AB;
        float A = a * a + 1;
        float B = 2 * (a * (b - Ay) - Ax);
        float C = -(R * R) + Ax * Ax + (b - Ay) * (b - Ay);

        Float[] res = quadraticEquation(A, B, C);
        if (res != null && res.length > 0) {
            x = res[0];
            y = a * x + b;
            if (y < Ay) return;
            if (res.length > 1) {
                x = res[1];
                y = a * x + b;
            }
        }
    }

    public void reset() {
        x = y = Integer.MIN_VALUE;
    }

    public boolean isValid() {
        return x != Integer.MIN_VALUE && y != Integer.MIN_VALUE;
    }

    /**
     * Quadratic equation level 2: ax2 + bx + c = 0
     *
     * @param a: Coefficient level 2
     * @param b: Coefficient level 1
     * @param c: Coefficient level 0
     */
    public static Float[] quadraticEquation(float a, float b, float c) {
        // Coefficient checking
        if (a == 0) {
            if (b == 0) {
                return null;
            } else {
                return new Float[]{(-c / b)};
            }
        }
        // Find delta
        float delta = b * b - 4 * a * c;
        float x1;
        float x2;
        // Find x
        if (delta > 0) {
            x1 = (float) ((-b + Math.sqrt(delta)) / (2 * a));
            x2 = (float) ((-b - Math.sqrt(delta)) / (2 * a));
            return new Float[]{x1, x2};
        } else if (delta == 0) {
            x1 = (-b / (2 * a));
            return new Float[]{x1};
        }
        return null;
    }
}
