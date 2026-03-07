/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * EPOCH 2 → 3: Flattening (1.13) to Modern Foundation (1.14-1.16)
 */
package com.retromod.legacy;

public class Epoch2To3Transition extends BaseEpochTransition {
    
    @Override public String name() { return "Flattening 1.13 → Modern 1.14-1.16"; }
    @Override public int sourceEpoch() { return 2; }
    @Override public int targetEpoch() { return 3; }
    
    public Epoch2To3Transition() {
        // Text components renamed
        addClass("net/minecraft/text/LiteralText", "net/minecraft/text/Text");
        addClass("net/minecraft/text/TranslatableText", "net/minecraft/text/Text");
        
        // Registry system modernized
        addClass("net/minecraft/util/registry/Registry", "net/minecraft/registry/Registries");
        
        // Entity AI system changed
        addClass("net/minecraft/entity/ai/EntityAIBase", "net/minecraft/entity/ai/goal/Goal");
        addClass("net/minecraft/entity/ai/EntityAITasks", "net/minecraft/entity/ai/goal/GoalSelector");
        
        // Villager profession changes
        addClass("net/minecraft/entity/passive/EntityVillager$ITradeList",
                 "net/minecraft/village/TradeOffers$Factory");
        
        addShim("com.retromod.virtual.VillagerTradeShim");
    }
}
