package com.retromod.shim.api.forge.embedded;
import java.util.concurrent.Callable;
public class CapabilityManagerShim {
    public static void register(Class<?> type, Object storage, Callable<?> factory) {
        System.out.println("[RetroMod] Legacy capability registration for: " + type.getName());
    }
}
