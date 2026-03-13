/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.minecraftforge.common.util;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class LazyOptional<T> {
    private final Supplier<T> supplier;
    private T value;
    private boolean resolved;

    private LazyOptional(Supplier<T> supplier) { this.supplier = supplier; }

    public static <T> LazyOptional<T> of(Supplier<T> supplier) { return new LazyOptional<>(supplier); }

    public static <T> LazyOptional<T> empty() { return new LazyOptional<>(() -> null); }

    public boolean isPresent() { resolve(); return value != null; }

    public void ifPresent(Consumer<? super T> consumer) { resolve(); if (value != null) consumer.accept(value); }

    public <U> Optional<U> map(Function<? super T, ? extends U> mapper) { resolve(); return value != null ? Optional.ofNullable(mapper.apply(value)) : Optional.empty(); }

    public T orElse(T other) { resolve(); return value != null ? value : other; }

    public Optional<T> resolve() { if (!resolved) { value = supplier != null ? supplier.get() : null; resolved = true; } return Optional.ofNullable(value); }

    public void invalidate() { value = null; resolved = false; }
}
