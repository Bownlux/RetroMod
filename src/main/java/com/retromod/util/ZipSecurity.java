/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Security utilities for ZIP/JAR extraction.
 *
 * <p><b>Zip Slip</b> is a path traversal vulnerability (CVE-2018-1263) where a
 * crafted ZIP/JAR archive contains entries with paths like {@code "../../etc/malicious.class"}.
 * If extracted naively, these entries escape the intended output directory and can
 * overwrite arbitrary files on the filesystem. Since RetroMod extracts and re-packages
 * mod JARs, we must validate every entry path before writing.</p>
 *
 * <p><b>Symlink attacks:</b> An attacker could replace the retromod-input/ directory
 * with a symlink pointing to a sensitive location (e.g., {@code ~/.ssh/}). When
 * RetroMod writes transformed mods to that directory, it would actually be writing
 * to the symlinked target. We check for symlinks before operating on directories.</p>
 *
 * @see <a href="https://security.snyk.io/research/zip-slip-vulnerability">Snyk Zip Slip Research</a>
 */
public final class ZipSecurity {

    /** Default per-entry size cap (50 MB) — matches Fabric/Forge extractor defaults. */
    public static final long DEFAULT_MAX_ENTRY_SIZE = 50L * 1024 * 1024;
    /** Default total extraction cap (500 MB). */
    public static final long DEFAULT_MAX_TOTAL_SIZE = 500L * 1024 * 1024;

    private ZipSecurity() {}

    /**
     * Validate that a ZIP entry name is safe — no path traversal components,
     * no absolute paths, no empty/null. Use this whenever copying an entry
     * name to disk OR to another archive (since downstream tools may extract
     * the output archive and be vulnerable to zip-slip themselves).
     *
     * @param entryName the entry name to validate
     * @return the (unchanged) entry name if safe
     * @throws IOException if the entry name contains unsafe components
     */
    public static String safeEntryName(String entryName) throws IOException {
        if (entryName == null || entryName.isEmpty()) {
            throw new IOException("ZIP entry has null/empty name");
        }
        // Normalize separators and check for traversal
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("\\")) {
            throw new IOException("ZIP entry name is absolute: " + entryName);
        }
        // Windows drive-letter absolute paths
        if (normalized.length() >= 3 && normalized.charAt(1) == ':' &&
                (normalized.charAt(2) == '/' || normalized.charAt(2) == '\\')) {
            throw new IOException("ZIP entry name has drive letter: " + entryName);
        }
        for (String part : normalized.split("/")) {
            if ("..".equals(part)) {
                throw new IOException("ZIP entry name contains path traversal: " + entryName);
            }
        }
        return entryName;
    }

    /**
     * Read an InputStream into a byte array, capping total bytes read. Unlike
     * InputStream.readAllBytes() this does NOT trust the header-declared size —
     * it counts bytes actually read, so a malicious archive that reports size=0
     * but streams gigabytes will be caught.
     *
     * @param is       the stream to read (not closed by this method)
     * @param maxBytes the maximum bytes to read before throwing
     * @return the bytes read
     * @throws IOException if the stream exceeds maxBytes, or any I/O error
     */
    public static byte[] safeReadAllBytes(InputStream is, long maxBytes) throws IOException {
        if (maxBytes <= 0) maxBytes = DEFAULT_MAX_ENTRY_SIZE;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = is.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new IOException("ZIP entry exceeds " + maxBytes + " bytes (possible zip bomb)");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** Convenience overload that uses the default per-entry cap. */
    public static byte[] safeReadAllBytes(InputStream is) throws IOException {
        return safeReadAllBytes(is, DEFAULT_MAX_ENTRY_SIZE);
    }

    /**
     * Safely resolve a ZIP entry name against an output directory.
     * Normalizes the path and verifies it stays within the target directory.
     *
     * @param outputDir the target extraction directory
     * @param entryName the ZIP entry name to resolve
     * @return the validated output path
     * @throws IOException if the entry name would escape the output directory
     */
    public static Path safeResolve(Path outputDir, String entryName) throws IOException {
        Path resolved = outputDir.resolve(entryName).normalize();
        if (!resolved.startsWith(outputDir.normalize())) {
            throw new IOException("Zip Slip: entry '" + entryName
                + "' resolves outside target directory");
        }
        return resolved;
    }

    /**
     * Validate that a path is not a symbolic link.
     * Prevents symlink attacks where an attacker replaces a directory with a symlink
     * to trick RetroMod into reading/writing files outside the game directory.
     *
     * @param path the path to check
     * @throws IOException if the path is a symbolic link
     */
    public static void validateNotSymlink(Path path) throws IOException {
        if (Files.exists(path) && Files.isSymbolicLink(path)) {
            throw new IOException("Security: symlink detected at " + path
                + " — refusing to operate on symlinked directories");
        }
    }
}
