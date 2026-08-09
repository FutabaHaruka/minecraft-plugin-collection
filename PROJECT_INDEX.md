# 项目清单

| 项目 | 当前整理版本 | 类型 | 源码完整度 | 建议 GitHub 状态 |
|---|---|---|---|---|
| BreedConsumeControl | 1.8.5 双狗圈同时锁定修复 | Bukkit/CatServer 相关插件 | 较完整，含 README、审计、构建脚本 | 可建仓库；先核对依赖许可证 |
| CrownControl | 1.0.0-rc8-p1 Forge事件桥修复 | Bukkit/CatServer 相关插件 | 完整度高，含 Maven `pom.xml`、README、LICENSE | 优先上传 |
| MintControl | 1.0.0-rc17-p2 运行桥路径修复 | Bukkit/CatServer 相关插件 | 完整度高，含 Maven `pom.xml`、README、LICENSE | 优先上传 |
| FakeCreativeGuard | 1.1.0 | Bukkit 插件 | 有 `src/main` 与 `plugin.yml`，缺标准构建文件 | 可上传源码；后续补 Maven/Gradle |
| HarukaExchange | 1.0.0 | Bukkit 插件 | 有源码、资源、测试/桩代码，缺标准构建文件 | 可上传源码；后续补 Maven/Gradle |
| ImmortalersDelightRecipeFix | 1.1.0 | Forge 1.20.1 Mod | 有源码与 `mods.toml`，缺标准 Gradle 工程文件 | 可上传源码；后续补 ForgeGradle 工程 |
| AyCore-CacheOptimized | 1.3.2-BETA | 既有 AyCore 的优化/补丁 | 局部源码 + compile stubs + 工具 | **公开前核对原 AyCore 许可** |
| PokeTaskPlugin | 2.10.0-BETA AIR点击保护/奖励结算修复 | 既有插件修复 | 仅局部源码与 stubs | **不要当作完整源码；先核对原插件许可** |
| PokeDecompose | 1.1.1 | 既有插件功能修复/扩展 | 当前包仅保留局部 Java、配置和说明 | **公开前核对原插件许可** |
| DailyStore | 1.0.2 ItemDataFix | 既有插件修复 | 当前无完整源码，仅配置与修复报告 | **建议只保留说明，不公开原 JAR** |
| Guild-CatServer-Fix | 3.0.4 指令参数最小修复 | 既有 Guild 插件二进制补丁 | 只有 class 补丁，无完整源码 | **建议仅记录补丁说明，不公开原 JAR/class** |
| Pixelmon TeraTurnOrderFix | V6 | Pixelmon 8.4.2 补丁 | 补丁源码 + ASM patcher | **仅发布你写的补丁源码/工具，不发布 Pixelmon 完整 JAR** |

## 本次未作为独立插件仓库的内容

以下属于配置包/菜单包/汉化资源，而不是独立 Java 插件源码：TrMenu 功能卡商店、PlayerWarps 汉化配置、PokeDialogue 配置、ajLeaderboards 排行榜配置、PixelmonPvp 排行榜配置等。建议另建 `minecraft-server-configs` 仓库存放。
