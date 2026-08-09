# 配置目录隔离修复审计

## 已确认的旧实现风险

旧版没有直接使用 Bukkit 为插件分配的 `JavaPlugin#getDataFolder()`，而是取得父目录后再手工拼接固定目录名。该做法绕过了服务端核心对插件数据目录的所有权分配，在混合核心、热加载、目录代理或路径异常时不能提供可靠边界。

## 新版写入规则

- 只接受启动时 Bukkit 返回的原始 dataFolder。
- 不重新计算 `plugins/` 根目录，也不拼接兄弟插件目录。
- config、迁移备份和审计日志全部通过同一个 owned-path 校验器。
- 每次读取或保存前重新确认 dataFolder 没有改变。
- 拒绝 dataFolder/config/日志文件符号链接。
- 拒绝绝对路径与 `..` 越界路径。
- config 使用同目录临时文件加原子替换，防止部分写入。
- 初次生成配置时不覆盖已经存在的 config.yml。
- 路径验证失败会抛出错误并停止写入，不会回退到其他插件目录。

## 不会执行的操作

- 不遍历 `plugins/`。
- 不查找其他插件的 `config.yml`。
- 不调用其他插件的 `saveConfig()`、`reloadConfig()` 或 `YamlConfiguration.save()`。
- 不修改其他插件的数据目录。
