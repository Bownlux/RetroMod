package com.retromod.shim.api.fabric.embedded;
public class TransferApiShim {
    public static Object getItemStorageSided() {
        try {
            Class<?> storage = Class.forName("net.fabricmc.fabric.api.transfer.v1.item.ItemStorage");
            return storage.getField("SIDED").get(null);
        } catch (Exception e) { return null; }
    }
}
