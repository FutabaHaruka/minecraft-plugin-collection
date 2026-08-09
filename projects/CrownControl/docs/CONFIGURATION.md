# CrownControl 配置说明

`rules`、`pokemon-lists`、`categories`、金币、点券、概率和冷却字段与 MintControl 的全局规则一致。

皇冠物品不需要配置：插件只接受 Pixelmon 原生金色王冠和银色王冠。`settings.native-verification-ticks` 是核验轮询间隔；`settings.native-verification-timeout-ticks` 是等待银色王冠属性选择完成的最长时间。

插件只扣除或退还配置中的金币与点券，不修改皇冠物品。
