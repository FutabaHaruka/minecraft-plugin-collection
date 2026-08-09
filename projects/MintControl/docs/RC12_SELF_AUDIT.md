# rc13 自审计报告

## 用户反馈对应修复

### 1. 取消自定义薄荷

删除 `MintDefinition`、`ItemRule`、`ItemSnapshot`、`ItemMatcherService`、`NativeItemBridge` 和 `NbtBridge`。配置中不存在 `mints`。

### 2. 删除物品/NBT消耗

删除 `CostItemRule`、`InventoryPlan` 及所有背包槽位修改代码。`CostService` 只调用 Vault 和 PlayerPoints。

### 3. 只拦截原生薄荷

LaunchClassLoader 事件桥先确认物品属于 Pixelmon，并在原生类名、注册名、翻译键或显示名中具有 `mint/薄荷` 标识。非薄荷的其他 `ItemInteractionEvent` 直接忽略。

### 4. 配置串到其他插件目录

`PluginConfig` 不再调用 Bukkit 的 `getConfig/saveConfig/reloadConfig`。它取得当前插件数据目录的父目录，并强制使用名为 `MintControl` 的子目录。所有配置写入均调用独立 `YamlConfiguration#save(File)`。

审计日志同样使用 `PluginConfig#getDataFolder()`，不会使用其他插件的数据目录。

## 字节码检查

- Java major version：52；
- JAR 不包含 `org.bukkit`、`net.minecraft`、`net.minecraftforge` 或 `com.pixelmonmod` 外部类；
- `PluginConfig.class` 不包含 `JavaPlugin.getConfig/saveConfig/reloadConfig` 调用；
- 不存在被删除的物品规则类；
- 配置和 plugin.yml 均通过 YAML 解析。
