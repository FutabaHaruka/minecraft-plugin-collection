# CrownControl 1.0.0-rc5 极限训练读取修复

## 症状

rc2 已能匹配 `BottleCapEvent`，但使用金色或银色王冠后仍然无法生效。

## 根因

Pixelmon 1.12.2 8.4.2 的 `IVStore` 不存在：

```text
getHypertrainedArray()
getHyperTrainedArray()
```

该版本实际公开的是：

```text
isHyperTrained(StatsType)
```

rc2 因读不到数组而返回“核验不可用”，随后主动取消 `BottleCapEvent`，所以原生皇冠不会产生效果。

## rc3 修复

插件从 `StatsType.getStatValues()` 取得六项属性，并依次调用：

```text
IVStore.isHyperTrained(StatsType)
```

读取顺序为 HP、攻击、防御、特攻、特防、速度。数组访问器仅作为其他构建的兼容路径。

## 保持不变

名单、类别、权限、金币、点券、概率、冷却、审计、原生物品消耗和银冠选择界面逻辑均未改变。
