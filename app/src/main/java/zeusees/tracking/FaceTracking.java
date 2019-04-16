package zeusees.tracking;

public class FaceTracking {

    static {
        System.loadLibrary("zeuseesTracking-lib");
    }

    public native static void update(byte[] data, int height, int width, long session);

    public native static void initTracking(byte[] data, int height, int width, long session);

    public native static long createSession(String modelPath);

    public native static void releaseSession(long session);

    public native static int getTrackingNum(long session);

    public native static int[] getTrackingLandmarkByIndex(int index, long session);

    public native static int[] getTrackingLocationByIndex(int index, long session);

    public native static int getTrackingIDByIndex(int index, long session);


    private long session;
    private Face face;


    public FaceTracking(String pathModel) {
        session = createSession(pathModel);
    }

    protected void finalize() throws java.lang.Throwable {
        super.finalize();
        releaseSession(session);
    }

    public void faceTrackingInit(byte[] data, int height, int width) {
        initTracking(data, height, width, session);

    }

    public void faceTrackingUpdate(byte[] data, int height, int width) {

        update(data, height, width, session);
        int numbFace = getTrackingNum(session);
        if (numbFace > 0) {
            int[] landmarks = getTrackingLandmarkByIndex(0, session);
            int[] faceRect = getTrackingLocationByIndex(0, session);
            int id = getTrackingIDByIndex(0, session);
            face = new Face(faceRect[0], faceRect[1], faceRect[2], faceRect[3], landmarks, id);
        } else {
            face = null;
        }
    }

    public Face getTrackingInfo() {
        if (face != null) {
            return face.clone();
        }
        return null;

    }


}
