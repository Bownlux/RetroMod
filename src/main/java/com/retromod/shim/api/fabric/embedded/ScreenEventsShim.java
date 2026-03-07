package com.retromod.shim.api.fabric.embedded;
import java.lang.reflect.Method;
public class ScreenEventsShim {
    public static Object beforeRender(Object screen) {
        try {
            Class<?> events = Class.forName("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents");
            Method method = events.getMethod("beforeRender", Class.forName("net.minecraft.client.gui.screen.Screen"));
            return method.invoke(null, screen);
        } catch (Exception e) { return null; }
    }
}
