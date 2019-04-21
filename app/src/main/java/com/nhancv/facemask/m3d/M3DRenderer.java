package com.nhancv.facemask.m3d;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.nhancv.facemask.m3d.transformation.ObjectTransformation;

import org.andresoviedo.android_3d_model_engine.animation.Animator;
import org.andresoviedo.android_3d_model_engine.drawer.DrawerFactory;
import org.andresoviedo.android_3d_model_engine.drawer.Object3DImpl;
import org.andresoviedo.android_3d_model_engine.model.AnimatedModel;
import org.andresoviedo.android_3d_model_engine.model.Camera;
import org.andresoviedo.android_3d_model_engine.model.Object3D;
import org.andresoviedo.android_3d_model_engine.model.Object3DData;
import org.andresoviedo.android_3d_model_engine.services.Object3DBuilder;
import org.andresoviedo.util.android.GLUtil;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class M3DRenderer implements GLSurfaceView.Renderer {
    private final static String TAG = M3DRenderer.class.getName();
    // frustrum - nearest pixel
    private static final float near = 1f;
    // frustrum - fartest pixel
    private static final float far = 100f;
    // 3D matrices to project our 3D world
    private final float[] modelProjectionMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    // mvpMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] mvpMatrix = new float[16];
    // light position required to render with lighting
    private final float[] lightPosInEyeSpace = new float[4];
    boolean visible = false;
    // 3D window (parent component)
    private M3DSurfaceView main;
    // width of the screen
    private int width;
    // height of the screen
    private int height;
    private DrawerFactory drawer;
    // The wireframe associated shape (it should be made of lines only)
    private Map<Object3DData, Object3DData> wireframes = new HashMap<Object3DData, Object3DData>();
    // The loaded textures
    private Map<byte[], Integer> textures = new HashMap<byte[], Integer>();
    // The corresponding opengl bounding boxes and drawer
    private Map<Object3DData, Object3DData> boundingBoxes = new HashMap<Object3DData, Object3DData>();
    // The corresponding opengl bounding boxes
    private Map<Object3DData, Object3DData> normals = new HashMap<Object3DData, Object3DData>();
    private Map<Object3DData, Object3DData> skeleton = new HashMap<>();
    /**
     * Whether the info of the model has been written to console log
     */
    private boolean infoLogged = false;
    /**
     * Skeleton Animator
     */
    private Animator animator = new Animator();
    private List<ObjectTransformation> objectTransformationList = new ArrayList<>();

    /**
     * Construct a new renderer for the specified surface view
     *
     * @param modelSurfaceView the 3D window
     */
    public M3DRenderer(M3DSurfaceView modelSurfaceView) {
        this.main = modelSurfaceView;
    }

    public void setObjectTransformationList(List<ObjectTransformation> objectTransformationList) {
        this.objectTransformationList = objectTransformationList;
    }

    public float getNear() {
        return near;
    }
//    int value = -1;
//    public void setRotate(float angle,float x, float y, float z) {
//        Matrix.rotateM(modelProjectionMatrix, 0,angle, x, y, z);
//    }
//    public void setScale(float x, float y ,float z){
//        //default is 1, 1, 1
//        if(x ==0) {
//            x =1;
//        }
//        if(y == 0) {
//            y = 1;
//        }
//        if(z==0) {
//            z =1;
//        }
//        Matrix.scaleM(modelProjectionMatrix, 0, x, y, z);
//    }
//    public void setTranslate(float x, float y, float z) {
//       Matrix.translateM(modelProjectionMatrix, 0, x, y, z);
//    }

    public float getFar() {
        return far;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // Set the background frame color
        float[] backgroundColor = main.getBackgroundColor();
//        GLES20.glClearColor(backgroundColor[0], backgroundColor[1], backgroundColor[2], backgroundColor[3]);
        GLES20.glDisable(GL10.GL_DITHER);
        GLES20.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);

        GLES20.glClearColor(0, 0, 0, 0);

        // Use culling to remove back faces.
        // Don't remove back faces so we can see them
        // GLES20.glEnable(GLES20.GL_CULL_FACE);

        // Enable depth testing for hidden-surface elimination.
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Enable blending for combining colors when there is transparency
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // This component will draw the actual models using OpenGL
        drawer = new DrawerFactory();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        this.width = width;
        this.height = height;

        // Adjust the viewport based on geometry changes, such as screen rotation
        GLES20.glViewport(0, 0, width, height);

        // INFO: Set the camera position (View matrix)
        // The camera has 3 vectors (the position, the vector where we are looking at, and the up position (sky)
        Camera camera = this.main.getCamera();
        Matrix.setLookAtM(modelViewMatrix, 0, camera.xPos, camera.yPos, camera.zPos, camera.xView, camera.yView,
                camera.zView, camera.xUp, camera.yUp, camera.zUp);

        // the projection matrix is the 3D virtual space (cube) that we want to project
        float ratio = (float) width / height;
        Log.d(TAG, "projection: [" + -ratio + "," + ratio + ",-1,1]-near/far[1,10]");
        Matrix.frustumM(modelProjectionMatrix, 0, -ratio, ratio, -1, 1, getNear(), getFar());

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mvpMatrix, 0, modelProjectionMatrix, 0, modelViewMatrix, 0);

    }

    public void performTranformation(Object3DData objData, ObjectTransformation transformation) {
        float[] rotation = transformation.getRotationValue();
        if (rotation != null)
            objData.setRotation(rotation);
        float[] translation = transformation.getTranslationValue();
        if (translation != null)
            objData.setPosition(translation);
        float[] scale = transformation.getScaleValue();
        if (scale != null)
            objData.setScale(scale);
    }

    public void setObjectVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public void onDrawFrame(GL10 unused) {

        // Draw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        M3DSceneLoader scene = main.getScene();
        if (scene == null) {
            // scene not ready
            return;
        }

        // animate scene
        scene.onDrawFrame();

        // recalculate mvp matrix according to where we are looking at now
        Camera camera = scene.getCamera();

        if (camera.hasChanged()) {
            Matrix.setLookAtM(modelViewMatrix, 0, camera.xPos, camera.yPos, camera.zPos, camera.xView, camera.yView,
                    camera.zView, camera.xUp, camera.yUp, camera.zUp);
            // Log.d("Camera", "Changed! :"+camera.ToStringVector());
            Matrix.multiplyMM(mvpMatrix, 0, modelProjectionMatrix, 0, modelViewMatrix, 0);
            camera.setChanged(false);
        }

        // draw light
        if (scene.isDrawLighting()) {

            Object3DImpl lightBulbDrawer = (Object3DImpl) drawer.getPointDrawer();

            float[] lightModelViewMatrix = lightBulbDrawer.getMvMatrix(lightBulbDrawer.getMMatrix(scene.getLightBulb()), modelViewMatrix);

            // Calculate position of the light in eye space to support lighting
            Matrix.multiplyMV(lightPosInEyeSpace, 0, lightModelViewMatrix, 0, scene.getLightPosition(), 0);

            // Draw a point that represents the light bulb
            //lightBulbDrawer.draw(scene.getLightBulb(), modelProjectionMatrix, modelViewMatrix, -1, lightPosInEyeSpace);
        }
      /*  Object3DData axis = Object3DBuilder.buildAxis().setId("axis");
        axis.setColor(new float[]{1.0f,0,0,1.0f});
        scene.addObject(axis);*/
        List<Object3DData> objects = scene.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            Object3DData objData = null;
            ObjectTransformation transformation = null;

            try {
                objData = objects.get(i);
                objData.setVisible(visible);

                if (objData.isVisible()) {
                    if (objectTransformationList != null && i < objectTransformationList.size()) {
                        transformation = objectTransformationList.get(i);
                        //perform tranformation with objectData using tranformation
                        performTranformation(objData, transformation);
                        //objData.center
                    }
                    //objData.setColor(new float[]{1.0f,0,0,1.0f});
                    //objData.setPosition(new float[]{0,1.0f,0});
                    //objData.setRotation()
                    //objData.setScale(new float[]{1,1,1});
                    boolean changed = objData.isChanged();

                    Object3D drawerObject = drawer.getDrawer(objData, scene.isDrawTextures(), scene.isDrawLighting(),
                            scene.isDrawAnimation()); //obj

                    if (!infoLogged) {
                        Log.i("ModelRenderer", "Using drawer " + drawerObject.getClass());
                        infoLogged = true;
                    }

                    Integer textureId = textures.get(objData.getTextureData());
                    if (textureId == null && objData.getTextureData() != null) {
                        Log.i("ModelRenderer", "Loading GL Texture...");
                        ByteArrayInputStream textureIs = new ByteArrayInputStream(objData.getTextureData());
                        textureId = GLUtil.loadTexture(textureIs);
                        textureIs.close();
                        textures.put(objData.getTextureData(), textureId);
                    }

                    if (objData.getDrawMode() == GLES20.GL_POINTS) {
                        Object3DImpl lightBulbDrawer = (Object3DImpl) drawer.getPointDrawer();
                        lightBulbDrawer.draw(objData, modelProjectionMatrix, modelViewMatrix, GLES20.GL_POINTS, lightPosInEyeSpace);
                    } else if (scene.isAnaglyph()) {
                        // TODO: implement anaglyph
                    } else if (scene.isDrawWireframe() && objData.getDrawMode() != GLES20.GL_POINTS
                            && objData.getDrawMode() != GLES20.GL_LINES && objData.getDrawMode() != GLES20.GL_LINE_STRIP
                            && objData.getDrawMode() != GLES20.GL_LINE_LOOP) {
                        // Log.d("ModelRenderer","Drawing wireframe model...");
                        try {
                            // Only draw wireframes for objects having faces (triangles)
                            Object3DData wireframe = wireframes.get(objData);
                            if (wireframe == null || changed) {
                                Log.i("ModelRenderer", "Generating wireframe model...");
                                wireframe = Object3DBuilder.buildWireframe(objData);
                                wireframes.put(objData, wireframe);
                            }
                            drawerObject.draw(wireframe, modelProjectionMatrix, modelViewMatrix, wireframe.getDrawMode(),
                                    wireframe.getDrawSize(), textureId != null ? textureId : -1, lightPosInEyeSpace);
                        } catch (Error e) {
                            Log.e("ModelRenderer", e.getMessage(), e);
                        }
                    } else if (scene.isDrawPoints() || objData.getFaces() == null || !objData.getFaces().loaded()) {
                        drawerObject.draw(objData, modelProjectionMatrix, modelViewMatrix
                                , GLES20.GL_POINTS, objData.getDrawSize(),
                                textureId != null ? textureId : -1, lightPosInEyeSpace);
                    } else if (scene.isDrawSkeleton() && objData instanceof AnimatedModel && ((AnimatedModel) objData)
                            .getAnimation() != null) {
                        Object3DData skeleton = this.skeleton.get(objData);
                        if (skeleton == null) {
                            skeleton = Object3DBuilder.buildSkeleton((AnimatedModel) objData);
                            this.skeleton.put(objData, skeleton);
                        }
                        animator.update(skeleton);
                        drawerObject = drawer.getDrawer(skeleton, false, scene.isDrawLighting(), scene
                                .isDrawAnimation());
                        drawerObject.draw(skeleton, modelProjectionMatrix, modelViewMatrix, -1, lightPosInEyeSpace);
                    } else {
                        drawerObject.draw(objData, modelProjectionMatrix, modelViewMatrix,
                                textureId != null ? textureId : -1, lightPosInEyeSpace);
                    }

                    // Draw bounding box
                    if (scene.isDrawBoundingBox() || scene.getSelectedObject() == objData) {
                        Object3DData boundingBoxData = boundingBoxes.get(objData);
                        if (boundingBoxData == null || changed) {
                            boundingBoxData = Object3DBuilder.buildBoundingBox(objData);
                            boundingBoxes.put(objData, boundingBoxData);
                        }
                        Object3D boundingBoxDrawer = drawer.getBoundingBoxDrawer();
                        boundingBoxDrawer.draw(boundingBoxData, modelProjectionMatrix, modelViewMatrix, -1, null);
                    }

                    // Draw normals
                    if (scene.isDrawNormals()) {
                        Object3DData normalData = normals.get(objData);
                        if (normalData == null || changed) {
                            normalData = Object3DBuilder.buildFaceNormals(objData);
                            if (normalData != null) {
                                // it can be null if object isnt made of triangles
                                normals.put(objData, normalData);
                            }
                        }
                        if (normalData != null) {
                            Object3D normalsDrawer = drawer.getFaceNormalsDrawer();
                            normalsDrawer.draw(normalData, modelProjectionMatrix, modelViewMatrix, -1, null);
                        }
                    }
                }
                // TODO: enable this only when user wants it
                // obj3D.drawVectorNormals(result, modelViewMatrix);
            } catch (Exception ex) {
                Log.e("ModelRenderer", "There was a problem rendering the object '" + objData.getId() + "':" + ex.getMessage(), ex);
            }
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public float[] getModelProjectionMatrix() {
        return modelProjectionMatrix;
    }

    public float[] getModelViewMatrix() {
        return modelViewMatrix;
    }
}
