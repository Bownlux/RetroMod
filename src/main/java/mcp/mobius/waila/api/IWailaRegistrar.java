/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package mcp.mobius.waila.api;

public interface IWailaRegistrar {
    default void registerHeadProvider(IWailaDataProvider provider, Class<?> clazz) {}
    default void registerBodyProvider(IWailaDataProvider provider, Class<?> clazz) {}
    default void registerTailProvider(IWailaDataProvider provider, Class<?> clazz) {}
    default void registerStackProvider(IWailaDataProvider provider, Class<?> clazz) {}
}
