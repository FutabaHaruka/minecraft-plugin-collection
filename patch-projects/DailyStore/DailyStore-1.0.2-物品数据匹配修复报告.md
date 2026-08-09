# DailyStore 完整物品数据匹配修复报告

## 输入数据检查

- `ShopItem`：1 条
- `TodayItem`：1 条
- `BuyShopItem`：46 条
- `BuyTodayItem`：14 条

所有记录均采用 ProtocolLib `StreamSerializer` 生成的 Base64 物品数据。
修正版会把模板和玩家背包物品数量统一为 1，再比较完整序列化数据。

## 匹配字段

- 数字物品 ID／CatServer Forge 物品映射
- Data／耐久值
- 显示名称与 Lore
- 附魔和 ItemMeta
- Pixelmon／Forge NBT
- 不比较堆叠数量

## 原版问题

原版只调用 `ItemStack.isSimilar()`；在 CatServer 1.12.2 中，部分 Forge/Pixelmon 隐藏 NBT 不一定进入 Bukkit ItemMeta，导致实际物品无法被收购匹配。

## 修复内容

- 新增完整序列化数据匹配器。
- 收购数量预检查和实际扣除使用同一匹配规则。
- 修复旧版按数组长度多循环一格的边界风险。
- 明确声明 ProtocolLib 为加载依赖。
- 保留原有命令、界面、价格和每日刷新数据。

## 数据指纹抽查

- `ShopItem[0]`：记录ID `OQ8JVyz4`，数字物品ID `339`，保存数量 `1`，Data `0`，SHA-256前16位 `29cce528e8499dea`
- `BuyShopItem[0]`：记录ID `7G02e7W5`，数字物品ID `5869`，保存数量 `1`，Data `0`，SHA-256前16位 `ebd8570450d35df0`
- `BuyShopItem[1]`：记录ID `91V7133a`，数字物品ID `5435`，保存数量 `1`，Data `0`，SHA-256前16位 `39881c67c7642772`
- `BuyShopItem[2]`：记录ID `2f65MDv8`，数字物品ID `5403`，保存数量 `1`，Data `0`，SHA-256前16位 `3600a3dcc3125db3`
- `BuyShopItem[3]`：记录ID `De981660`，数字物品ID `5383`，保存数量 `1`，Data `0`，SHA-256前16位 `afefe007c313bd7d`
- `BuyShopItem[4]`：记录ID `rI67j229`，数字物品ID `5394`，保存数量 `1`，Data `0`，SHA-256前16位 `39880de751bea270`
- `BuyShopItem[5]`：记录ID `677Mo5ZZ`，数字物品ID `5383`，保存数量 `1`，Data `0`，SHA-256前16位 `afefe007c313bd7d`
- `BuyShopItem[6]`：记录ID `0J84070V`，数字物品ID `5380`，保存数量 `1`，Data `0`，SHA-256前16位 `ce1ebc1bd6f4f1b7`
- `BuyShopItem[7]`：记录ID `G67A3A78`，数字物品ID `5382`，保存数量 `1`，Data `0`，SHA-256前16位 `6328f7a65fa3817e`

## 安装

1. 完整关闭服务器。
2. 备份 `plugins/DailyStore/data.yml`。
3. 删除旧的 `DailyStore-1.0.1.jar`。
4. 放入 `DailyStore-1.0.2-ItemDataFix.jar`。
5. 将本包内 `DailyStore/` 配置目录覆盖回 `plugins/DailyStore/`。
6. 确认服务器中只保留一个 DailyStore JAR，并且 ProtocolLib 已正常加载。
