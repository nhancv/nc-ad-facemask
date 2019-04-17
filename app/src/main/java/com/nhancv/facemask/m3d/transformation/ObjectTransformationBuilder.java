package com.nhancv.facemask.m3d.transformation;

public class ObjectTransformationBuilder {
    private Rotation rotation;
    private Scale scale;
    private Translation translation;

    public ObjectTransformationBuilder  setRotation(Rotation rotation) {
        this.rotation = rotation;
        return this;
    }

    public ObjectTransformationBuilder setScale(Scale scale){
        this.scale = scale;
        return this;
    }

    public ObjectTransformationBuilder setTranslation(Translation translation){
        this.translation = translation;
        return this;
    }

    public ObjectTransformation build(){
        return new ObjectTransformation(this.rotation,this.scale,this.translation);
    }
}
