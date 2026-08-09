# GitHub 上传步骤

## 方案 A：一个总仓库（当前整理包就是这种结构）

在 GitHub 新建空仓库，例如 `minecraft-plugin-collection`，不要勾选自动创建 README（本地已经有）。

Windows PowerShell / Git Bash：

```bash
cd Minecraft-Plugins-GitHub-Source
git init
git branch -M main
git add .
git commit -m "Initial import of Minecraft plugin sources"
git remote add origin https://github.com/<你的用户名>/minecraft-plugin-collection.git
git push -u origin main
```

## 方案 B：每个插件一个仓库（更推荐长期维护）

例如上传 `CrownControl`：

```bash
cd projects/CrownControl
git init
git branch -M main
git add .
git commit -m "Initial release: CrownControl rc8-p1"
git remote add origin https://github.com/<你的用户名>/CrownControl.git
git push -u origin main
```

然后对 MintControl、BreedConsumeControl 等重复执行。

## JAR 发布

源码仓库中不要直接长期堆积每个版本 JAR。编译好的 JAR 更适合作为 GitHub Release 附件。当前整理出的 JAR 在另一个本地包 `Minecraft-Plugins-Release-Assets-LOCAL-ONLY.zip` 中。

第三方衍生项目（Guild、PokeTask、AyCore、PokeDecompose、DailyStore、Pixelmon 补丁）在上传 Release 前必须先确认允许再分发。
