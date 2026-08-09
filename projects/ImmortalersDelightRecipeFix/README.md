# Immortalers Delight Recipe Log Fix 1.1.0

Forge 1.20.1 服务端补丁。

## 修复内容

屏蔽受影响版本的 `EnchantalCoolerRecipe#matches` 遗留调试输出：

- `容器不对`
- `输入物品数量：...`

不会停止正常配方查询，也不会修改配方判定、加工进度、容器消耗或自动化。

## 与 1.0.0 的区别

1.0.0 依赖 Mixin 精确重定向目标模组内部的 `println` 字节码，不同版本可能无法命中，而且旧配置会静默忽略注入失败。

1.1.0 改为：

1. 优先安装 Log4j 事件过滤器；
2. 日志框架被整合包改动时，自动启用 stdout 备用过滤器；
3. 不再依赖目标方法签名和具体字节码布局。

## 安装

1. 关闭服务器；
2. 删除旧版 `immortalers_delight_recipe_fix-1.20.1-forge-1.0.0.jar`；
3. 将新版 JAR 放入服务端 `mods`；
4. 完整重启服务器，不要热重载。

启动时应出现一次：

- `installed Log4j recipe-spam filter`，或
- `installed stdout fallback filter`。
