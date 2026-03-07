package com.retromod.shim.api.fabric.embedded;
public class PlayerEventsShim {
    public static Object getUseBlockEvent() {
        try {
            Class<?> callback = Class.forName("net.fabricmc.fabric.api.event.player.UseBlockCallback");
            return callback.getField("EVENT").get(null);
        } catch (Exception e) { return null; }
    }
}
