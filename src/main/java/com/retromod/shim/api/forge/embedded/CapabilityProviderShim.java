package com.retromod.shim.api.forge.embedded;
import java.lang.reflect.Method;
import java.util.Optional;
public class CapabilityProviderShim {
    public static LazyOptionalShim<?> getCapability(Object provider, Object capability, Object direction) {
        try {
            for (Method m : provider.getClass().getMethods()) {
                if (m.getName().equals("getCapability")) {
                    Object result = m.invoke(provider, capability, direction);
                    if (result instanceof Optional) {
                        return LazyOptionalShim.of(() -> ((Optional<?>) result).orElse(null));
                    }
                    return LazyOptionalShim.of(() -> result);
                }
            }
        } catch (Exception e) { }
        return LazyOptionalShim.empty();
    }
}
