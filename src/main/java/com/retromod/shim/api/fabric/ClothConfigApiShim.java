/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * Cloth Config API Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Cloth Config API compatibility shim.
 * 
 * Cloth Config is the most popular configuration library for Fabric mods.
 * Major API changes:
 * - v4.x -> v5.x: Builder API changes
 * - v5.x -> v6.x: Entry builders refactored
 * - v6.x -> v11.x: ConfigBuilder changes
 * - Screen creation changes across versions
 */
public class ClothConfigApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Cloth Config API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "4.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "11.0.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // PACKAGE RELOCATIONS
        // ============================================================

        // Old package: me.shedaniel.clothconfig2
        // Some versions used different packages
        transformer.registerClassRedirect(
            "me/shedaniel/clothconfig/api/ConfigBuilder",
            "me/shedaniel/clothconfig2/api/ConfigBuilder"
        );

        transformer.registerClassRedirect(
            "me/shedaniel/clothconfig/api/ConfigCategory",
            "me/shedaniel/clothconfig2/api/ConfigCategory"
        );

        transformer.registerClassRedirect(
            "me/shedaniel/clothconfig/api/ConfigEntryBuilder",
            "me/shedaniel/clothconfig2/api/ConfigEntryBuilder"
        );

        // ============================================================
        // CLOTH CONFIG v6.x COMPATIBILITY
        // ============================================================
        // v6.x was widely used with MC 1.17–1.19 and is a common
        // dependency. Many mods pin "cloth-config >= 6.0" specifically.
        //
        // Key changes between v6.x and v11.x:
        //   - ConfigScreen.Builder replaced ConfigBuilder in some flows
        //   - getOrCreateCategory signature changed
        //   - Tooltip handling changed (Optional<Text[]> -> varargs)
        //   - SubCategoryBuilder was refactored
        //   - AbstractConfigEntry generics changed

        // v6.x ConfigBuilder.getOrCreateCategory used Text directly
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigBuilder",
            "getOrCreateCategory",
            "(Lnet/minecraft/text/Text;)Lme/shedaniel/clothconfig2/api/ConfigCategory;",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "getOrCreateCategoryCompat",
            "(Ljava/lang/Object;Lnet/minecraft/text/Text;)Ljava/lang/Object;"
        );

        // v6.x SubCategoryBuilder constructor took (Text, List) in some versions
        transformer.registerClassRedirect(
            "me/shedaniel/clothconfig2/gui/entries/SubCategoryListEntry$Builder",
            "me/shedaniel/clothconfig2/impl/builders/SubCategoryBuilder"
        );

        // v6.x AbstractConfigEntry.setTooltipSupplier (removed in later versions)
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/AbstractConfigListEntry",
            "setTooltipSupplier",
            "(Ljava/util/function/Supplier;)V",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "setTooltipSupplierCompat",
            "(Ljava/lang/Object;Ljava/util/function/Supplier;)V"
        );

        // v6.x used setErrorSupplier differently
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/AbstractConfigListEntry",
            "setErrorSupplier",
            "(Ljava/util/function/Supplier;)V",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "setErrorSupplierCompat",
            "(Ljava/lang/Object;Ljava/util/function/Supplier;)V"
        );

        // v6.x ConfigBuilder.setSavingRunnable signature
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigBuilder",
            "setSavingRunnable",
            "(Ljava/lang/Runnable;)V",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "setSavingRunnableCompat",
            "(Ljava/lang/Object;Ljava/lang/Runnable;)V"
        );
        
        // ============================================================
        // CONFIG BUILDER CHANGES
        // ============================================================
        
        // Old: ConfigBuilder.create().setParentScreen(parent)
        // Still works but method signatures changed
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigBuilder",
            "create",
            "()Lme/shedaniel/clothconfig2/api/ConfigBuilder;",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "createBuilder",
            "()Ljava/lang/Object;"
        );
        
        // ============================================================
        // ENTRY BUILDER CHANGES
        // ============================================================
        
        // Old startStrField -> startTextField (renamed in some versions)
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigEntryBuilder",
            "startStrField",
            "(Lnet/minecraft/text/Text;Ljava/lang/String;)Lme/shedaniel/clothconfig2/impl/builders/StringFieldBuilder;",
            "me/shedaniel/clothconfig2/api/ConfigEntryBuilder",
            "startTextField",
            "(Lnet/minecraft/text/Text;Ljava/lang/String;)Lme/shedaniel/clothconfig2/impl/builders/StringFieldBuilder;"
        );
        
        // Old startBooleanToggle signature changes
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigEntryBuilder",
            "startBooleanToggle",
            "(Lnet/minecraft/text/Text;Z)Lme/shedaniel/clothconfig2/impl/builders/BooleanToggleBuilder;",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "startBooleanToggle",
            "(Ljava/lang/Object;Lnet/minecraft/text/Text;Z)Ljava/lang/Object;"
        );
        
        // ============================================================
        // ABSTRACT CONFIG ENTRY CHANGES
        // ============================================================
        
        // setTooltip changes (varargs vs single)
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/AbstractConfigListEntry",
            "setTooltip",
            "(Ljava/util/Optional;)V",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "setTooltipCompat",
            "(Ljava/lang/Object;Ljava/util/Optional;)V"
        );
        
        // ============================================================
        // DROPDOWN BUILDER CHANGES
        // ============================================================
        
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigEntryBuilder",
            "startDropdownMenu",
            "(Lnet/minecraft/text/Text;Ljava/lang/Object;)Lme/shedaniel/clothconfig2/impl/builders/DropdownMenuBuilder;",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "startDropdownMenu",
            "(Ljava/lang/Object;Lnet/minecraft/text/Text;Ljava/lang/Object;)Ljava/lang/Object;"
        );
        
        // ============================================================
        // COLOR ENTRY CHANGES
        // ============================================================
        
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigEntryBuilder",
            "startColorField",
            "(Lnet/minecraft/text/Text;I)Lme/shedaniel/clothconfig2/impl/builders/ColorFieldBuilder;",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "startColorField",
            "(Ljava/lang/Object;Lnet/minecraft/text/Text;I)Ljava/lang/Object;"
        );
        
        // ============================================================
        // TEXT CLASS CHANGES
        // ============================================================
        
        // Old: Text.literal() / Text.translatable()
        // Very old: new LiteralText() / new TranslatableText()
        transformer.registerClassRedirect(
            "net/minecraft/text/LiteralText",
            "com/retromod/shim/api/fabric/embedded/TextShim$LiteralTextShim"
        );
        
        transformer.registerClassRedirect(
            "net/minecraft/text/TranslatableText",
            "com/retromod/shim/api/fabric/embedded/TextShim$TranslatableTextShim"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.ClothConfigShim",
            "com.retromod.shim.api.fabric.embedded.TextShim"
        };
    }
}
