# rc13 配置说明

## 精灵名单

```yaml
pokemon-lists:
  blacklist:
    - Mewtwo
  whitelist: []
  op-bypasses-blacklist: true
```

黑名单优先于白名单。白名单为空时关闭白名单限制；白名单通过后仍要继续判断精灵类别。

## 全局规则

```yaml
rules:
  enabled: true
  cooldown-seconds: 30
  cooldown-on: SUCCESS
  allowed-categories:
    - legendary
    - mythical
  costs:
    money: 100000.0
    points: 10
  chance:
    success-rate: 35.0
    failure-rate: 65.0
    consume-on: ATTEMPT
```

`consume-on` 只控制金币和点券：

- `ATTEMPT`：每次有效尝试扣除；
- `SUCCESS`：概率通过并准备放行原生操作时扣除，原生修改失败会退款；
- `FAILURE`：仅概率失败时扣除。

Pixelmon 原生薄荷物品始终由 Pixelmon 管理，不受该字段控制。
