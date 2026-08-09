# CrownControl 1.0.0-rc8

面向 Minecraft 1.12.2 混合端的 Pixelmon 原生金银皇冠拦截插件。

## 功能边界

- 直接监听 Pixelmon 8.4.x 原生 `BottleCapEvent`。
- 以事件中的 `EnumBottleCap.GOLD` / `EnumBottleCap.SILVER` 判定金色王冠与银色王冠。
- 不再依赖注册名、翻译键或中文显示名，因此资源包翻译和名称差异不会导致漏拦截。
- 其他 Pixelmon 道具、薄荷、自定义 NBT 道具和改名原版物品全部忽略。
- 规则链与 MintControl 一致：黑名单、白名单、类别、权限、冷却、金币/点券、概率、审计。
- 拒绝时取消 Pixelmon 原生事件；通过时放行，由 Pixelmon 自行执行极限训练并消耗皇冠。
- 插件不直接修改 IV，也不主动移除或返还皇冠物品。
- 成功核验比较操作前后的极限训练标记；银色王冠支持等待原生属性选择完成。

## 安装

1. 将 `CrownControl-1.0.0-rc8.jar` 放入 `plugins/`。
2. 完整关闭并重新启动服务器。
3. 配置文件位于 `plugins/CrownControl/config.yml`。
4. 使用 `/crownc status` 检查 Pixelmon 事件桥与统计。

## 常用命令

- `/crownc status`：查看事件桥、事件计数和规则状态。
- `/crownc check <1-6>`：查看队伍精灵名单、类别和极限训练状态。
- `/crownc list`：查看全局规则。
- `/crownc blacklist ...`、`/crownc whitelist ...`：管理名单。
- `/crownc reload`：重载配置。

## 核心权限

- `crowncontrol.use`
- `crowncontrol.crown.default`
- `crowncontrol.bypass.cost`
- `crowncontrol.bypass.chance`
- `crowncontrol.bypass.cooldown`
- `crowncontrol.admin`

## 注意

离线构建验证不能替代目标 CatServer/Pixelmon 修改版的实服交互验证。正式开放前请分别测试金色王冠、银色王冠选择界面、概率失败、余额不足、黑白名单和退款路径。
## 目录隔离修复

本版本不使用 Bukkit 返回的目录叶名称作为写入目标。即使混合端把本插件的数据目录错误指向其他插件目录，也只会写入 `plugins/CrownControl/config.yml`。目录内会创建 `.plugin-owner` 所有权标记；检测到路径越界、目录目标变化或软链接时会停止 I/O。

