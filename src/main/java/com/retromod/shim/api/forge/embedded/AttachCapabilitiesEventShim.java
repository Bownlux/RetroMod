package com.retromod.shim.api.forge.embedded;
import java.lang.reflect.Method;
public class AttachCapabilitiesEventShim {
    public static void addCapability(Object event, Object resourceLocation, Object provider) {
        try {
            Method m = event.getClass().getMethod("addCapability", Class.forName("net.minecraft.resources.ResourceLocation"), Class.forName("net.minecraftforge.common.capabilities.ICapabilityProvider"));
            m.invoke(event, resourceLocation, provider);
        } catch (Exception e) {
            System.out.println("[RetroMod] Legacy capability attachment - may need data attachment conversion");
        }
    }
}
