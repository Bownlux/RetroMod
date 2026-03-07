/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 * 
 * YACL (Yet Another Config Lib) API Compatibility Shim
 */
package com.retromod.shim.api.common;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * YACL API compatibility shim.
 * 
 * YACL is a modern config library that's becoming increasingly popular
 * as an alternative to Cloth Config.
 * Works on both Fabric and Forge/NeoForge.
 * 
 * API changes:
 * - v2.x -> v3.x: Major API restructure, builder pattern changes
 * - Controller types renamed and reorganized
 */
public class YaclApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "YACL API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "2.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "3.4.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "common";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // PACKAGE CHANGES (v2 -> v3)
        // ============================================================
        
        // Old: dev.isxander.yacl.api -> dev.isxander.yacl3.api
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/YetAnotherConfigLib",
            "dev/isxander/yacl3/api/YetAnotherConfigLib"
        );
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/ConfigCategory",
            "dev/isxander/yacl3/api/ConfigCategory"
        );
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/Option",
            "dev/isxander/yacl3/api/Option"
        );
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/OptionGroup",
            "dev/isxander/yacl3/api/OptionGroup"
        );
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/ButtonOption",
            "dev/isxander/yacl3/api/ButtonOption"
        );
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/ListOption",
            "dev/isxander/yacl3/api/ListOption"
        );
        
        // ============================================================
        // CONTROLLER CHANGES
        // ============================================================
        
        // Controllers package moved
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/controller/BooleanController",
            "dev/isxander/yacl3/api/controller/BooleanControllerBuilder"
        );
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/controller/StringController",
            "dev/isxander/yacl3/api/controller/StringControllerBuilder"
        );
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/controller/IntegerSliderController",
            "dev/isxander/yacl3/api/controller/IntegerSliderControllerBuilder"
        );
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/controller/DoubleSliderController",
            "dev/isxander/yacl3/api/controller/DoubleSliderControllerBuilder"
        );
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/controller/FloatSliderController",
            "dev/isxander/yacl3/api/controller/FloatSliderControllerBuilder"
        );
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/controller/EnumController",
            "dev/isxander/yacl3/api/controller/EnumControllerBuilder"
        );
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/controller/ColorController",
            "dev/isxander/yacl3/api/controller/ColorControllerBuilder"
        );
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/controller/TickBoxController",
            "dev/isxander/yacl3/api/controller/TickBoxControllerBuilder"
        );
        
        // ============================================================
        // BUILDER PATTERN CHANGES
        // ============================================================
        
        // YetAnotherConfigLib.createBuilder()
        transformer.registerMethodRedirect(
            "dev/isxander/yacl/api/YetAnotherConfigLib",
            "createBuilder",
            "()Ldev/isxander/yacl/api/YetAnotherConfigLib$Builder;",
            "dev/isxander/yacl3/api/YetAnotherConfigLib",
            "createBuilder",
            "()Ldev/isxander/yacl3/api/YetAnotherConfigLib$Builder;"
        );
        
        // ConfigCategory.createBuilder()
        transformer.registerMethodRedirect(
            "dev/isxander/yacl/api/ConfigCategory",
            "createBuilder",
            "()Ldev/isxander/yacl/api/ConfigCategory$Builder;",
            "dev/isxander/yacl3/api/ConfigCategory",
            "createBuilder",
            "()Ldev/isxander/yacl3/api/ConfigCategory$Builder;"
        );
        
        // Option.createBuilder()
        transformer.registerMethodRedirect(
            "dev/isxander/yacl/api/Option",
            "createBuilder",
            "()Ldev/isxander/yacl/api/Option$Builder;",
            "dev/isxander/yacl3/api/Option",
            "createBuilder",
            "()Ldev/isxander/yacl3/api/Option$Builder;"
        );
        
        // ============================================================
        // BINDING CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/Binding",
            "dev/isxander/yacl3/api/Binding"
        );
        
        transformer.registerMethodRedirect(
            "dev/isxander/yacl/api/Binding",
            "generic",
            "(Ljava/lang/Object;Ljava/util/function/Supplier;Ljava/util/function/Consumer;)Ldev/isxander/yacl/api/Binding;",
            "dev/isxander/yacl3/api/Binding",
            "generic",
            "(Ljava/lang/Object;Ljava/util/function/Supplier;Ljava/util/function/Consumer;)Ldev/isxander/yacl3/api/Binding;"
        );
        
        // ============================================================
        // DESCRIPTION/TOOLTIP CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/api/OptionDescription",
            "dev/isxander/yacl3/api/OptionDescription"
        );
        
        transformer.registerMethodRedirect(
            "dev/isxander/yacl/api/OptionDescription",
            "of",
            "(Lnet/minecraft/network/chat/Component;)Ldev/isxander/yacl/api/OptionDescription;",
            "dev/isxander/yacl3/api/OptionDescription",
            "of",
            "(Lnet/minecraft/network/chat/Component;)Ldev/isxander/yacl3/api/OptionDescription;"
        );
        
        // ============================================================
        // GUI CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "dev/isxander/yacl/gui/YACLScreen",
            "dev/isxander/yacl3/gui/YACLScreen"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.common.embedded.YaclShim"
        };
    }
}
