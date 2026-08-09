# BreedConsumeControl 1.8.5

Pixelmon 1.12.2 / 8.4.2 + CatServer 孵蛋控制插件。

## 本版修复

1.8.4 的 `SynthesisPlan` 只能保存一个力量道具锁定项。双方同时携带狗圈时，代码随机选一方，另一方被丢弃。1.8.5 改为最多保存两个独立锁定项：

- 双方携带不同狗圈：两个对应 IV 都锁定；
- 双方携带相同狗圈且来源 IV 相同：合并为同一个锁定结果；
- 双方携带相同狗圈但来源 IV 不同：同一子代属性无法同时保存两个值，随机选择其中一方并写日志；
- 单狗圈模式继续支持另一方携带不变之石锁定原始性格；
- 双狗圈模式自动取消不变之石要求，性格按 Pixelmon 原生规则生成。

## 配置迁移

配置模型升级到 `config-version: 3`。首次冷启动会备份旧配置并迁移：

```yaml
rules:
  item-lock:
    require-exactly-one-everstone: true
    require-at-least-one-power-item: true
    allow-two-power-items: true
    require-different-parents: true
    require-power-item-perfect-iv: true
```

旧的 `require-exactly-one-power-item` 会迁移成 `require-at-least-one-power-item`，不会继续写入活动配置。

## 安装

1. 完整关闭服务器。
2. 删除全部旧版 BreedConsumeControl JAR。
3. 把 `BreedConsumeControl-1.8.5-双狗圈同时锁定修复.jar` 放入 `plugins/`。
4. 保留旧 `plugins/BreedConsumeControl/config.yml`，新版会自动备份并迁移。
5. 冷启动，不使用 PlugMan 或 `/reload` 替换 JAR。
6. 执行 `/breedconsume`，确认版本 1.8.5、配置模型 v3、双狗圈为 true。

## 成功日志

不同狗圈会分别出现两条验证日志，例如：

```text
Power Item lock verified: item=力量护腕, stat=攻击, IV=31, sourceParent=1, stage=PostCommit
Power Item lock verified: item=力量腰带, stat=防御, IV=31, sourceParent=2, stage=PostCommit
```
