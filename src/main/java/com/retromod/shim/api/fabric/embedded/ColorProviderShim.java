package com.retromod.shim.api.fabric.embedded;
public class ColorProviderShim {
    public static Object getBlockRegistry() {
        try {
            Class<?> registry = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry");
            return registry.getField("BLOCK").get(null);
        } catch (Exception e) { return null; }
    }
}
