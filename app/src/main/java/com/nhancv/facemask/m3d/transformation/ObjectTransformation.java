package com.nhancv.facemask.m3d.transformation;

public class ObjectTransformation {
    Rotation rotation;
    Scale scale;
    Translation translation;
    public ObjectTransformation(Rotation rotation_, Scale scale_, Translation translation_){
        rotation = rotation_;
        scale = scale_;
        translation = translation_;
    }

    public float [] getRotationValue()
    {
        if(rotation!=null)
            return rotation.rotationValue();
        return null;
    }

    public float[] getScaleValue()
    {
        if(scale!=null)
            return scale.scaleValue();
        return null;
    }

    public float[] getTranslationValue()
    {
        if(translation!=null)
            return translation.translationValue();
        return null;
    }
}
