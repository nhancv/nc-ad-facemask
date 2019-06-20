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

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import static org.opencv.imgproc.Imgproc.cvtColor;

public class BitmapConversion {
    public Bitmap convertMat2Bitmap(Mat img) {
        int width = img.width();
        int height = img.height();
        Bitmap bmp = Bitmap.createBitmap(width,height, Bitmap.Config.ARGB_8888);
        Mat tmp;
        if(img.channels() ==1)
        {
            tmp =new Mat (width,height, CvType.CV_8UC1,new Scalar(1));
        }
        else
            tmp = new Mat (width,height,CvType.CV_8UC3);
        try {
            if(img.channels()==3){
                cvtColor(img,tmp, Imgproc.COLOR_RGB2BGRA); //convert from rgb to bgra
            }
            else
            {
                cvtColor(img,tmp,Imgproc.COLOR_GRAY2RGBA); //convert from gray to rgba
            }
            Utils.matToBitmap(tmp,bmp);
        }
        catch (CvException e){
            Log.d("Exception ",e.getMessage());
        }
        return bmp;
    }
    public Mat convertBitmap2Mat(Bitmap bmp) {
        Mat rgbaMat = new Mat(bmp.getHeight(),bmp.getWidth(),CvType.CV_8UC4); //empty mat
//        Bitmap bmp32 = bmp.copy(Bitmap.Constant.ARGB_8888,true);
        Utils.bitmapToMat(bmp,rgbaMat);
        //From a 4 channel image, convert to 3 channel image
        Mat rgbMat = new Mat(bmp.getHeight(),bmp.getWidth(),CvType.CV_8UC3);
        cvtColor(rgbaMat,rgbMat,Imgproc.COLOR_RGBA2BGR,3); //BGR type for OpenCV
        return rgbMat;
    }
    public Mat convertBitmap2GrayMat(Bitmap bmp){
        Mat rgbaMat = new Mat(bmp.getHeight(),bmp.getWidth(),CvType.CV_8UC4); //empty mat
//        Bitmap bmp32 = bmp.copy(Bitmap.Constant.ARGB_8888,true);
        Utils.bitmapToMat(bmp,rgbaMat);
        //From a 4 channel image, convert to  gray scale img
        Mat grayMat = new Mat(bmp.getHeight(),bmp.getWidth(),CvType.CV_8UC1);
        cvtColor(rgbaMat,grayMat,Imgproc.COLOR_RGBA2GRAY,3); //BGR type for OpenCV
        return grayMat;
    }
}
