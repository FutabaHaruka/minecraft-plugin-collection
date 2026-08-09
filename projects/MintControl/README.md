# MintControl 1.0.0-rc17

适用于 Minecraft 1.12.2 混合端（CatServer 等）与 Pixelmon Reforged 8.x 的 Bukkit 插件。

## rc13 行为

- 只监听 Pixelmon 原生 `ItemInteractionEvent`。
- 只处理 Pixelmon 自己的原生薄荷。
- 不配置、不识别、不支持任何自定义薄荷。
- 不读取物品数字 ID、Data、名称、Lore 或 NBT 作为规则。
- 不消耗、返还或修改任何插件材料；原生薄荷由 Pixelmon 自行消耗。
- 插件只管理精灵黑白名单、精灵类别、权限、金币、PlayerPoints、概率和冷却。

固定判定顺序：

1. 精灵黑名单；
2. 精灵白名单；
3. 精灵类别；
4. 权限、冷却与金币/点券预检；
5. 概率判定；
6. 允许时放行 Pixelmon 原生逻辑，拒绝时取消事件。


## rc17 热加载资源封闭

- Forge 事件桥不再使用任何 `URLClassLoader`，只加载运行桥 class 字节。
- 运行桥无法读取或暴露 `plugin.yml`、`config.yml` 或内置默认配置。
- 内置默认配置使用 `META-INF/mintcontrol/defaults.rc17` 唯一非 YAML 路径，并从自身 JAR 精确读取。
- 卸载时清空命令、事件、配置与服务引用；第三方热加载器留下的旧监听器也会停止执行。
- 从 rc16 或更早版本升级时必须彻底结束 JVM 后冷启动。

## 配置路径

rc17 不使用 Bukkit 内置配置缓存，强制读写：

```text
plugins/MintControl/config.yml
```

启动日志和 `/mintc status` 会显示实际绝对路径。旧 rc11 配置中的 `mints`、`rules.consume-mint`、`rules.costs.items` 会被删除，并在同目录生成 `config.rc11-before-rc13.yml` 备份。

## 构建

```bash
mvn clean package
```

正式 Maven 构建使用 Spigot API `1.12.2-R0.1-SNAPSHOT`。`src/compileOnly` 和 `src/offlineStubs` 仅用于兼容/离线编译验证，JAR 构建明确排除 Bukkit、Forge、Minecraft 和 Pixelmon 外部类。
## 目录隔离修复

本版本不使用 Bukkit 返回的目录叶名称作为写入目标。即使混合端把本插件的数据目录错误指向其他插件目录，也只会写入 `plugins/MintControl/config.yml`。目录内会创建 `.plugin-owner` 所有权标记；检测到路径越界、目录目标变化或软链接时会停止 I/O。

