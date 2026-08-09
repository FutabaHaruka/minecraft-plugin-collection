# CrownControl 1.0.0-rc5 构建验证

验证日期：2026-07-27

## 本轮根因

rc2 已正确监听 Pixelmon 8.4.2 的 `BottleCapEvent`，但极限训练状态读取仍调用了不存在的：

```text
IVStore#getHypertrainedArray()
IVStore#getHyperTrainedArray()
```

目标 Pixelmon 8.4.2 的 `IVStore` 实际公开接口为：

```text
IVStore#isHyperTrained(StatsType)
StatsType#getStatValues()
```

因此 rc2 会把状态读取判定为不可用，主动取消已匹配的皇冠事件，表现为皇冠完全不生效。

## rc3 修复

- 保留 `BottleCapEvent` 与 `EnumBottleCap.GOLD/SILVER` 精确匹配。
- 从 `StatsType.getStatValues()` 获取六项属性。
- 对每一项调用 `IVStore.isHyperTrained(StatsType)`。
- 读取顺序：HP、攻击、防御、特攻、特防、速度。
- 保留直接布尔数组访问器作为其他构建的兼容路径。
- 名单、权限、金币、点券、概率、冷却、审计、退款和原生皇冠消耗逻辑不变。

## 编译与测试

- `javac --release 8` 编译通过。
- 插件类：27 个。
- Java 字节码 major version：52。
- 反射模拟测试通过：`RC3_HYPER_TRAINING_REFLECTION_OK`。
- 最终 JAR 不包含 Bukkit、Forge、Minecraft 或 Pixelmon 外部类。

`CROWN_CONTROL_RC3_HYPER_TRAINING_READER_OK`
