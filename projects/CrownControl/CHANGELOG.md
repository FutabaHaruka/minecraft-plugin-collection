# CrownControl 1.0.0-rc8

## 1.0.0-rc8

- Removed the runtime bridge URLClassLoader and all plugin-JAR URLs.
- Runtime hook classes are copied as bytecode from the exact CrownControl code source into a resource-free ClassLoader.
- Moved bundled defaults to `META-INF/crowncontrol/defaults.rc8`; no bundled `config.yml` or default YAML resource remains.
- Hot-unload cleanup now clears plugin, service, logger and reflected Method references even when Forge listener unregister fails.
- Stale event listeners become inert after detachment.

- Removed whole-JAR injection into Forge LaunchClassLoader.
- Added a private child-first runtime bridge classloader.
- Prevents `config.yml` and `plugin.yml` from entering the global resource namespace during hot loading.
- The private runtime classloader is closed on disable/reload.
- Keeps the previous dedicated-folder and owner-marker protections.

# Changelog

## 1.0.0-rc8

- 不再信任 Bukkit/CatServer 返回的数据目录叶名称。
- 优先根据 Bukkit 更新目录定位真实 `plugins` 根目录，兼容错误的同级或嵌套数据目录。
- 始终只写入 `plugins/CrownControl/`，错误分配的其他插件目录保持不变。
- 增加目录所有权标记、路径越界检查、运行时根目录变更检查和软链接拒绝。

## 1.0.0-rc5

- 修复 Pixelmon 8.4.2 皇冠拦截事件不匹配。
- 监听事件由通用 `ItemInteractionEvent` 改为皇冠实际发布的 `BottleCapEvent`。
- 皇冠判定改为 `EnumBottleCap.GOLD` / `EnumBottleCap.SILVER`，不再依赖注册名、翻译键或显示名。
- `/crownc status` 事件统计改为 `BottleCapEvent`。
- 名单、权限、金币、点券、概率、冷却、审计和原生核验逻辑保持不变。

## 1.0.0-rc1

- 从 MintControl rc12 的原生事件拦截架构独立派生 CrownControl。
- 仅识别 Pixelmon 原生金色王冠与银色王冠。
- 完整复用名单、类别、权限、经济、概率、冷却、审计和事件桥逻辑。
- 将成功核验从“性格变化”替换为“极限训练标记新增”。
- 银色王冠采用轮询等待，允许玩家完成原生属性选择。
- 审计字段改为操作前后极限训练快照。
