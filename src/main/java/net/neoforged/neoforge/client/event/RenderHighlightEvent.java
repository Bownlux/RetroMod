/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.neoforged.neoforge.client.event;

public class RenderHighlightEvent {
    public boolean isCanceled() { return false; }

    public void setCanceled(boolean canceled) {}

    public static class Block extends RenderHighlightEvent {
        public Object getTarget() { return null; }

        public Object getPoseStack() { return null; }

        public Object getMultiBufferSource() { return null; }

        public Object getCamera() { return null; }
    }
}
