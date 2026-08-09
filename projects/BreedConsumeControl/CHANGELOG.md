# Changelog

## 1.8.5

- 修复双方携带力量道具时随机只保留一方的问题；
- `SynthesisPlan` 从单锁定字段改为最多两个 `PowerLock`；
- 父母区间算法和精确目标 V 算法均应用全部不同属性锁定；
- MakeEgg 与 PostCommit 两个阶段分别复核每个锁定项；
- 同类狗圈冲突增加明确处理和日志；
- 配置升级到 v3：`require-at-least-one-power-item` + `allow-two-power-items`；
- 旧 v2 配置自动备份和迁移；
- 保留狗圈 EVAdjusting 识别、NBT 兼容、父母消耗和严格状态持久化。

## 1.8.4

- 配置去重和规范化。
