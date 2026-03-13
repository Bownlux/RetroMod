/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.minecraftforge.common.capabilities;

public interface ICapabilityProvider {
    default <T> Object getCapability(Object cap, Object side) { return null; }

    default <T> Object getCapability(Object cap) { return getCapability(cap, null); }
}
