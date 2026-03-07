package com.retromod.shim.api.forge.embedded;
public class ForgeCapabilitiesShim {
    public static Object getItemHandler() {
        try { return Class.forName("net.neoforged.neoforge.capabilities.Capabilities$ItemHandler").getField("BLOCK").get(null);
        } catch (Exception e) {
            try { return Class.forName("net.minecraftforge.common.capabilities.ForgeCapabilities").getField("ITEM_HANDLER").get(null);
            } catch (Exception e2) { return null; }
        }
    }
    public static Object getFluidHandler() {
        try { return Class.forName("net.neoforged.neoforge.capabilities.Capabilities$FluidHandler").getField("BLOCK").get(null);
        } catch (Exception e) {
            try { return Class.forName("net.minecraftforge.common.capabilities.ForgeCapabilities").getField("FLUID_HANDLER").get(null);
            } catch (Exception e2) { return null; }
        }
    }
    public static Object getEnergy() {
        try { return Class.forName("net.neoforged.neoforge.capabilities.Capabilities$EnergyStorage").getField("BLOCK").get(null);
        } catch (Exception e) {
            try { return Class.forName("net.minecraftforge.common.capabilities.ForgeCapabilities").getField("ENERGY").get(null);
            } catch (Exception e2) { return null; }
        }
    }
}
