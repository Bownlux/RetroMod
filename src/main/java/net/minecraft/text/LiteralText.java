/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill stub for net.minecraft.text.LiteralText.
 *
 * This class was removed in Minecraft 1.19, replaced by Text.literal().
 * Mods targeting 1.18 and earlier used this class directly.
 * The stub prevents ClassNotFoundException for legacy mod code.
 */
package net.minecraft.text;

/**
 * Minimal stub for the removed LiteralText class.
 *
 * In versions prior to 1.19, LiteralText extended BaseText and held
 * a raw string literal. Since BaseText is also removed, this stub
 * extends Object directly and provides the essential API surface.
 */
public class LiteralText {

    private final String string;

    public LiteralText(String string) {
        this.string = string != null ? string : "";
    }

    /**
     * Returns the literal string content.
     */
    public String getString() {
        return string;
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
    public LiteralText copy() {
        return this;
    }

    @Override
    public String toString() {
        return "LiteralText{string='" + string + "'}";
    }
}
