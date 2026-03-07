/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * This class provides a shim implementation for the removed NbtType utility.
 * Original source: Fabric API fabric-api-base module
 * 
 * When a mod calls NbtType.getType(b), RetroMod redirects it here.
 */
package com.retromod.shim.fabric.embedded;

/**
 * Shim for net.fabricmc.fabric.api.util.NbtType
 * 
 * This class was removed in Fabric API 0.93.0+
 * Original purpose: NBT type constants and utilities
 */
public final class NbtTypeShim {
    
    // NBT Type Constants (from original NbtType class)
    public static final byte END = 0;
    public static final byte BYTE = 1;
    public static final byte SHORT = 2;
    public static final byte INT = 3;
    public static final byte LONG = 4;
    public static final byte FLOAT = 5;
    public static final byte DOUBLE = 6;
    public static final byte BYTE_ARRAY = 7;
    public static final byte STRING = 8;
    public static final byte LIST = 9;
    public static final byte COMPOUND = 10;
    public static final byte INT_ARRAY = 11;
    public static final byte LONG_ARRAY = 12;
    
    private NbtTypeShim() {
        // Utility class
    }
    
    /**
     * Get the integer type code for an NBT type byte.
     * Original: NbtType.getType(byte)
     */
    public static int getType(byte type) {
        return type & 0xFF;
    }
    
    /**
     * Check if a byte represents a valid NBT type.
     */
    public static boolean isValidType(byte type) {
        int t = type & 0xFF;
        return t >= END && t <= LONG_ARRAY;
    }
    
    /**
     * Get the name of an NBT type.
     */
    public static String getTypeName(byte type) {
        return switch (type) {
            case END -> "TAG_End";
            case BYTE -> "TAG_Byte";
            case SHORT -> "TAG_Short";
            case INT -> "TAG_Int";
            case LONG -> "TAG_Long";
            case FLOAT -> "TAG_Float";
            case DOUBLE -> "TAG_Double";
            case BYTE_ARRAY -> "TAG_Byte_Array";
            case STRING -> "TAG_String";
            case LIST -> "TAG_List";
            case COMPOUND -> "TAG_Compound";
            case INT_ARRAY -> "TAG_Int_Array";
            case LONG_ARRAY -> "TAG_Long_Array";
            default -> "UNKNOWN";
        };
    }
}
