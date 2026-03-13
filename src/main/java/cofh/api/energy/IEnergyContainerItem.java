/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package cofh.api.energy;

public interface IEnergyContainerItem {
    default int receiveEnergy(Object container, int maxReceive, boolean simulate) { return 0; }
    default int extractEnergy(Object container, int maxExtract, boolean simulate) { return 0; }
    default int getEnergyStored(Object container) { return 0; }
    default int getMaxEnergyStored(Object container) { return 0; }
}
