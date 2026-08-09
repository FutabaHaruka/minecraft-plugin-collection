# BreedConsumeControl 1.8.5 自审与验证报告

## 根因

1.8.4 的 `SynthesisPlan` 只有一组 `powerParent/powerStatType/powerLockedIv` 字段。
双方都携带力量道具时，`validateItemLocks()` 使用随机数选择其中一方，另一方锁定信息直接丢失。
随后父母区间算法、精确目标 V 算法以及 MakeEgg/PostCommit 复核阶段都只能应用这一组锁定。

## 修复

- 新增 `PowerLock`，计划对象保存最多两个独立锁定项；
- 父母1和父母2的力量道具分别解析和验证；
- 父母区间模式遍历全部锁定项写入；
- 精确目标 V 模式把全部不同属性锁定计入目标 V；
- MakeEgg 和 PostCommit 均逐项使用 `StatsType` 写入并读回验证；
- 不同狗圈锁定两个属性；
- 相同狗圈且 IV 相同合并为共同结果；
- 相同狗圈但 IV 不同随机选一方并输出警告，因为一个子代属性无法同时保存两个不同值。

## 配置

- 配置模型升级为 `config-version: 3`；
- 删除活动配置中的旧键 `require-exactly-one-power-item`；
- 新键：
  - `rules.item-lock.require-at-least-one-power-item: true`
  - `rules.item-lock.allow-two-power-items: true`
- v2 及更早配置会在冷启动时备份并自动迁移；
- 单狗圈时继续要求另一方携带不变之石；
- 双狗圈时自动免除不变之石要求，两个 IV 锁定同时执行。

## 验证结果

- Java 8 编译：通过；
- 插件版本、Manifest 版本：1.8.5；
- 配置模型：v3；
- 默认配置完整路径重复键：0；
- 默认配置叶子键：51；
- RuntimeSettings 配置读取路径：24，缺失：0；
- JAR 重复 ZIP 条目：0；
- JAR 中默认配置：1 份；
- 未打包 Bukkit、Forge、Minecraft、CatServer 或 Pixelmon compileOnly 类；
- 六种狗圈到 `StatsType` 映射：通过；
- EVAdjusting 内部属性识别：通过；
- 双不同属性锁在编译字节码计划中保持 2 项：通过；
- 双相同属性锁合并为 1 项：通过；
- 父母区间随机边界测试：通过；
- 严格合成组合回归：15102 组通过；
- CatServer/Spigot/混淆 NBT 方法解析：通过；
- MakeEgg 与 PostCommit 双阶段逐锁验证代码：存在并通过静态审计。

## 未完成的验证

当前环境没有启动用户实际 CatServer 世界，因此没有完成真实牧场出蛋的端到端运行测试。安装后应使用两个不同狗圈测试一次，并确认 PostCommit 阶段出现两条 `Power Item lock verified` 日志。
