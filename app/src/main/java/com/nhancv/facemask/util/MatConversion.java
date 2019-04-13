package com.nhancv.facemask.util;

import android.graphics.Point;

import org.opencv.core.Mat;
import org.opencv.utils.Converters;

import java.util.ArrayList;
import java.util.List;

public class MatConversion {
    public Mat convertPts2OpenCVPts(List<Point> points){
        List<org.opencv.core.Point> oCVPoints = new ArrayList<>();
        for(int i = 0;i < points.size();i++)
        {
            oCVPoints.add(new org.opencv.core.Point(points.get(i).x,points.get(i).y));
        }
        Mat result = Converters.vector_Point2f_to_Mat(oCVPoints);
        return result;
    }
    /**
     * Input: Mat Points
     * Return: Android pts
     * */
    public void convertMatPts2(Mat points,List<Point> pts){
        List<org.opencv.core.Point> oCVPoints = new ArrayList<>();
        Converters.Mat_to_vector_Point(points,oCVPoints);
        int count = points.rows();
        float[] buff = new float[2*count];
        points.get(0,0,buff);//get buff points
        for(int i = 0;i < count;i++)
        {
            pts.add(new Point((int)buff[i*2],(int)buff[i*2+1]));
        }
    }
    public void convertMat2ByteList(Mat status,List<Byte> us){
        Converters.Mat_to_vector_uchar(status,us);
    }
}
