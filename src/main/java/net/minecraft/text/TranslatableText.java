/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill stub for net.minecraft.text.TranslatableText.
 *
 * This class was removed in Minecraft 1.19, replaced by Text.translatable().
 * Mods targeting 1.18 and earlier used this class directly.
 * The stub prevents ClassNotFoundException for legacy mod code.
 */
package net.minecraft.text;

/**
 * Minimal stub for the removed TranslatableText class.
 *
 * In versions prior to 1.19, TranslatableText extended BaseText and
 * held a translation key with optional format arguments. Since BaseText
 * is also removed, this stub extends Object directly.
 */
public class TranslatableText {

    private final String key;
    private final Object[] args;

    public TranslatableText(String key, Object... args) {
        this.key = key != null ? key : "";
        this.args = args != null ? args : new Object[0];
    }

    /**
     * Returns the translation key.
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the format arguments.
     */
    public Object[] getArgs() {
        return args;
    }

    /**
     * Returns the raw key as the string representation,
     * since actual translation is unavailable in the stub.
     */
    public String getString() {
        return key;
    }

    /**
     * Returns null since OrderedText infrastructure is unavailable.
     */
    public Object asOrderedText() {
        return null;
    }

    /**
     * Returns this instance as a shallow copy stand-in.
     */
    public TranslatableText copy() {
        return this;
    }

    @Override
    public String toString() {
        return "TranslatableText{key='" + key + "'}";
    }
}
