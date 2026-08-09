package com.wangzhu.immortalersdelightfix;

/** Narrow matching rules for the two debug messages emitted by the recipe. */
final class SpamMessages {
    private SpamMessages() {
    }

    static boolean shouldSuppress(String message) {
        if (message == null) {
            return false;
        }

        String value = message.strip();

        // Raw message passed to System.out.println in affected builds.
        if ("容器不对".equals(value)) {
            return true;
        }

        if (value.startsWith("输入物品数量：") || value.startsWith("输入物品数量:")) {
            return true;
        }

        // Some logging layouts or wrappers may include caller information in
        // the formatted message before the original Chinese debug text.
        if (value.contains("EnchantalCoolerRecipe") && value.contains(":matches")) {
            return value.endsWith("容器不对")
                    || value.contains("输入物品数量：")
                    || value.contains("输入物品数量:");
        }

        return false;
    }
}
