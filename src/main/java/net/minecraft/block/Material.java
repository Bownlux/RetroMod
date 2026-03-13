/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill stub for net.minecraft.block.Material.
 *
 * This class was removed in Minecraft 1.20. Block material properties
 * were inlined into BlockState / block settings directly.
 * Mods targeting 1.19 and earlier used this class directly.
 * The stub prevents ClassNotFoundException for legacy mod code.
 */
package net.minecraft.block;

/**
 * Minimal stub for the removed Material class.
 *
 * In versions prior to 1.20, Material was an enum-like class with
 * static final instances representing block material types. Each
 * instance described physical properties such as solidity, flammability,
 * and liquid state.
 */
public class Material {

    private final boolean liquid;
    private final boolean solid;
    private final boolean blocksMovement;
    private final boolean replaceable;
    private final boolean burnable;
    private final MaterialColor color;
    private final PistonBehavior pistonBehavior;

    private Material(boolean liquid, boolean solid, boolean blocksMovement,
                     boolean replaceable, boolean burnable, MaterialColor color,
                     PistonBehavior pistonBehavior) {
        this.liquid = liquid;
        this.solid = solid;
        this.blocksMovement = blocksMovement;
        this.replaceable = replaceable;
        this.burnable = burnable;
        this.color = color;
        this.pistonBehavior = pistonBehavior;
    }

    // Convenience builder for common solid, non-liquid materials
    private static Material solid(MaterialColor color) {
        return new Material(false, true, true, false, false, color, PistonBehavior.NORMAL);
    }

    private static Material solid(MaterialColor color, boolean burnable) {
        return new Material(false, true, true, false, burnable, color, PistonBehavior.NORMAL);
    }

    // --- Static material constants ---

    public static final Material AIR = new Material(
            false, false, false, true, false,
            MaterialColor.CLEAR, PistonBehavior.NORMAL);

    public static final Material STONE = solid(MaterialColor.STONE);
    public static final Material EARTH = solid(MaterialColor.DIRT);
    public static final Material WOOD = solid(MaterialColor.WOOD, true);
    public static final Material NETHER_WOOD = solid(MaterialColor.BROWN);
    public static final Material BAMBOO = solid(MaterialColor.DARK_GREEN, true);

    public static final Material METAL = new Material(
            false, true, true, false, false,
            MaterialColor.IRON, PistonBehavior.NORMAL);

    public static final Material WATER = new Material(
            true, false, false, true, false,
            MaterialColor.WATER, PistonBehavior.DESTROY);

    public static final Material LAVA = new Material(
            true, false, false, true, false,
            MaterialColor.BRIGHT_RED, PistonBehavior.DESTROY);

    public static final Material FIRE = new Material(
            false, false, false, true, false,
            MaterialColor.CLEAR, PistonBehavior.DESTROY);

    public static final Material SAND = solid(MaterialColor.PALE_YELLOW);
    public static final Material WOOL = solid(MaterialColor.WHITE, true);

    public static final Material GLASS = new Material(
            false, true, true, false, false,
            MaterialColor.CLEAR, PistonBehavior.NORMAL);

    public static final Material ICE = new Material(
            false, true, true, false, false,
            MaterialColor.LIGHT_BLUE_GRAY, PistonBehavior.NORMAL);

    public static final Material SNOW = new Material(
            false, false, false, true, false,
            MaterialColor.WHITE, PistonBehavior.DESTROY);

    public static final Material CLAY = solid(MaterialColor.LIGHT_GRAY);
    public static final Material GOURD = solid(MaterialColor.PALE_GREEN);

    public static final Material PLANT = new Material(
            false, false, false, false, false,
            MaterialColor.DARK_GREEN, PistonBehavior.DESTROY);

    public static final Material REPLACEABLE_PLANT = new Material(
            false, false, false, true, true,
            MaterialColor.DARK_GREEN, PistonBehavior.DESTROY);

    public static final Material LEAVES = new Material(
            false, true, true, false, true,
            MaterialColor.DARK_GREEN, PistonBehavior.DESTROY);

    public static final Material SPONGE = solid(MaterialColor.PALE_YELLOW);
    public static final Material COBWEB = solid(MaterialColor.WHITE);
    public static final Material REDSTONE_LAMP = solid(MaterialColor.CLEAR);
    public static final Material REPAIR_STATION = solid(MaterialColor.IRON);
    public static final Material CAKE = solid(MaterialColor.CLEAR);
    public static final Material DECORATION = solid(MaterialColor.CLEAR);

    public static final Material PISTON = new Material(
            false, true, true, false, false,
            MaterialColor.STONE, PistonBehavior.BLOCK);

    public static final Material PORTAL = new Material(
            false, false, false, false, false,
            MaterialColor.CLEAR, PistonBehavior.BLOCK);

    // --- Instance methods ---

    public boolean isLiquid() {
        return liquid;
    }

    public boolean isSolid() {
        return solid;
    }

    public boolean blocksMovement() {
        return blocksMovement;
    }

    public boolean isReplaceable() {
        return replaceable;
    }

    public boolean isBurnable() {
        return burnable;
    }

    public MaterialColor getColor() {
        return color;
    }

    public PistonBehavior getPistonBehavior() {
        return pistonBehavior;
    }

    /**
     * Piston behavior enum, originally in its own file but included
     * here for stub self-containedness.
     */
    public enum PistonBehavior {
        NORMAL,
        DESTROY,
        BLOCK,
        IGNORE,
        PUSH_ONLY
    }
}
