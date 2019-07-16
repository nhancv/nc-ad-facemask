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

package zeusees.tracking;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Environment;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.TimingLogger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class STUtils {
    private static RenderScript mRS = null;
    private static ScriptIntrinsicYuvToRGB mYuvToRgb = null;
    private static Allocation ain = null;
    private static Allocation aOut = null;
    private static Bitmap bitmap = null;

    public STUtils() {
    }

    public static int[] getBGRAImageByte(Bitmap image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (image.getConfig().equals(Bitmap.Config.ARGB_8888)) {
            int[] imgData = new int[width * height];
            image.getPixels(imgData, 0, width, 0, 0, width, height);
            return imgData;
        } else {
            return null;
        }
    }

    @SuppressLint({"NewApi"})
    public static Bitmap NV21ToRGBABitmap(byte[] nv21, int width, int height, Context context) {
        TimingLogger timings = new TimingLogger("STUtils timing", "NV21ToRGBABitmap");
        Rect rect = new Rect(0, 0, width, height);
        try {
            Class.forName("android.renderscript.Element$DataKind").getField("PIXEL_YUV");
            Class.forName("android.renderscript.ScriptIntrinsicYuvToRGB");
            if (mRS == null) {
                mRS = RenderScript.create(context);
                mYuvToRgb = ScriptIntrinsicYuvToRGB.create(mRS, Element.U8_4(mRS));
                Type.Builder tb = new Type.Builder(mRS, Element.createPixel(mRS, Element.DataType.UNSIGNED_8, Element.DataKind.PIXEL_YUV));
                tb.setX(width);
                tb.setY(height);
                tb.setMipmaps(false);
                tb.setYuvFormat(17);
                ain = Allocation.createTyped(mRS, tb.create(), 1);
                timings.addSplit("Prepare for ain");
                Type.Builder tb2 = new Type.Builder(mRS, Element.RGBA_8888(mRS));
                tb2.setX(width);
                tb2.setY(height);
                tb2.setMipmaps(false);
                aOut = Allocation.createTyped(mRS, tb2.create(), 0);
                timings.addSplit("Prepare for aOut");
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                timings.addSplit("Create Bitmap");
            }

            ain.copyFrom(nv21);
            timings.addSplit("ain copyFrom");
            mYuvToRgb.setInput(ain);
            timings.addSplit("setInput ain");
            mYuvToRgb.forEach(aOut);
            timings.addSplit("NV21 to ARGB forEach");
            aOut.copyTo(bitmap);
            timings.addSplit("Allocation to Bitmap");
        } catch (Exception var10) {
            YuvImage yuvImage = new YuvImage(nv21, 17, width, height, (int[]) null);
            timings.addSplit("NV21 bytes to YuvImage");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(rect, 50, baos);
            byte[] cur = baos.toByteArray();
            timings.addSplit("YuvImage crop and compress to Jpeg Bytes");
            bitmap = BitmapFactory.decodeByteArray(cur, 0, cur.length);
            timings.addSplit("Jpeg Bytes to Bitmap");
        }

        timings.dumpToLog();
        return bitmap;
    }

    public static Bitmap NV21ToRGBABitmap(byte[] nv21, int width, int height) {
        YuvImage yuvImage = new YuvImage(nv21, 17, width, height, (int[]) null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, baos);
        byte[] cur = baos.toByteArray();
        return BitmapFactory.decodeByteArray(cur, 0, cur.length);
    }

    public static void drawFaceRect(Canvas canvas, Rect rect, int width, int height, boolean frontCamera) {
        if (canvas != null) {
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            int strokeWidth = Math.max(width / 240, 2);
            paint.setStrokeWidth((float) strokeWidth);
            if (frontCamera) {
                int left = rect.left;
                rect.left = width - rect.right;
                rect.right = width - left;
            }

            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(rect, paint);
        }
    }

    public static void drawPoints(Canvas canvas, Paint paint, PointF[] points, float[] visibles, int width, int height, boolean frontCamera) {
        if (canvas != null) {
            int strokeWidth = Math.max(width / 240, 2);

            for (int i = 0; i < points.length; ++i) {
                PointF p = points[i];
                if (frontCamera) {
                    p.x = (float) width - p.x;
                }

                if ((double) visibles[i] < 0.5D) {
                    paint.setColor(Color.rgb(255, 20, 20));
                } else {
                    paint.setColor(Color.rgb(57, 168, 243));
                }

                canvas.drawCircle(p.x, p.y, (float) strokeWidth, paint);
            }

            paint.setColor(Color.rgb(57, 138, 243));
        }
    }

    public static Rect RotateDeg90(Rect rect, int width, int height) {
        int left = rect.left;
        rect.left = height - rect.bottom;
        rect.bottom = rect.right;
        rect.right = height - rect.top;
        rect.top = left;
        return rect;
    }

    public static Rect RotateDeg270(Rect rect, int width, int height) {
        int left = rect.left;
        rect.left = rect.top;
        rect.top = width - rect.right;
        rect.right = rect.bottom;
        rect.bottom = width - left;
        return rect;
    }

    public static PointF RotateDeg90(PointF point, int width, int height) {
        float x = point.x;
        point.x = (float) height - point.y;
        point.y = x;
        return point;
    }

    public static PointF RotateDeg270(PointF point, int width, int height) {
        float x = point.x;
        point.x = point.y;
        point.y = (float) width - x;
        return point;
    }

    public static Bitmap getRotateBitmap(Bitmap bitmap, int rotation) {
        if (null != bitmap && !bitmap.isRecycled()) {
            Matrix matrix = new Matrix();
            matrix.postRotate((float) rotation);
            Bitmap cropBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
            return cropBitmap;
        } else {
            return null;
        }
    }

    public static void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
            bitmap = null;
        }
    }

    public static void copyModelIfNeed(String modelName, Context mContext) {
        String path = getModelPath(modelName, mContext);
        if (path != null) {
            File modelFile = new File(path);
            if (!modelFile.exists()) {
                try {
                    if (modelFile.exists()) {
                        modelFile.delete();
                    }

                    modelFile.createNewFile();
                    InputStream in = mContext.getApplicationContext().getAssets().open(modelName);
                    if (in == null) {
                        Log.e("MultiTrack106", "the src module is not existed");
                    }

                    OutputStream out = new FileOutputStream(modelFile);
                    byte[] buffer = new byte[4096];

                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        out.write(buffer, 0, n);
                    }

                    in.close();
                    out.close();
                } catch (IOException var8) {
                    modelFile.delete();
                }
            }
        }

    }

    public static String getModelPath(String modelName, Context mContext) {
        String path = null;
        File dataDir = mContext.getApplicationContext().getExternalFilesDir((String) null);
        if (dataDir != null) {
            path = dataDir.getAbsolutePath() + File.separator + modelName;
        }

        return path;
    }


    public static void copyFilesFromAssets(Context context, String oldPath, String newPath) {
        try {
            String[] fileNames = context.getAssets().list(oldPath);
            if ((fileNames != null ? fileNames.length : 0) > 0) {
                // directory
                File file = new File(newPath);
                if (!file.mkdir()) {
                    Log.d("mkdir", "can't make folder");

                }

                for (String fileName : fileNames) {
                    copyFilesFromAssets(context, oldPath + "/" + fileName,
                            newPath + "/" + fileName);
                }
            } else {
                // file
                InputStream is = context.getAssets().open(oldPath);
                FileOutputStream fos = new FileOutputStream(new File(newPath));
                byte[] buffer = new byte[1024];
                int byteCount;
                while ((byteCount = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, byteCount);
                }
                fos.flush();
                is.close();
                fos.close();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void initModelFiles(Context context) {
        String assetPath = "ZeuseesFaceTracking";
        String sdcardPath = Environment.getExternalStorageDirectory() + File.separator + assetPath;
        copyFilesFromAssets(context, assetPath, sdcardPath);
    }

    /* Checks if external storage is available for read and write */
    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /* Checks if external storage is available to at least read */
    public static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

    /**
     * Checks if the app has permission to write to device storage or open camera
     * If the app does not has permission then the user will be prompted to grant permissions
     */
    public static boolean verifyPermissions(Activity activity, String[] permissions, int requestCode) {
        for (String s : permissions) {
            if (ActivityCompat.checkSelfPermission(activity, s) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        activity,
                        permissions,
                        requestCode
                );
                return false;
            }
        }
        return true;
    }

    public static byte[] convertYUV420ToNV21(Image imgYUV420) {
        byte[] rez;

        ByteBuffer buffer0 = imgYUV420.getPlanes()[0].getBuffer();
        ByteBuffer buffer2 = imgYUV420.getPlanes()[2].getBuffer();
        int buffer0_size = buffer0.remaining();
        int buffer2_size = buffer2.remaining();
        rez = new byte[buffer0_size + buffer2_size];

        buffer0.get(rez, 0, buffer0_size);
        buffer2.get(rez, buffer0_size, buffer2_size);

        return rez;
    }

    /**
     * Width < Height: 480 x 640
     * @param data
     * @param imageWidth
     * @param imageHeight
     * @return
     */
    public static byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // Rotate the Y luma
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i--;
            }
        }
        return yuv;
    }

    private static byte[] rotateYUV420Degree180(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        int i = 0;
        int count = 0;
        for (i = imageWidth * imageHeight - 1; i >= 0; i--) {
            yuv[count] = data[i];
            count++;
        }
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (i = imageWidth * imageHeight * 3 / 2 - 1; i >= imageWidth
                * imageHeight; i -= 2) {
            yuv[count++] = data[i - 1];
            yuv[count++] = data[i];
        }
        return yuv;
    }

    public static byte[] rotateYUV420Degree270(byte[] data, int imageWidth,
                                               int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        int nWidth = 0, nHeight = 0;
        int wh = 0;
        int uvHeight = 0;
        if (imageWidth != nWidth || imageHeight != nHeight) {
            nWidth = imageWidth;
            nHeight = imageHeight;
            wh = imageWidth * imageHeight;
            uvHeight = imageHeight >> 1;// uvHeight = height / 2
        }
        // ??Y
        int k = 0;
        for (int i = 0; i < imageWidth; i++) {
            int nPos = 0;
            for (int j = 0; j < imageHeight; j++) {
                yuv[k] = data[nPos + i];
                k++;
                nPos += imageWidth;
            }
        }
        for (int i = 0; i < imageWidth; i += 2) {
            int nPos = wh;
            for (int j = 0; j < uvHeight; j++) {
                yuv[k] = data[nPos + i];
                yuv[k + 1] = data[nPos + i + 1];
                k += 2;
                nPos += imageWidth;
            }
        }
        return rotateYUV420Degree180(yuv, imageWidth, imageHeight);
    }

    public static void decodeYUV420(int[] rgba, byte[] yuv420, int width, int height) {
        int half_width = (width + 1) >> 1;
        int half_height = (height +1) >> 1;
        int y_size = width * height;
        int uv_size = half_width * half_height;


        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {


                double y = (yuv420[j * width + i]) & 0xff;
                double v = (yuv420[y_size + (j >> 1) * half_width + (i>>1)]) & 0xff;
                double u = (yuv420[y_size + uv_size + (j >> 1) * half_width + (i>>1)]) & 0xff;


                double r;
                double g;
                double b;


                r = y + 1.402 * (u-128);
                g = y - 0.34414*(v-128) - 0.71414*(u-128);
                b = y + 1.772*(v-128);


                if (r < 0) r = 0;
                else if (r > 255) r = 255;
                if (g < 0) g = 0;
                else if (g > 255) g = 255;
                if (b < 0) b = 0;
                else if (b > 255) b = 255;


                int ir = (int)r;
                int ig = (int)g;
                int ib = (int)b;
                rgba[j * width + i] = 0xff000000 | (ir << 16) | (ig << 8) | ib;
            }
        }
    }
}
