/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.minecraftforge.fml.common;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SidedProxy {
    String clientSide() default "";
    String serverSide() default "";
    String modId() default "";
}
