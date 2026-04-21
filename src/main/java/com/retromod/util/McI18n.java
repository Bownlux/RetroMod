/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Thin wrapper around Minecraft's {@code Text.translatable() / Component.translatable()}
 * that we can invoke reflectively, since RetroMod has no compile-time MC classpath.
 *
 * <p>When the user has a localized version of Minecraft (picked up from their
 * in-game Language setting), MC loads lang files from every mod's
 * {@code assets/<modid>/lang/<locale>.json} automatically. A Text built from
 * {@link #translatable(String)} resolves to the right locale at render time.
 *
 * <p>If the translatable API isn't available (weirdly old MC versions, or the
 * reflection call fails), we fall back to {@code Text.literal(key)} so nothing
 * crashes — users just see the raw translation key on screen, which is the
 * standard MC behavior for missing translations anyway.
 */
public final class McI18n {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod");

    private static volatile Class<?> textClass;
    private static volatile Method translatableMethod;
    private static volatile Method literalMethod;
    private static volatile boolean resolved;

    private McI18n() {}

    /**
     * Build a Text/Component that will be resolved against the current MC
     * language at render time. If the translatable API isn't reachable, the
     * key itself is returned wrapped in a literal Text.
     *
     * @param translationKey the key, e.g. "retromod.settings.title"
     * @return the Text/Component object, or null if both translatable and
     *         literal could not be reached (unlikely)
     */
    public static Object translatable(String translationKey) {
        resolve();
        try {
            if (translatableMethod != null) {
                return translatableMethod.invoke(null, translationKey);
            }
            if (literalMethod != null) {
                return literalMethod.invoke(null, translationKey);
            }
        } catch (Exception e) {
            LOGGER.debug("McI18n.translatable({}) failed: {}", translationKey, e.getMessage());
        }
        return null;
    }

    /**
     * Build a literal Text/Component containing {@code text} verbatim.
     * Use this for things that should never be translated (proper nouns,
     * user-supplied strings, build IDs, etc.).
     */
    public static Object literal(String text) {
        resolve();
        try {
            if (literalMethod != null) {
                return literalMethod.invoke(null, text);
            }
        } catch (Exception e) {
            LOGGER.debug("McI18n.literal({}) failed: {}", text, e.getMessage());
        }
        return null;
    }

    // ──────────────────────────────────────────────────────────────────────
    // INTERNAL
    // ──────────────────────────────────────────────────────────────────────

    private static void resolve() {
        if (resolved) return;
        synchronized (McI18n.class) {
            if (resolved) return;
            textClass = McReflect.findClass(
                "net.minecraft.text.Text",
                "net.minecraft.network.chat.Component"
            );
            if (textClass != null) {
                translatableMethod = McReflect.findMethod(textClass,
                        new Class[]{String.class}, "translatable");
                literalMethod = McReflect.findMethod(textClass,
                        new Class[]{String.class}, "literal");
                // Older MC: no Text.literal, try Text.of
                if (literalMethod == null) {
                    literalMethod = McReflect.findMethod(textClass,
                            new Class[]{String.class}, "of");
                }
            }
            resolved = true;
        }
    }
}
