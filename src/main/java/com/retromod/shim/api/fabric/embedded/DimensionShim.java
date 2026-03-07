package com.retromod.shim.api.fabric.embedded;
import java.lang.reflect.Method;
public class DimensionShim {
    public static Object teleport(Object entity, Object world, Object target) {
        try {
            Class<?> dims = Class.forName("net.fabricmc.fabric.api.dimension.v1.FabricDimensions");
            for (Method m : dims.getMethods()) {
                if (m.getName().equals("teleport")) {
                    return m.invoke(null, entity, world, target);
                }
            }
        } catch (Exception e) { throw new RuntimeException("Dimension teleport failed", e); }
        return entity;
    }
}
