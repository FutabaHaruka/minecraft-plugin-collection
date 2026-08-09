# FakeCreativeGuard 1.1.0

目标环境：Minecraft 1.12.2 / Bukkit-Spigot API / CatServer 类混合核心。

## 功能

1. 拦截未授权玩家切换到 `GameMode.CREATIVE`。
2. 纯事件驱动，不进行每 Tick 全服玩家扫描。
3. 未授权创造玩家的任何方块交互会被取消；箱子等容器额外通过 `InventoryOpenEvent` 再拦截一次。
4. 玩家尝试切换到 `CREATIVE` 时通过 `PlayerGameModeChangeEvent` 立即阻止；登录时也检查一次异常创造状态。
5. 如果安装 ProtocolLib，则在数据包层拦截客户端 `SET_CREATIVE_SLOT` 包。只有“服务端确实为创造 + 拥有 bypass 权限”的玩家才允许发送。
6. 所有违规写入独立日志：`plugins/FakeCreativeGuard/security.log`.

## 权限

`fakecreativeguard.bypass`

默认仅 OP 拥有。正常需要使用创造模式的非 OP 管理员必须显式授予此权限。

## 安装

- 将 `FakeCreativeGuard-1.1.0.jar` 放入 `plugins/`。
- 强烈建议同时安装与你的 1.12.2 服务端兼容的 ProtocolLib 4.x，这样可以启用最前端的创造背包包拦截。
- 完整重启服务器，不建议使用 PlugMan/热卸载。

## 数据目录

本插件不会读取或修改以下任何插件目录或配置：

- PlotSquared
- Essentials
- LuckPerms
- 其他任何插件

本插件自身只使用：

`plugins/FakeCreativeGuard/security.log`

没有 `config.yml`。

## 记录示例

`2026-08-07 14:30:00.123 | type=PACKET_SET_CREATIVE_SLOT | player=Test | uuid=... | ip=... | world=plot | xyz=10.00,65.00,20.00 | gamemode=SURVIVAL | detail=illegal creative inventory packet while mode=SURVIVAL`

## 注意

这是针对“伪创造/异常创造背包包/未授权创造状态导致地皮容器放行”的专用防护，不是通用反作弊插件。
