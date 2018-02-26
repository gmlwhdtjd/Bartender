package com.helloworld.bartender.Item;

/**
 * Created by 김현식 on 2018-02-05.
 */

public class Item {
    private long id;
    private String filter_name;
    private float blur;
    private float focus;
    private float aberration;
    private float noiseSize;
    private float noiseIntensity;


    public Item() {

    }

    public Item(String filter_name, float blur, float focus, float aberration, float noiseSize, float noiseIntensity) {
        this.filter_name = filter_name;
        this.blur = blur;
        this.focus = focus;
        this.aberration = aberration;
        this.noiseSize = noiseSize;
        this.noiseIntensity = noiseIntensity;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFilter_name() {
        return filter_name;
    }

    public void setFilter_name(String filter_name) {
        this.filter_name = filter_name;
    }

    public float getBlur() {
        return blur;
    }

    public void setBlur(float blur) {
        this.blur = blur;
    }

    public float getFocus() {
        return focus;
    }

    public void setFocus(float focus) {
        this.focus = focus;
    }

    public float getAberration() {
        return aberration;
    }

    public void setAberration(float aberration) {
        this.aberration = aberration;
    }

    public float getNoiseSize() {
        return noiseSize;
    }

    public void setNoiseSize(float noiseSize) {
        this.noiseSize = noiseSize;
    }

    public float getNoiseIntensity() {
        return noiseIntensity;
    }

    public void setNoiseIntensity(float noiseIntensity) {
        this.noiseIntensity = noiseIntensity;
    }

}
