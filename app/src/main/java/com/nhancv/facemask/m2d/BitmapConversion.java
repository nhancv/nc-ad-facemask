package com.nhancv.facemask.m2d;

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
//        Bitmap bmp32 = bmp.copy(Bitmap.Config.ARGB_8888,true);
        Utils.bitmapToMat(bmp,rgbaMat);
        //From a 4 channel image, convert to 3 channel image
        Mat rgbMat = new Mat(bmp.getHeight(),bmp.getWidth(),CvType.CV_8UC3);
        cvtColor(rgbaMat,rgbMat,Imgproc.COLOR_RGBA2BGR,3); //BGR type for OpenCV
        return rgbMat;
    }
}
