/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package cofh.api.energy;

public interface IEnergyHandler extends IEnergyConnection {
    default int receiveEnergy(Object from, int maxReceive, boolean simulate) { return 0; }
    default int extractEnergy(Object from, int maxExtract, boolean simulate) { return 0; }
    default int getEnergyStored(Object from) { return 0; }
    default int getMaxEnergyStored(Object from) { return 0; }
}
