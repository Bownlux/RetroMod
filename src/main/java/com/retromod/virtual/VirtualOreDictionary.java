/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.virtual;

import java.util.*;

/**
 * Virtual replacement for OreDictionary.
 * The Ore Dictionary was replaced by tags in 1.13+.
 * This bridges old ore dict names to modern tag system.
 */
public class VirtualOreDictionary {
    
    private static final Map<String, List<Object>> ORE_NAMES = new HashMap<>();
    private static final Map<Object, List<String>> ITEM_NAMES = new HashMap<>();
    private static final Map<String, String> ORE_TO_TAG = new HashMap<>();
    
    public static final int WILDCARD_VALUE = Short.MAX_VALUE;
    
    static {
        // Pre-populate ore name to tag mappings
        ORE_TO_TAG.put("oreIron", "c:ores/iron");
        ORE_TO_TAG.put("oreGold", "c:ores/gold");
        ORE_TO_TAG.put("oreCopper", "c:ores/copper");
        ORE_TO_TAG.put("oreDiamond", "c:ores/diamond");
        ORE_TO_TAG.put("oreCoal", "c:ores/coal");
        ORE_TO_TAG.put("oreLapis", "c:ores/lapis");
        ORE_TO_TAG.put("oreRedstone", "c:ores/redstone");
        ORE_TO_TAG.put("oreEmerald", "c:ores/emerald");
        ORE_TO_TAG.put("oreQuartz", "c:ores/quartz");
        
        ORE_TO_TAG.put("ingotIron", "c:ingots/iron");
        ORE_TO_TAG.put("ingotGold", "c:ingots/gold");
        ORE_TO_TAG.put("ingotCopper", "c:ingots/copper");
        
        ORE_TO_TAG.put("gemDiamond", "c:gems/diamond");
        ORE_TO_TAG.put("gemEmerald", "c:gems/emerald");
        ORE_TO_TAG.put("gemLapis", "c:gems/lapis");
        ORE_TO_TAG.put("gemQuartz", "c:gems/quartz");
        
        ORE_TO_TAG.put("dustRedstone", "c:dusts/redstone");
        ORE_TO_TAG.put("dustGlowstone", "c:dusts/glowstone");
        
        ORE_TO_TAG.put("nuggetGold", "c:nuggets/gold");
        ORE_TO_TAG.put("nuggetIron", "c:nuggets/iron");
        
        ORE_TO_TAG.put("blockIron", "c:storage_blocks/iron");
        ORE_TO_TAG.put("blockGold", "c:storage_blocks/gold");
        ORE_TO_TAG.put("blockDiamond", "c:storage_blocks/diamond");
        ORE_TO_TAG.put("blockCoal", "c:storage_blocks/coal");
        
        ORE_TO_TAG.put("plankWood", "minecraft:planks");
        ORE_TO_TAG.put("logWood", "minecraft:logs");
        ORE_TO_TAG.put("slabWood", "minecraft:wooden_slabs");
        ORE_TO_TAG.put("stairWood", "minecraft:wooden_stairs");
        ORE_TO_TAG.put("stickWood", "c:rods/wooden");
        ORE_TO_TAG.put("treeSapling", "minecraft:saplings");
        ORE_TO_TAG.put("treeLeaves", "minecraft:leaves");
        
        ORE_TO_TAG.put("dyeBlack", "c:dyes/black");
        ORE_TO_TAG.put("dyeRed", "c:dyes/red");
        ORE_TO_TAG.put("dyeGreen", "c:dyes/green");
        ORE_TO_TAG.put("dyeBrown", "c:dyes/brown");
        ORE_TO_TAG.put("dyeBlue", "c:dyes/blue");
        ORE_TO_TAG.put("dyePurple", "c:dyes/purple");
        ORE_TO_TAG.put("dyeCyan", "c:dyes/cyan");
        ORE_TO_TAG.put("dyeLightGray", "c:dyes/light_gray");
        ORE_TO_TAG.put("dyeGray", "c:dyes/gray");
        ORE_TO_TAG.put("dyePink", "c:dyes/pink");
        ORE_TO_TAG.put("dyeLime", "c:dyes/lime");
        ORE_TO_TAG.put("dyeYellow", "c:dyes/yellow");
        ORE_TO_TAG.put("dyeLightBlue", "c:dyes/light_blue");
        ORE_TO_TAG.put("dyeMagenta", "c:dyes/magenta");
        ORE_TO_TAG.put("dyeOrange", "c:dyes/orange");
        ORE_TO_TAG.put("dyeWhite", "c:dyes/white");
        
        ORE_TO_TAG.put("cobblestone", "c:cobblestones");
        ORE_TO_TAG.put("stone", "c:stones");
        ORE_TO_TAG.put("sand", "c:sands");
        ORE_TO_TAG.put("sandstone", "c:sandstones");
        ORE_TO_TAG.put("gravel", "c:gravels");
        ORE_TO_TAG.put("netherrack", "c:netherracks");
        ORE_TO_TAG.put("obsidian", "c:obsidians");
        ORE_TO_TAG.put("endstone", "c:end_stones");
        
        ORE_TO_TAG.put("cropWheat", "c:crops/wheat");
        ORE_TO_TAG.put("cropPotato", "c:crops/potato");
        ORE_TO_TAG.put("cropCarrot", "c:crops/carrot");
        ORE_TO_TAG.put("cropNetherWart", "c:crops/nether_wart");
        
        ORE_TO_TAG.put("leather", "c:leathers");
        ORE_TO_TAG.put("feather", "c:feathers");
        ORE_TO_TAG.put("string", "c:strings");
        ORE_TO_TAG.put("slimeball", "c:slimeballs");
        ORE_TO_TAG.put("egg", "c:eggs");
        ORE_TO_TAG.put("paper", "c:papers");
        ORE_TO_TAG.put("bone", "c:bones");
        ORE_TO_TAG.put("gunpowder", "c:gunpowders");
        ORE_TO_TAG.put("enderPearl", "c:ender_pearls");
        ORE_TO_TAG.put("blazeRod", "c:blaze_rods");
        ORE_TO_TAG.put("blazePowder", "c:blaze_powders");
        
        ORE_TO_TAG.put("glass", "c:glass_blocks");
        ORE_TO_TAG.put("paneGlass", "c:glass_panes");
        ORE_TO_TAG.put("glassColorless", "c:glass_blocks/colorless");
        ORE_TO_TAG.put("paneGlassColorless", "c:glass_panes/colorless");
        
        ORE_TO_TAG.put("chest", "c:chests");
        ORE_TO_TAG.put("chestWood", "c:chests/wooden");
        ORE_TO_TAG.put("chestEnder", "c:chests/ender");
        ORE_TO_TAG.put("chestTrapped", "c:chests/trapped");
        
        ORE_TO_TAG.put("workbench", "c:workbenches");
    }
    
    public static void registerOre(String name, Object ore) {
        ORE_NAMES.computeIfAbsent(name, k -> new ArrayList<>()).add(ore);
        ITEM_NAMES.computeIfAbsent(ore, k -> new ArrayList<>()).add(name);
        
        String tagName = convertToTag(name);
        System.out.println("RetroMod: Bridging ore dict '" + name + "' to tag '" + tagName + "'");
    }
    
    public static List<Object> getOres(String name) {
        return ORE_NAMES.getOrDefault(name, Collections.emptyList());
    }
    
    public static List<Object> getOres(String name, boolean alwaysCreateEntry) {
        if (alwaysCreateEntry) {
            return ORE_NAMES.computeIfAbsent(name, k -> new ArrayList<>());
        }
        return getOres(name);
    }
    
    public static List<String> getOreNames(Object item) {
        return ITEM_NAMES.getOrDefault(item, Collections.emptyList());
    }
    
    public static String[] getOreNames() {
        return ORE_NAMES.keySet().toArray(new String[0]);
    }
    
    public static boolean containsMatch(boolean strict, List<Object> targets, Object input) {
        for (Object target : targets) {
            if (itemMatches(target, input, strict)) {
                return true;
            }
        }
        return false;
    }
    
    public static boolean itemMatches(Object target, Object input, boolean strict) {
        return target.equals(input);
    }
    
    public static int getOreID(String name) {
        String[] names = getOreNames();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) return i;
        }
        return -1;
    }
    
    public static String getOreName(int id) {
        String[] names = getOreNames();
        if (id >= 0 && id < names.length) {
            return names[id];
        }
        return null;
    }
    
    public static String convertToTag(String oreName) {
        // Check predefined mappings first
        String predefined = ORE_TO_TAG.get(oreName);
        if (predefined != null) return predefined;
        
        // Auto-convert based on naming patterns
        if (oreName.startsWith("ore")) {
            return "c:ores/" + camelToSnake(oreName.substring(3));
        } else if (oreName.startsWith("ingot")) {
            return "c:ingots/" + camelToSnake(oreName.substring(5));
        } else if (oreName.startsWith("gem")) {
            return "c:gems/" + camelToSnake(oreName.substring(3));
        } else if (oreName.startsWith("dust")) {
            return "c:dusts/" + camelToSnake(oreName.substring(4));
        } else if (oreName.startsWith("nugget")) {
            return "c:nuggets/" + camelToSnake(oreName.substring(6));
        } else if (oreName.startsWith("block")) {
            return "c:storage_blocks/" + camelToSnake(oreName.substring(5));
        } else if (oreName.startsWith("dye")) {
            return "c:dyes/" + camelToSnake(oreName.substring(3));
        } else if (oreName.startsWith("plank")) {
            return "minecraft:planks";
        } else if (oreName.startsWith("log")) {
            return "minecraft:logs";
        } else if (oreName.startsWith("stick")) {
            return "c:rods/wooden";
        }
        
        return "c:" + camelToSnake(oreName);
    }
    
    private static String camelToSnake(String camel) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) result.append('_');
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
