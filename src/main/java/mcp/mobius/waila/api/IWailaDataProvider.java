/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package mcp.mobius.waila.api;

import java.util.List;

public interface IWailaDataProvider {
    default Object getWailaStack(IWailaDataAccessor accessor, Object config) { return null; }
    default List<String> getWailaHead(Object itemStack, List<String> tooltip, IWailaDataAccessor accessor, Object config) { return tooltip; }
    default List<String> getWailaBody(Object itemStack, List<String> tooltip, IWailaDataAccessor accessor, Object config) { return tooltip; }
    default List<String> getWailaTail(Object itemStack, List<String> tooltip, IWailaDataAccessor accessor, Object config) { return tooltip; }
}
