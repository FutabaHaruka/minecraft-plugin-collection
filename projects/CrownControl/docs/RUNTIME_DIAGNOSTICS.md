# CrownControl 1.0.0-rc5 运行诊断

执行 `/crownc status`：

- `全部BottleCapEvent` 增加：Pixelmon 原生皇冠事件已到达。
- `原生金银皇冠事件` 增加：事件类型为 `GOLD` 或 `SILVER`，匹配成功。
- `业务回调` 增加：名单、权限、经济、概率和冷却规则已执行。
- `最近事件` 会显示 `capType=GOLD` 或 `capType=SILVER`，并附带真实物品类、注册名、翻译键与显示名。

Pixelmon 8.4.2 的皇冠交互发布 `BottleCapEvent`；旧 rc1 监听通用
`ItemInteractionEvent`，因此计数不增加并且无法拦截。rc2 已修复该事件类型不匹配。

银色王冠放行后可能停留在核验状态，直到玩家选择一个属性或达到配置超时。
