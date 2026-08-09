# 1.0.0-rc17-p2

- 修复 CatServer/Forge 1.12.2 事件触发时 `LaunchMintEventHook` 无法由 LaunchClassLoader 解析的问题。
- 不再使用私有子 ClassLoader 注册 Forge 监听器；只将 `cn.licry.mintcontrol.runtime.*` 的 class 字节定义到 LaunchClassLoader。
- 不向 LaunchClassLoader 添加插件 JAR URL，不暴露 `plugin.yml`、默认配置或其他资源。

# Changelog

## 1.0.0-rc17

- 重新审计 rc16 热加载隔离。
- 删除仍可访问完整插件 JAR 资源的私有 `URLClassLoader`。
- 新增无 URL、无自有资源查找能力的字节码专用运行桥。
- 只从自身精确代码源复制 `cn.licry.mintcontrol.runtime.*` class 字节。
- 内置默认配置移动到唯一的 `META-INF/mintcontrol/defaults.rc17` 非 YAML 路径。
- 卸载时解除命令执行器/补全器并清空配置、服务和回调对象图。
- Forge 监听器即使注销失败也会清空回调状态，残留实例不再执行旧配置。
- 修正 `/mintc status` 中写死的旧版本标题。

## 1.0.0-rc16

- 停止把完整插件 JAR 注入 Forge 全局 LaunchClassLoader。
- 默认配置不再使用根目录 `config.yml`，改为插件唯一资源路径并从自身 JAR 精确读取。
- 增加固定目录、所有权标记、路径越界和软链接保护。

## 1.0.0-rc14

- 删除自定义薄荷身份、NBT/Lore/Data/数字 ID 匹配与额外材料消耗。
- 只监听 Pixelmon 原生薄荷事件。
- 只保留黑白名单、类别、权限、Vault、PlayerPoints、概率和冷却。
