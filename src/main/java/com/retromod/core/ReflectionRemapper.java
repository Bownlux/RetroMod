/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime reflection remapper for RetroMod.
 * 
 * When mods use reflection to access Minecraft classes/methods,
 * they might use old names that no longer exist. This class provides
 * utilities to:
 * 
 * 1. Intercept Class.getMethod/getDeclaredMethod calls
 * 2. Remap old method names to new ones
 * 3. Handle both direct and obfuscated names
 * 
 * Usage in transformed code:
 * Original: clazz.getMethod("getWorld")
 * Transformed: ReflectionRemapper.getMethod(clazz, "getWorld")
 */
public final class ReflectionRemapper {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-reflection");
    
    // Cache of remapped methods
    private static final Map<MethodKey, Method> methodCache = new ConcurrentHashMap<>();
    
    // Cache of remapped fields
    private static final Map<FieldKey, Field> fieldCache = new ConcurrentHashMap<>();
    
    // Method name remappings: oldName -> newName
    private static final Map<String, Map<String, String>> methodRemaps = new ConcurrentHashMap<>();
    
    // Field name remappings: oldName -> newName
    private static final Map<String, Map<String, String>> fieldRemaps = new ConcurrentHashMap<>();
    
    // Class remappings: oldClassName -> newClassName
    private static final Map<String, String> classRemaps = new ConcurrentHashMap<>();
    
    static {
        initializeRemappings();
    }
    
    private ReflectionRemapper() {
        // Utility class
    }
    
    /**
     * Initialize remappings from RetroModTransformer.
     */
    private static void initializeRemappings() {
        RetroModTransformer transformer = RetroModTransformer.getInstance();
        
        // Copy class redirects
        classRemaps.putAll(transformer.getClassRedirects());
        
        // Build method remaps by class
        for (var entry : transformer.getMethodRedirects().entrySet()) {
            var key = entry.getKey();
            var target = entry.getValue();
            
            // Convert internal name to class name
            String className = key.owner().replace('/', '.');
            String oldMethod = key.name();
            String newMethod = target.name();
            
            if (!oldMethod.equals(newMethod)) {
                methodRemaps
                    .computeIfAbsent(className, k -> new ConcurrentHashMap<>())
                    .put(oldMethod, newMethod);
            }
        }
        
        LOGGER.info("ReflectionRemapper initialized with {} class remaps, {} method remap groups",
            classRemaps.size(), methodRemaps.size());
    }
    
    /**
     * Add a runtime method remapping.
     */
    public static void addMethodRemap(String className, String oldName, String newName) {
        methodRemaps
            .computeIfAbsent(className, k -> new ConcurrentHashMap<>())
            .put(oldName, newName);
    }
    
    /**
     * Add a runtime field remapping.
     */
    public static void addFieldRemap(String className, String oldName, String newName) {
        fieldRemaps
            .computeIfAbsent(className, k -> new ConcurrentHashMap<>())
            .put(oldName, newName);
    }
    
    // =========================================================
    // METHOD ACCESS
    // =========================================================
    
    /**
     * Get a method, remapping the name if necessary.
     * Replaces: clazz.getMethod(name, paramTypes)
     */
    public static Method getMethod(Class<?> clazz, String name, Class<?>... parameterTypes) 
            throws NoSuchMethodException {
        
        MethodKey key = new MethodKey(clazz, name, parameterTypes);
        
        // Check cache first
        Method cached = methodCache.get(key);
        if (cached != null) return cached;
        
        // Try original name first
        try {
            Method method = clazz.getMethod(name, parameterTypes);
            methodCache.put(key, method);
            return method;
        } catch (NoSuchMethodException e) {
            // Try remapped name
        }
        
        // Look for remapping
        String remappedName = remapMethodName(clazz, name);
        if (remappedName != null && !remappedName.equals(name)) {
            LOGGER.debug("Remapping reflection method: {}.{} -> {}", 
                clazz.getName(), name, remappedName);
            
            Method method = clazz.getMethod(remappedName, parameterTypes);
            methodCache.put(key, method);
            return method;
        }
        
        // Also try in superclasses with remapping
        Method method = findMethodInHierarchy(clazz, name, parameterTypes);
        if (method != null) {
            methodCache.put(key, method);
            return method;
        }
        
        throw new NoSuchMethodException(clazz.getName() + "." + name);
    }
    
    /**
     * Get a declared method, remapping the name if necessary.
     * Replaces: clazz.getDeclaredMethod(name, paramTypes)
     */
    public static Method getDeclaredMethod(Class<?> clazz, String name, Class<?>... parameterTypes) 
            throws NoSuchMethodException {
        
        // Try original name first
        try {
            return clazz.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            // Try remapped name
        }
        
        // Look for remapping
        String remappedName = remapMethodName(clazz, name);
        if (remappedName != null && !remappedName.equals(name)) {
            LOGGER.debug("Remapping reflection declared method: {}.{} -> {}", 
                clazz.getName(), name, remappedName);
            return clazz.getDeclaredMethod(remappedName, parameterTypes);
        }
        
        throw new NoSuchMethodException(clazz.getName() + "." + name);
    }
    
    /**
     * Find a method in the class hierarchy with remapping support.
     */
    private static Method findMethodInHierarchy(Class<?> clazz, String name, Class<?>... parameterTypes) {
        Class<?> current = clazz;
        
        while (current != null && current != Object.class) {
            // Try original name
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {}
            
            // Try remapped name
            String remapped = remapMethodName(current, name);
            if (remapped != null && !remapped.equals(name)) {
                try {
                    return current.getDeclaredMethod(remapped, parameterTypes);
                } catch (NoSuchMethodException ignored) {}
            }
            
            current = current.getSuperclass();
        }
        
        return null;
    }
    
    /**
     * Remap a method name for a class.
     */
    private static String remapMethodName(Class<?> clazz, String name) {
        // Check this class
        Map<String, String> remaps = methodRemaps.get(clazz.getName());
        if (remaps != null && remaps.containsKey(name)) {
            return remaps.get(name);
        }
        
        // Check superclasses
        Class<?> superClass = clazz.getSuperclass();
        while (superClass != null && superClass != Object.class) {
            remaps = methodRemaps.get(superClass.getName());
            if (remaps != null && remaps.containsKey(name)) {
                return remaps.get(name);
            }
            superClass = superClass.getSuperclass();
        }
        
        // Check interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            remaps = methodRemaps.get(iface.getName());
            if (remaps != null && remaps.containsKey(name)) {
                return remaps.get(name);
            }
        }
        
        return null;
    }
    
    // =========================================================
    // FIELD ACCESS
    // =========================================================
    
    /**
     * Get a field, remapping the name if necessary.
     * Replaces: clazz.getField(name)
     */
    public static Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
        FieldKey key = new FieldKey(clazz, name);
        
        Field cached = fieldCache.get(key);
        if (cached != null) return cached;
        
        // Try original name
        try {
            Field field = clazz.getField(name);
            fieldCache.put(key, field);
            return field;
        } catch (NoSuchFieldException e) {
            // Try remapped name
        }
        
        // Look for remapping
        String remappedName = remapFieldName(clazz, name);
        if (remappedName != null && !remappedName.equals(name)) {
            LOGGER.debug("Remapping reflection field: {}.{} -> {}", 
                clazz.getName(), name, remappedName);
            
            Field field = clazz.getField(remappedName);
            fieldCache.put(key, field);
            return field;
        }
        
        throw new NoSuchFieldException(clazz.getName() + "." + name);
    }
    
    /**
     * Get a declared field, remapping the name if necessary.
     */
    public static Field getDeclaredField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            // Try remapped name
        }
        
        String remappedName = remapFieldName(clazz, name);
        if (remappedName != null && !remappedName.equals(name)) {
            LOGGER.debug("Remapping reflection declared field: {}.{} -> {}", 
                clazz.getName(), name, remappedName);
            return clazz.getDeclaredField(remappedName);
        }
        
        throw new NoSuchFieldException(clazz.getName() + "." + name);
    }
    
    private static String remapFieldName(Class<?> clazz, String name) {
        Map<String, String> remaps = fieldRemaps.get(clazz.getName());
        if (remaps != null && remaps.containsKey(name)) {
            return remaps.get(name);
        }
        return null;
    }
    
    // =========================================================
    // CLASS ACCESS
    // =========================================================
    
    /**
     * Load a class, remapping the name if necessary.
     * Replaces: Class.forName(name)
     */
    public static Class<?> forName(String className) throws ClassNotFoundException {
        // Try original name
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            // Try remapped name
        }
        
        // Check for class remapping
        String internalName = className.replace('.', '/');
        String remappedInternal = classRemaps.get(internalName);
        
        if (remappedInternal != null) {
            String remappedName = remappedInternal.replace('/', '.');
            LOGGER.debug("Remapping reflection class: {} -> {}", className, remappedName);
            return Class.forName(remappedName);
        }
        
        throw new ClassNotFoundException(className);
    }
    
    /**
     * Load a class with a specific class loader.
     */
    public static Class<?> forName(String className, boolean initialize, ClassLoader loader) 
            throws ClassNotFoundException {
        
        try {
            return Class.forName(className, initialize, loader);
        } catch (ClassNotFoundException e) {
            // Try remapped name
        }
        
        String internalName = className.replace('.', '/');
        String remappedInternal = classRemaps.get(internalName);
        
        if (remappedInternal != null) {
            String remappedName = remappedInternal.replace('/', '.');
            LOGGER.debug("Remapping reflection class: {} -> {}", className, remappedName);
            return Class.forName(remappedName, initialize, loader);
        }
        
        throw new ClassNotFoundException(className);
    }
    
    // =========================================================
    // UTILITY METHODS
    // =========================================================
    
    /**
     * Invoke a method, handling potential remapping.
     */
    public static Object invoke(Object obj, String methodName, Object... args) 
            throws ReflectiveOperationException {
        
        Class<?> clazz = obj.getClass();
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
        }
        
        Method method = getMethod(clazz, methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(obj, args);
    }
    
    /**
     * Get a field value, handling potential remapping.
     */
    public static Object getFieldValue(Object obj, String fieldName) 
            throws ReflectiveOperationException {
        
        Field field = getField(obj.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }
    
    /**
     * Set a field value, handling potential remapping.
     */
    public static void setFieldValue(Object obj, String fieldName, Object value) 
            throws ReflectiveOperationException {
        
        Field field = getField(obj.getClass(), fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
    
    /**
     * Check if a method exists (original or remapped).
     */
    public static boolean hasMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            getMethod(clazz, name, parameterTypes);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    /**
     * Check if a field exists (original or remapped).
     */
    public static boolean hasField(Class<?> clazz, String name) {
        try {
            getField(clazz, name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
    
    // --- Cache key records ---
    
    private record MethodKey(Class<?> clazz, String name, Class<?>[] parameterTypes) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodKey that)) return false;
            return clazz.equals(that.clazz) && 
                   name.equals(that.name) && 
                   Arrays.equals(parameterTypes, that.parameterTypes);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(clazz, name, Arrays.hashCode(parameterTypes));
        }
    }
    
    private record FieldKey(Class<?> clazz, String name) {}
}
