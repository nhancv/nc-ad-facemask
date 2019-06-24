package com.nhancv.facemask.util;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.imgproc.Imgproc.cvtColor;

public class MatConversion {
    public Mat convertPts2OpenCVPts(List<Point> points) {
        List<org.opencv.core.Point> oCVPoints = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            oCVPoints.add(new org.opencv.core.Point(points.get(i).x, points.get(i).y));
        }
        Mat result = Converters.vector_Point2f_to_Mat(oCVPoints);
        return result;
    }

    /**
     * Input: Mat Points
     * Return: Android pts
     */
    public void convertMatPts2(Mat points, List<Point> pts) {
        List<org.opencv.core.Point> oCVPoints = new ArrayList<>();
        Converters.Mat_to_vector_Point(points, oCVPoints);
        int count = points.rows();
        float[] buff = new float[2 * count];
        points.get(0, 0, buff);//get buff points
        for (int i = 0; i < count; i++) {
            pts.add(new Point((int) buff[i * 2], (int) buff[i * 2 + 1]));
        }
    }

    public void convertMat2ByteList(Mat status, List<Byte> us) {
        Converters.Mat_to_vector_uchar(status, us);
    }

    public Bitmap convertMat2Bitmap(Mat img) {
        int width = img.width();
        int height = img.height();
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Mat tmp;
        if (img.channels() == 1) {
            tmp = new Mat(width, height, CvType.CV_8UC1, new Scalar(1));
        } else
            tmp = new Mat(width, height, CvType.CV_8UC3);
        try {
            if (img.channels() == 3) {
                cvtColor(img, tmp, Imgproc.COLOR_RGB2BGRA); //convert from rgb to bgra
            } else {
                cvtColor(img, tmp, Imgproc.COLOR_GRAY2RGBA); //convert from gray to rgba
            }
            Utils.matToBitmap(tmp, bmp);
        } catch (CvException e) {
            Log.d("Exception ", e.getMessage());
        }
        return bmp;
    }

    public Mat convertBitmap2Mat(Bitmap bmp) {
        Mat rgbaMat = new Mat(bmp.getHeight(), bmp.getWidth(), CvType.CV_8UC4); //empty mat
        Utils.bitmapToMat(bmp, rgbaMat);
        //From a 4 channel image, convert to 3 channel image
        Mat rgbMat = new Mat(bmp.getHeight(), bmp.getWidth(), CvType.CV_8UC3);
        cvtColor(rgbaMat, rgbMat, Imgproc.COLOR_RGBA2BGR, 3); //BGR type for OpenCV
        return rgbMat;
    }

    public Mat convertBitmap2GrayMat(Bitmap bmp) {
        Mat rgbaMat = new Mat(bmp.getHeight(), bmp.getWidth(), CvType.CV_8UC4); //empty mat
        Utils.bitmapToMat(bmp, rgbaMat);
        //From a 4 channel image, convert to  gray scale img
        Mat grayMat = new Mat(bmp.getHeight(), bmp.getWidth(), CvType.CV_8UC1);
        cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY, 3); //BGR type for OpenCV
        return grayMat;
    }
}
