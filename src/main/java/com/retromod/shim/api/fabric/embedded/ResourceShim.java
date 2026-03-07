package com.retromod.shim.api.fabric.embedded;
import java.lang.reflect.Method;
public class ResourceShim {
    public static Object getHelper(Object resourceType) {
        try {
            Class<?> helper = Class.forName("net.fabricmc.fabric.api.resource.ResourceManagerHelper");
            Method get = helper.getMethod("get", Class.forName("net.minecraft.resource.ResourceType"));
            return get.invoke(null, resourceType);
        } catch (Exception e) { return null; }
    }
}
