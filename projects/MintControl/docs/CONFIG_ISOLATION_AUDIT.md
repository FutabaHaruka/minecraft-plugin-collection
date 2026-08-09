# 配置目录隔离审计

MintControl 的所有可写文件固定锚定于实际 `plugins` 根目录下的 `MintControl` 子目录：

```text
plugins/MintControl/
```

插件不会根据热加载器临时传入的其他插件目录写入文件。每次配置 I/O 前都会重新验证插件主类、插件名、plugins 根目录、固定目录目标、`.plugin-owner` 内容和软链接链。

config、迁移备份、审计日志与临时文件全部经过同一个 owned-path 校验器。绝对路径、`.`、`..`、路径越界、目录目标变化及软链接都会被拒绝。

目录隔离只能限制 MintControl 自身的 I/O；它不能阻止拥有相同操作系统权限的其他缺陷或恶意插件主动写入 `plugins/MintControl/`。
