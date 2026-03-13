/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill stub for net.minecraft.block.MaterialColor.
 *
 * This class was removed in Minecraft 1.20, replaced by MapColor.
 * Mods targeting 1.19 and earlier used this class directly.
 * The stub prevents ClassNotFoundException for legacy mod code.
 */
package net.minecraft.block;

/**
 * Minimal stub for the removed MaterialColor class.
 *
 * In versions prior to 1.20, MaterialColor provided static color
 * constants used by blocks for map rendering. Each instance had
 * a numeric ID and an RGB color value.
 */
public class MaterialColor {

    /**
     * The RGB color value.
     */
    public final int color;

    /**
     * The numeric identifier for this color.
     */
    public final int id;

    private MaterialColor(int id, int color) {
        this.id = id;
        this.color = color;
    }

    // Standard color constants
    public static final MaterialColor CLEAR = new MaterialColor(0, 0);
    public static final MaterialColor PALE_GREEN = new MaterialColor(1, 0x7FB238);
    public static final MaterialColor PALE_YELLOW = new MaterialColor(2, 0xF7E9A3);
    public static final MaterialColor WHITE_GRAY = new MaterialColor(3, 0xC7C7C7);
    public static final MaterialColor BRIGHT_RED = new MaterialColor(4, 0xFF0000);
    public static final MaterialColor PALE_PURPLE = new MaterialColor(5, 0xA0A0FF);
    public static final MaterialColor IRON = new MaterialColor(6, 0xA7A7A7);
    public static final MaterialColor DARK_GREEN = new MaterialColor(7, 0x007C00);
    public static final MaterialColor WHITE = new MaterialColor(8, 0xFFFFFF);
    public static final MaterialColor LIGHT_BLUE_GRAY = new MaterialColor(9, 0xA4A8B8);
    public static final MaterialColor DIRT = new MaterialColor(10, 0x976D4D);
    public static final MaterialColor STONE = new MaterialColor(11, 0x707070);
    public static final MaterialColor WATER = new MaterialColor(12, 0x4040FF);
    public static final MaterialColor WOOD = new MaterialColor(13, 0x8F7748);
    public static final MaterialColor SPRUCE = new MaterialColor(14, 0x815631);
    public static final MaterialColor RED = new MaterialColor(15, 0xB4282A);
    public static final MaterialColor BLACK = new MaterialColor(16, 0x252525);
    public static final MaterialColor GOLD = new MaterialColor(17, 0xFAEE4D);
    public static final MaterialColor DIAMOND = new MaterialColor(18, 0x5CDBD5);
    public static final MaterialColor LAPIS = new MaterialColor(19, 0x4A80FF);
    public static final MaterialColor EMERALD = new MaterialColor(20, 0x00D93A);
    public static final MaterialColor BROWN = new MaterialColor(21, 0x6A3400);
    public static final MaterialColor BLUE = new MaterialColor(22, 0x3C44AA);
    public static final MaterialColor PURPLE = new MaterialColor(23, 0x7525A0);
    public static final MaterialColor CYAN = new MaterialColor(24, 0x2D7C9D);
    public static final MaterialColor ORANGE = new MaterialColor(25, 0xD87F33);
    public static final MaterialColor LIGHT_GRAY = new MaterialColor(26, 0x9D9D97);
    public static final MaterialColor PINK = new MaterialColor(27, 0xF27FA5);
    public static final MaterialColor LIME = new MaterialColor(28, 0x7FCC19);
    public static final MaterialColor YELLOW = new MaterialColor(29, 0xE5E533);
    public static final MaterialColor GREEN = new MaterialColor(30, 0x667F33);
    public static final MaterialColor MAGENTA = new MaterialColor(31, 0xB24CD8);

    @Override
    public String toString() {
        return "MaterialColor{id=" + id + ", color=0x" + Integer.toHexString(color) + "}";
    }
}
