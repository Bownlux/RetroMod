package com.retromod.shim.api.fabric.embedded;
import java.lang.reflect.Method;
public class BiomeShim {
    public static void addFeature(Object predicate, Object step, Object feature) {
        try {
            Class<?> mods = Class.forName("net.fabricmc.fabric.api.biome.v1.BiomeModifications");
            for (Method m : mods.getMethods()) {
                if (m.getName().equals("addFeature") && m.getParameterCount() == 3) {
                    m.invoke(null, predicate, step, feature);
                    return;
                }
            }
        } catch (Exception e) { }
    }
}
