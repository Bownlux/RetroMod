/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package baubles.api.render;

public interface IRenderBauble {
    default void onPlayerBaubleRender(Object itemStack, Object player, Object renderType, float partialTick) {}

    enum RenderType { BODY }
}
