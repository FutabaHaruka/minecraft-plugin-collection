# CrownControl 1.0.0-rc5 自审计

## 根因

Pixelmon 1.12.2 8.4.2 的 `InteractionBottleCap` 在应用皇冠前发布
`com.pixelmonmod.pixelmon.api.events.pokemon.BottleCapEvent`。rc1 监听的是
`ItemInteractionEvent`，该路径不会收到皇冠事件。

## 修复

- 监听 `BottleCapEvent`。
- 使用 `event.getBottleCap()` 精确匹配 `GOLD` 与 `SILVER`。
- 使用 `event.getPokemon()`、`event.getPlayer()` 和 `event.getItemStack()` 进入原业务回调。
- 拒绝时取消同一个可取消的 `BottleCapEvent`，从而阻止 Pixelmon 后续极限训练与皇冠消耗。
- 不再以注册名、翻译键、显示名或语言文本作为身份条件。

## 不变部分

名单、精灵类别、权限、金币、点券、概率、冷却、审计、退款和极限训练结果核验均未改变。
