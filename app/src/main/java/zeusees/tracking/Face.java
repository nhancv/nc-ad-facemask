package zeusees.tracking;


import java.util.Arrays;

public class Face implements Cloneable {

    public int ID;
    public int left;
    public int top;
    public int right;
    public int bottom;
    public int height;
    public int width;
    public int[] landmarks;

    Face(int x1, int y1, int x2, int y2) {
        this(x1, y1, x2 - x1, y2 - y1, new int[106 * 2], 0);
    }


    Face(int x1, int y1, int _width, int _height, int[] landmark, int id) {
        left = x1;
        top = y1;
        right = x1 + _width;
        bottom = y1 + _height;
        width = _width;
        height = _height;
        landmarks = landmark;
        ID = id;
    }

    @Override
    public String toString() {
        return "Face{" +
                "ID=" + ID +
                ", left=" + left +
                ", top=" + top +
                ", right=" + right +
                ", bottom=" + bottom +
                ", height=" + height +
                ", width=" + width +
                ", landmarks=" + Arrays.toString(landmarks) +
                '}';
    }

    @Override
    public Face clone() {
        try {
            return (Face) super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
        }
    }
}
