package com.retromod.shim.api.forge.embedded;
import java.util.Optional;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
public class LazyOptionalShim<T> {
    private final Supplier<T> supplier;
    private T value;
    private boolean resolved = false;
    private LazyOptionalShim(Supplier<T> supplier) { this.supplier = supplier; }
    public static <T> LazyOptionalShim<T> of(Supplier<T> supplier) { return new LazyOptionalShim<>(supplier); }
    public static <T> LazyOptionalShim<T> empty() { return new LazyOptionalShim<>(() -> null); }
    public T orElse(T other) { resolve(); return value != null ? value : other; }
    public T orElseThrow() { resolve(); if (value == null) throw new NoSuchElementException(); return value; }
    public void ifPresent(Consumer<? super T> consumer) { resolve(); if (value != null) consumer.accept(value); }
    public <U> LazyOptionalShim<U> map(Function<? super T, ? extends U> mapper) {
        resolve(); if (value == null) return empty(); return LazyOptionalShim.of(() -> mapper.apply(value));
    }
    public Optional<T> resolve() {
        if (!resolved) { resolved = true; try { value = supplier.get(); } catch (Exception e) { value = null; } }
        return Optional.ofNullable(value);
    }
    public boolean isPresent() { resolve(); return value != null; }
    public void invalidate() { resolved = false; value = null; }
}
