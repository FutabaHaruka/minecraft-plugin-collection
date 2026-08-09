# MintControl rc17 热加载资源封闭审计

## rc16 仍存在的风险

rc16 已经停止把完整插件 JAR 注入 Forge 全局 `LaunchClassLoader`，但事件桥仍创建了一个以完整插件 JAR 为 URL 的私有 `URLClassLoader`。正常的父子加载关系下，父加载器不会反向读取该子加载器资源；然而部分 CatServer 热加载器、诊断插件或错误的类加载器枚举逻辑会持有并主动查询其他加载器，因此这个加载器仍然能够返回 MintControl 的 `plugin.yml` 和内置默认配置。

rc16 的事件监听器在卸载时还存在第二个问题：如果第三方热加载器导致 Forge `EventBus.unregister` 失败，旧监听器仍会持有 Bukkit 插件实例、配置服务、回调 Method 和 Logger，并可能继续执行旧配置。

## rc17 修复

1. 删除 `URLClassLoader`。
2. 从主插件 ProtectionDomain 指向的自身 JAR/类目录中，只读取 `cn.licry.mintcontrol.runtime.*` 的 class 字节。
3. 使用无 URL、无自有资源查找能力的 `ResourceFreeRuntimeClassLoader` 定义这些 class。
4. 运行桥的父加载器仍是 Forge LaunchClassLoader，仅用于取得 Forge、Pixelmon 和 Minecraft 类。
5. 内置默认配置从 YAML 资源改为 `META-INF/mintcontrol/defaults.rc17`，JAR 内不再存在任何名为 `config.yml` 的资源，也没有除 `plugin.yml` 之外的 YAML 文件。
6. 监听器卸载时无条件清空 plugin、service、Method、Logger 和 EventBus 引用；即便第三方热加载器阻止真正注销，残留监听器也会进入 inert 状态，不再执行旧配置。
7. Bukkit 命令执行器和补全器在 `onDisable` 中解除绑定，并清空完整配置/服务对象图。

## 静态与构建验证

- Java 8 字节码：全部 MintControl class 的 major version 均为 52。
- JAR 完整性：通过 `unzip -t`。
- 重复条目：0。
- 运行桥不继承 `URLClassLoader`。
- JAR/字节码中不存在 `addURL` 或 `addUrl`。
- 插件业务 class 不调用 `getResource`、`getResources`、`saveResource` 或 `saveDefaultConfig`。
- 资源探针验证：运行桥无法取得 `plugin.yml`、`config.yml` 或内置默认配置资源。
- JAR 仅保留 Bukkit 必需的根目录 `plugin.yml`；内置默认配置使用唯一 META-INF 非 YAML 路径。

## 部署要求

旧版本曾经向全局 LaunchClassLoader 注入过完整 JAR。该 URL 在 JVM 运行期间无法可靠移除，因此升级 rc17 时必须完整结束 Java 进程后冷启动。不要使用 `/reload`、PlugMan reload/unload/load 或同类方式替换旧 JAR。

如果冷启动 rc17 后，热加载另一个插件仍把 MintControl 内容写进它自己的配置目录，则写入动作已经不来自 rc17 的资源加载链，需要审计实际使用的热加载器 JAR及目标插件的默认配置读取代码。
