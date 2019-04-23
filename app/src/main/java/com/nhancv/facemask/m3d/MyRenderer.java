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

package com.nhancv.facemask.m3d;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.MotionEvent;

import com.nhancv.facemask.R;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.loader.LoaderOBJ;
import org.rajawali3d.loader.ParsingException;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.renderer.Renderer;

public class MyRenderer extends Renderer {

    private Object3D maskObj = new Object3D();
    private double offset = 0.005f;
    private Vector3 rotation, position;

    public MyRenderer(Context context) {
        this(context, false);
    }

    public MyRenderer(Context context, boolean registerForResources) {
        super(context, registerForResources);

    }

    public void updateRotation(Vector3 rotation) {
        this.rotation = rotation;
    }

    public void updatePosition(Vector3 position) {
        this.position = position;
        getCurrentCamera().setPosition(-position.x/250, -position.y/250, 0);
    }

    @Override
    protected void onRender(long ellapsedRealtime, double deltaTime) {
        super.onRender(ellapsedRealtime, deltaTime);
//        maskObj.setPosition(maskObj.getX() + offset, maskObj.getY() + offset, maskObj.getZ() + offset);
//        maskObj.rotate(maskObj.getRotX() + offset, maskObj.getRotY() + offset, maskObj.getRotZ() + offset, 1f);
//        if (Math.abs(maskObj.getX()) > 1) {
//            offset = -offset;
//            maskObj.setRotation(0, 0, 0);
//            maskObj.setPosition(0, 0, 0);
//        }
        if (this.rotation != null) {
            maskObj.setRotation(this.rotation);
        }
        if(this.position != null) {
//            maskObj.setPosition(this.position);
        }

    }

    @Override
    protected void initScene() {
//        DirectionalLight directionalLight = new DirectionalLight(1f, .2f, -1.0f);
//        directionalLight.setColor(1.0f, 1.0f, 1.0f);
//        directionalLight.setPower(2);
//        getCurrentScene().addLight(directionalLight);
//
//        Material material = new Material();
//        material.enableLighting(true);
//        material.setDiffuseMethod(new DiffuseMethod.Lambert());
//        material.setColor(0);
//
//        Texture earthTexture = new Texture("Earth", R.drawable.earthtruecolor_nasa_big);
//        try {
//            material.addTexture(earthTexture);
//
//        } catch (ATexture.TextureException error) {
//            Log.d("DEBUG", "TEXTURE ERROR");
//        }
//
//        Ornament mask = getVMask();
//        LoaderOBJ objParser1 = new LoaderOBJ(mContext.getResources(), mTextureManager, mask.getModelResId());
//        try {
//            objParser1.parse();
//        } catch (ParsingException e) {
//            e.printStackTrace();
//        }
//        maskObj = objParser1.getParsedObject();
//        maskObj.setScale(mask.getScale());
//        maskObj.setPosition(mask.getOffsetX(), mask.getOffsetY(), mask.getOffsetZ());
//        maskObj.setRotation(mask.getRotateX(), mask.getRotateY(), mask.getRotateZ());
//
//        maskObj.setMaterial(material);
//        getCurrentScene().addChild(maskObj);
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {

    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }


    private Ornament getPantherMask() {
        Ornament ornament = new Ornament();
        ornament.setModelResId(R.raw.panther_obj);
        ornament.setImgResId(R.drawable.ic_panther_mask);
        ornament.setScale(0.12f);
        ornament.setOffset(0, -0.1f, 0.0f);
        ornament.setRotate(0, 0, 0);
        ornament.setColor(2333);
        return ornament;
    }

    private Ornament getGlass() {
        Ornament ornament = new Ornament();
        ornament.setModelResId(R.raw.glasses_obj);
        ornament.setImgResId(R.drawable.ic_glasses);
        ornament.setScale(0.15f);
        ornament.setOffset(0, 0, 0.2f);
//        ornament.setRotate(-90.0f, 90.0f, 90.0f);
        ornament.setRotate(-90, 0, 0);
        ornament.setColor(Color.BLACK);
        return ornament;
    }

    private Ornament getMoustache() {
        Ornament ornament = new Ornament();
        ornament.setModelResId(R.raw.moustache_obj);
        ornament.setImgResId(R.drawable.ic_moustache);
        ornament.setScale(0.15f);
        ornament.setOffset(0, -0.25f, 0.2f);
        ornament.setRotate(-90.0f, 90.0f, 90.0f);
        ornament.setColor(Color.BLACK);
        return ornament;
    }

    private Ornament get3dHead() {
        Ornament ornament = new Ornament();
        ornament.setModelResId(R.raw.head_obj);
        ornament.setImgResId(R.drawable.ic_moustache);
        ornament.setScale(0.01f);
        ornament.setOffset(0, 0, 0f);
        ornament.setRotate(0, 0, 0f);
        ornament.setColor(Color.BLACK);
        return ornament;
    }

    private Ornament getVMask() {
        Ornament ornament = new Ornament();
        ornament.setModelResId(R.raw.v_mask_obj);
        ornament.setImgResId(R.drawable.ic_v_mask);
        ornament.setScale(0.15f);
        ornament.setOffset(0, 0, 0f);
        ornament.setRotate(0, 0, 0f);
        ornament.setColor(Color.BLACK);
        return ornament;
    }
}
