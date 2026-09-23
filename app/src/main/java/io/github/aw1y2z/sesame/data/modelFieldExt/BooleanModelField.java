package io.github.aw1y2z.sesame.data.modelFieldExt;


import io.github.aw1y2z.sesame.data.ModelField;

public class BooleanModelField extends ModelField<Boolean> {

    public BooleanModelField(String code, String name, Boolean value) {
        super(code, name, value);
    }

    @Override
    public String getType() {
        return "BOOLEAN";
    }

}
