# rc13 运行诊断

完整重启服务器后执行：

```text
/mintc status
```

确认配置路径以 `plugins/MintControl/config.yml` 结尾。

使用一次原生薄荷后：

- `全部ItemInteractionEvent` 增加：Pixelmon 事件总线正常；
- `原生薄荷事件` 增加：原生薄荷识别正常；
- `业务回调` 增加：规则逻辑已执行；
- `回调错误` 增加：查看控制台异常；
- `最近事件` 显示 `ignored-non-mint`：触发的是其他永久性道具，不是薄荷。
