package com.retromod.shim.api.fabric.embedded;
import java.lang.reflect.Method;
public class TextShim {
    public static class LiteralTextShim {
        private String content;
        public LiteralTextShim(String content) { this.content = content; }
        public static Object create(String content) {
            try {
                Class<?> text = Class.forName("net.minecraft.text.Text");
                Method literal = text.getMethod("literal", String.class);
                return literal.invoke(null, content);
            } catch (Exception e) { return content; }
        }
    }
    public static class TranslatableTextShim {
        public TranslatableTextShim(String key, Object... args) { }
        public static Object create(String key, Object... args) {
            try {
                Class<?> text = Class.forName("net.minecraft.text.Text");
                Method translatable = text.getMethod("translatable", String.class, Object[].class);
                return translatable.invoke(null, key, args);
            } catch (Exception e) { return key; }
        }
    }
}
