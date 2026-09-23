package io.github.aw1y2z.sesame.data.modelFieldExt;


import io.github.aw1y2z.sesame.data.ModelField;

public class TextModelField extends ModelField<String> {

    public TextModelField(String code, String name, String value) {
        super(code, name, value);
    }

    @Override
    public String getType() {
        return "TEXT";
    }

    @Override
    public String getConfigValue() {
        return value;
    }

    @Override
    public void setConfigValue(String configValue) {
        value = configValue;
    }

    public static class ReadOnlyTextModelField extends TextModelField {

        public ReadOnlyTextModelField(String code, String name, String value) {
            super(code, name, value);
        }

        @Override
        public String getType() {
            return "READ_TEXT";
        }

        @Override
        public String getValue() {
            return null;
        }

        @Override
        public void setConfigValue(String configValue) {
        }

    }

    public static class UrlTextModelField extends ReadOnlyTextModelField {

        public UrlTextModelField(String code, String name, String value) {
            super(code, name, value);
        }

        @Override
        public String getType() {
            return "URL_TEXT";
        }

    }

}
