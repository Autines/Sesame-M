package io.github.aw1y2z.sesame.data.modelFieldExt;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.aw1y2z.sesame.data.ModelField;

public class EmptyModelField extends ModelField<Object> {

    private final Runnable clickRunner;

    public Runnable getClickRunner() {
        return clickRunner;
    }

    public EmptyModelField(String code, String name) {
        super(code, name, null);
        this.clickRunner = null;
    }

    public EmptyModelField(String code, String name, Runnable clickRunner) {
        super(code, name, null);
        this.clickRunner = clickRunner;
    }

    @Override
    public String getType() {
        return "EMPTY";
    }

    @Override
    public void setObjectValue(Object value) {
    }

}
