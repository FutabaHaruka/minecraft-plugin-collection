package com.aystudio.core.bukkit.util.common;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ReflectionUtil {
    private static final Class<?>[] EMPTY_TYPES = new Class<?>[0];
    private static final Object[] EMPTY_ARGS = new Object[0];

    private static final ClassValue<MethodCache> METHOD_CACHE = new ClassValue<MethodCache>() {
        @Override
        protected MethodCache computeValue(Class<?> type) {
            return new MethodCache();
        }
    };

    // Only successful lookups are cached. A missing class may become available later
    // through AyCore's runtime library loader, so negative results must not be sticky.
    private static final ConcurrentMap<String, Boolean> PRESENT_CLASSES = new ConcurrentHashMap<String, Boolean>();

    public ReflectionUtil() {
    }

    public static Object invokeMethod(Object obj, String methodName) {
        return invokeMethodInternal(obj, methodName, EMPTY_TYPES, EMPTY_ARGS);
    }

    public static Object invokeMethod(Object obj, String methodName, Class<?>[] paramsClass, Object... params) {
        Class<?>[] safeTypes = paramsClass == null ? EMPTY_TYPES : paramsClass;
        Object[] safeParams = params == null ? EMPTY_ARGS : params;
        return invokeMethodInternal(obj, methodName, safeTypes, safeParams);
    }

    private static Object invokeMethodInternal(Object obj, String methodName, Class<?>[] paramsClass, Object[] params) {
        try {
            Class<?> owner = obj instanceof Class ? (Class<?>) obj : obj.getClass();
            Method method = METHOD_CACHE.get(owner).getPublic(owner, methodName, paramsClass);
            return method.invoke(obj, params);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Object invokeDeclaredMethod(Object obj, String methodName, Class<?>[] paramsClass, Object... params) {
        Class<?>[] safeTypes = paramsClass == null ? EMPTY_TYPES : paramsClass;
        Object[] safeParams = params == null ? EMPTY_ARGS : params;
        try {
            Class<?> owner = obj instanceof Class ? (Class<?>) obj : obj.getClass();
            Method method = METHOD_CACHE.get(owner).getDeclared(owner, methodName, safeTypes);
            return method.invoke(obj, safeParams);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean hasClass(String className) {
        if (PRESENT_CLASSES.containsKey(className)) {
            return true;
        }
        try {
            Class.forName(className);
            PRESENT_CLASSES.putIfAbsent(className, Boolean.TRUE);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static final class MethodCache {
        private final ConcurrentMap<String, Method> publicNoArg = new ConcurrentHashMap<String, Method>();
        private final ConcurrentMap<MethodKey, Method> publicMethods = new ConcurrentHashMap<MethodKey, Method>();
        private final ConcurrentMap<MethodKey, Method> declaredMethods = new ConcurrentHashMap<MethodKey, Method>();

        private Method getPublic(Class<?> owner, String name, Class<?>[] params) throws NoSuchMethodException {
            if (params.length == 0) {
                Method cached = publicNoArg.get(name);
                if (cached != null) {
                    return cached;
                }
                Method resolved = owner.getMethod(name, EMPTY_TYPES);
                Method previous = publicNoArg.putIfAbsent(name, resolved);
                return previous == null ? resolved : previous;
            }
            MethodKey key = new MethodKey(name, params);
            Method cached = publicMethods.get(key);
            if (cached != null) {
                return cached;
            }
            Method resolved = owner.getMethod(name, params);
            Method previous = publicMethods.putIfAbsent(key, resolved);
            return previous == null ? resolved : previous;
        }

        private Method getDeclared(Class<?> owner, String name, Class<?>[] params) throws NoSuchMethodException {
            MethodKey key = new MethodKey(name, params);
            Method cached = declaredMethods.get(key);
            if (cached != null) {
                return cached;
            }
            Method resolved = owner.getDeclaredMethod(name, params);
            if (!resolved.isAccessible()) {
                resolved.setAccessible(true);
            }
            Method previous = declaredMethods.putIfAbsent(key, resolved);
            return previous == null ? resolved : previous;
        }
    }

    private static final class MethodKey {
        private final String name;
        private final Class<?>[] parameterTypes;
        private final int hash;

        private MethodKey(String name, Class<?>[] parameterTypes) {
            this.name = name;
            this.parameterTypes = parameterTypes.length == 0 ? EMPTY_TYPES : parameterTypes.clone();
            this.hash = 31 * name.hashCode() + Arrays.hashCode(this.parameterTypes);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MethodKey)) {
                return false;
            }
            MethodKey other = (MethodKey) obj;
            return name.equals(other.name) && Arrays.equals(parameterTypes, other.parameterTypes);
        }
    }
}
