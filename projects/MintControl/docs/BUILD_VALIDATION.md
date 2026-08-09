# rc13 构建验证

验证日期：2026-07-27

## 编译

- 当前容器未预装 Maven，且容器网络无法下载 Maven，因此本轮未执行本地 `mvn package`；
- 使用 Java 编译器的 `--release 8` 对 `src/main`、`src/compileOnly` 与离线 Bukkit 签名桩完成完整编译；
- 编译得到插件类：27 个；
- 成品 Java 字节码 major version：52；
- 成品 JAR：`MintControl-1.0.0-rc14.jar`。

## JAR 边界

- JAR 仅包含 `cn/licry/mintcontrol/**`、`plugin.yml`、`config.yml` 和 Manifest；
- 未打入 Bukkit、Forge、Minecraft 或 Pixelmon 外部类；
- 未包含已删除的自定义薄荷/物品成本类；
- 插件类数量：27 个。

## 配置审计

- 默认配置不存在 `mints`；
- `rules.costs` 只存在 `money` 与 `points`；
- 不存在 `items`、NBT、Lore、Data 或自定义薄荷身份配置；
- `plugin.yml` 版本为 `1.0.0-rc14`；
- YAML 解析通过。

## 配置目录隔离

- 运行源码中不存在 `JavaPlugin#getConfig()`；
- 不存在 `JavaPlugin#saveConfig()`；
- 不存在 `JavaPlugin#reloadConfig()`；
- 使用独立 `YamlConfiguration.loadConfiguration(File)` / `save(File)`；
- 路径固定为服务器插件根目录下的 `MintControl/config.yml`；
- 审计日志固定写入同一专属目录下的 `logs`。

## 运行链路

- 事件桥仍注册 Pixelmon 专用事件总线；
- 只处理 Pixelmon 所属且具有原生薄荷标识的物品事件；
- 不执行任何自定义物品匹配；
- 不修改背包或扣除额外物品；
- 只调用 Vault 与 PlayerPoints 货币桥；
- 拒绝时取消原生事件，通过时放行 Pixelmon 自身处理。

## 结果

`RC12_YAML_AND_JAR_STRUCTURE_OK`
