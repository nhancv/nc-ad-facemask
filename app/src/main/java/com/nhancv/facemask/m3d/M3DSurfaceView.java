package com.nhancv.facemask.m3d;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import org.andresoviedo.android_3d_model_engine.model.Camera;

public class M3DSurfaceView extends GLSurfaceView {

    private M3DRenderer renderer;

    /**
     * Background GL clear color. Default is light gray
     */
    private float[] backgroundColor = new float[]{0.2f, 0.2f, 0.2f, 1.0f};

    private M3DSceneLoader scene;
    private Camera camera;

    public M3DSurfaceView(Context context) {
        this(context, null);
    }

    public M3DSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Create an OpenGL ES 2.0 context.
        setEGLContextClientVersion(2);
        camera = new Camera();
        // This is the actual renderer of the 3D space
        renderer = new M3DRenderer(this);
        setRenderer(renderer);
    }

    public void setupRender(M3DSceneLoader sceneLoader) {
        this.scene = sceneLoader;
    }

    public M3DRenderer getModelRenderer(){
        return renderer;
    }

    public float[] getBackgroundColor() {
        return backgroundColor;
    }

    public M3DSceneLoader getScene() {
        return scene;
    }

    public Camera getCamera() {
        return camera;
    }
}
