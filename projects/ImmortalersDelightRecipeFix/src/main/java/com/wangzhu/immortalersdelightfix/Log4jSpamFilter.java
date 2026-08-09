package com.wangzhu.immortalersdelightfix;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Installs a Log4j-core Filter through reflection.
 *
 * <p>No Log4j implementation classes are linked at compile time, keeping the
 * patch small and compatible with Forge's bundled Log4j version.</p>
 */
final class Log4jSpamFilter {
    private static final String FILTER_CLASS = "org.apache.logging.log4j.core.Filter";
    private static final String RESULT_CLASS = "org.apache.logging.log4j.core.Filter$Result";

    private Log4jSpamFilter() {
    }

    static boolean install() {
        try {
            ClassLoader loader = Log4jSpamFilter.class.getClassLoader();
            Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager", false, loader);
            Class<?> filterInterface = Class.forName(FILTER_CLASS, false, loader);
            Class<?> resultClass = Class.forName(RESULT_CLASS, false, loader);

            Object deny = enumValue(resultClass, "DENY");
            Object neutral = enumValue(resultClass, "NEUTRAL");
            Object filter = Proxy.newProxyInstance(
                    loader,
                    new Class<?>[]{filterInterface},
                    new FilterInvocationHandler(deny, neutral)
            );

            Object context = logManagerClass.getMethod("getContext", boolean.class).invoke(null, false);
            Object configuration = context.getClass().getMethod("getConfiguration").invoke(context);

            Set<Object> loggerConfigs = Collections.newSetFromMap(new IdentityHashMap<>());
            Object rootLogger = invokeNoArgs(configuration, "getRootLogger");
            if (rootLogger != null) {
                loggerConfigs.add(rootLogger);
            }

            Object loggers = invokeNoArgs(configuration, "getLoggers");
            if (loggers instanceof Map<?, ?> map) {
                loggerConfigs.addAll(map.values());
            }

            int installed = 0;
            for (Object loggerConfig : loggerConfigs) {
                if (loggerConfig != null && addFilter(loggerConfig, filter, filterInterface)) {
                    installed++;
                }
            }

            if (installed == 0) {
                return false;
            }

            Method updateLoggers = findNoArgMethod(context.getClass(), "updateLoggers");
            if (updateLoggers != null) {
                updateLoggers.invoke(context);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean addFilter(Object target, Object filter, Class<?> filterInterface) {
        try {
            Method method = findMethod(target.getClass(), "addFilter", filterInterface);
            if (method == null) {
                return false;
            }
            method.invoke(target, filter);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?> parameterType) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(parameterType)) {
                return method;
            }
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                return method;
            }
        }
        return null;
    }

    private static Object invokeNoArgs(Object target, String name) throws Exception {
        Method method = findNoArgMethod(target.getClass(), name);
        return method == null ? null : method.invoke(target);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> enumClass, String value) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), value);
    }

    private static final class FilterInvocationHandler implements InvocationHandler {
        private final Object deny;
        private final Object neutral;

        private FilterInvocationHandler(Object deny, Object neutral) {
            this.deny = deny;
            this.neutral = neutral;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            if (name.equals("filter")) {
                String message = extractMessage(args);
                return SpamMessages.shouldSuppress(message) ? deny : neutral;
            }

            if (name.equals("getOnMatch")) {
                return deny;
            }
            if (name.equals("getOnMismatch")) {
                return neutral;
            }
            if (name.equals("isStarted")) {
                return true;
            }
            if (name.equals("isStopped")) {
                return false;
            }
            if (name.equals("getState") && method.getReturnType().isEnum()) {
                return enumConstant(method.getReturnType(), "STARTED");
            }
            if (name.equals("stop") && method.getReturnType() == boolean.class) {
                return true;
            }
            if (name.equals("toString")) {
                return "ImmortalersDelightRecipeSpamFilter";
            }
            if (name.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (name.equals("equals")) {
                return args != null && args.length == 1 && proxy == args[0];
            }

            Class<?> returnType = method.getReturnType();
            if (returnType.getName().equals(RESULT_CLASS)) {
                return neutral;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class || returnType == short.class
                    || returnType == int.class || returnType == long.class) {
                return 0;
            }
            if (returnType == float.class) {
                return 0.0F;
            }
            if (returnType == double.class) {
                return 0.0D;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }

        private String extractMessage(Object[] args) {
            if (args == null) {
                return null;
            }

            // LogEvent and Message overloads.
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                String className = arg.getClass().getName();
                if (className.equals("org.apache.logging.log4j.core.impl.Log4jLogEvent")
                        || className.endsWith("LogEvent")) {
                    String value = messageFromLogEvent(arg);
                    if (value != null) {
                        return value;
                    }
                }
                if (className.startsWith("org.apache.logging.log4j.message.")) {
                    String value = formattedMessage(arg);
                    if (value != null) {
                        return value;
                    }
                }
            }

            // Logger filter overloads place the message at argument index 3.
            if (args.length > 3 && args[3] != null) {
                Object message = args[3];
                String formatted = formattedMessage(message);
                return formatted != null ? formatted : String.valueOf(message);
            }

            for (Object arg : args) {
                if (arg instanceof String value) {
                    return value;
                }
            }
            return null;
        }

        private String messageFromLogEvent(Object event) {
            try {
                Object message = event.getClass().getMethod("getMessage").invoke(event);
                return formattedMessage(message);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private String formattedMessage(Object message) {
            if (message == null) {
                return null;
            }
            try {
                Class<?> messageInterface = Class.forName(
                        "org.apache.logging.log4j.message.Message",
                        false,
                        Log4jSpamFilter.class.getClassLoader()
                );
                if (messageInterface.isInstance(message)) {
                    Method method = messageInterface.getMethod("getFormattedMessage");
                    Object value = method.invoke(message);
                    return value == null ? null : String.valueOf(value);
                }
            } catch (Throwable ignored) {
                // Continue with the generic reflective fallback below.
            }
            try {
                Method method = message.getClass().getMethod("getFormattedMessage");
                if (!method.canAccess(message)) {
                    method.trySetAccessible();
                }
                Object value = method.invoke(message);
                return value == null ? null : String.valueOf(value);
            } catch (Throwable ignored) {
                return message instanceof CharSequence ? message.toString() : null;
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Object enumConstant(Class<?> type, String name) {
            try {
                return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
            } catch (Throwable ignored) {
                Object[] constants = type.getEnumConstants();
                return constants == null || constants.length == 0 ? null : constants[0];
            }
        }
    }
}
