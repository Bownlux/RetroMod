/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package cofh.api.energy;

public interface IEnergyConnection {
    default boolean canConnectEnergy(Object from) { return false; }
}
