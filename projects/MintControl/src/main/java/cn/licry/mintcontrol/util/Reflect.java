package cn.licry.mintcontrol.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class Reflect {
    private Reflect() {}

    public static Method findMethod(Class<?> type, String name, int parameterCount) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static Method findAnyMethod(Class<?> type, int parameterCount, String... names) {
        for (String name : names) {
            Method method = findMethod(type, name, parameterCount);
            if (method != null) return method;
        }
        return null;
    }

    public static Method findCompatibleMethod(Class<?> type, String name, Object... args) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterTypes().length != args.length) continue;
                Class<?>[] params = method.getParameterTypes();
                boolean compatible = true;
                for (int i = 0; i < params.length; i++) {
                    if (args[i] == null) continue;
                    Class<?> expected = wrap(params[i]);
                    if (!expected.isAssignableFrom(args[i].getClass())) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static Object invoke(Object target, String name, Object... args) throws Exception {
        Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass();
        Method method = findCompatibleMethod(type, name, args);
        if (method == null) throw new NoSuchMethodException(type.getName() + "#" + name);
        return method.invoke(target instanceof Class ? null : target, args);
    }

    public static Object getStaticField(Class<?> type, String... names) throws Exception {
        for (String name : names) {
            Class<?> current = type;
            while (current != null) {
                try {
                    Field field = current.getDeclaredField(name);
                    if (!Modifier.isStatic(field.getModifiers())) break;
                    field.setAccessible(true);
                    return field.get(null);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
        }
        throw new NoSuchFieldException(type.getName());
    }

    public static Object enumConstant(Class<?> enumClass, String name) {
        if (enumClass == null || !enumClass.isEnum() || name == null) return null;
        Object[] constants = enumClass.getEnumConstants();
        for (Object constant : constants) {
            Enum<?> value = (Enum<?>) constant;
            if (value.name().equalsIgnoreCase(name) || value.toString().equalsIgnoreCase(name)) return constant;
        }
        return null;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
