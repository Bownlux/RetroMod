/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.minecraftforge.registries;

import java.util.Optional;
import java.util.function.Supplier;

public class RegistryObject<T> implements Supplier<T> {
    private T value;

    private RegistryObject() {}

    public static <T> RegistryObject<T> create(Object name, Object registry) { return new RegistryObject<>(); }

    public T get() { return value; }

    public Optional<T> asOptional() { return Optional.ofNullable(value); }

    public boolean isPresent() { return value != null; }

    public Object getId() { return null; }
}
