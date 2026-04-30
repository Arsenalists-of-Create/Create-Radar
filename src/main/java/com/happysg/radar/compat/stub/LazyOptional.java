package com.happysg.radar.compat.stub;

public class LazyOptional<T> {
    private T instance;
    private LazyOptional(T instance) { this.instance = instance; }
    public static <T> LazyOptional<T> of(java.util.function.Supplier<T> instanceSupplier) { return new LazyOptional<>(instanceSupplier.get()); }
    public static <T> LazyOptional<T> empty() { return new LazyOptional<>(null); }
    public void invalidate() {}
    public <X> LazyOptional<X> cast() { return (LazyOptional<X>) this; }
}
