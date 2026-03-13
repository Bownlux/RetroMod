/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package mcp.mobius.waila.api;

import java.util.Set;
import java.util.HashSet;

public interface IWailaConfigHandler {
    default Set<String> getModuleNames() { return new HashSet<>(); }
    default boolean getConfig(String key) { return false; }
    default boolean getConfig(String key, boolean defaultValue) { return defaultValue; }
}
