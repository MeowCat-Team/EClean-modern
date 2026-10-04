# Fabric

[返回首页](../README-zh.md) · [English](Fabric.md) | 简体中文

EClean 支持 Minecraft **26.1.2 和 26.2** 的 Fabric 独立服务端。两个版本分别构建，模组声明精确的 Minecraft 依赖，请只安装与服务器版本匹配的一份。

| Minecraft | 文件 | 最低 Fabric API 版本 | Architectury API |
| --- | --- | --- | --- |
| 26.1.2 | `EClean-Modern-0.3.6-fabric-mc26.1.2.jar` | `0.155.3+26.1.2` | `20.1.16` |
| 26.2 | `EClean-Modern-0.3.6-fabric-mc26.2.jar` | `0.161.0+26.2` | `21.1.11` |

两个版本均需 Java 25、Fabric Loader 0.19.5 或更新版本。将 EClean、Fabric API，以及匹配游戏版本的 [Architectury API](https://modrinth.com/mod/architectury-api) Fabric 版放入服务器的 `mods/` 后启动。模组已包含 Kotlin 与配置库，无需 Fabric Language Kotlin；菜单使用原版箱子协议，客户端无需安装模组。Fabric 与 [NeoForge](NeoForge-zh.md) 通过 `mod-common` 共用原生服务端实现。

## 配置与命令

配置与语言文件位于 `config/eclean/`。normal 配置为 `config/eclean/config/normal/config.yml`，dev 配置位于 `config/eclean/config/dev/`。配置预设、语言迁移、原子重载、清理规则、预览、统计、告警、清理记录和共享垃圾桶均沿用 Paper 的共用实现。

世界名使用维度标识，如 `minecraft:overworld`、`minecraft:the_nether`、`minecraft:the_end` 或模组维度 ID。原版实体与物品规则仍使用 `ZOMBIE`、`DIAMOND` 等大写名称；模组类型保留命名空间，如 `EXAMPLE:CUSTOM_MOB`。

```text
/eclean config validate
/eclean config effective minecraft:overworld
/eclean clean all minecraft:overworld --preview
/eclean stats gui minecraft:overworld
/eclean trash
/eclean show
```

`/ecl` 为别名。清理与统计只检查已加载的完整区块，不为扫描新增区块加载票据。物品回收保留原生数据组件；自然消失回收只拦截物品寿命到期，拾取、损坏和区块卸载不会触发回收。垃圾桶内容保存在内存中，停服后不保留。

启用清理模块且 `cleanup.cleanWhenNoPlayers: true` 时，模组会保持空服 Tick，确保定时清理继续运行。关闭无人清理后，服务器可按 `pause-when-empty-seconds` 暂停；尚未完成的手动任务会先继续执行。原版 `/tick freeze` 仍由服务器控制。

菜单支持分类、排序、搜索、手动存入、取回、统计和密集实体查看；执行菜单清理前先显示规则预览，确认有效期为 30 秒。密集菜单临时传送会在 30 秒后返回并保留朝向，同一次服务器运行中断线重连也会恢复返回位置。搜索聊天不会公开发送，60 秒后超时，输入 `cancel` 可取消。

## 权限与集成

没有权限提供方时，命令与操作默认要求原版 OP 等级 2。模组使用 Fabric API 原生权限上下文，提供方使用资源标识：`eclean.command.clean.all` 对应 **`eclean:command.clean.all`**，`eclean.admin` 对应 **`eclean:admin`**。[权限列表](Permissions-zh.md) 中的能力与兼容别名均按这一规则转换。显式拒绝优先于别名、父权限与管理权限授权，也可单独为非 OP 玩家授予某项能力。

更新检查在后台运行，遵循 `global.updateCheck` 与 `advanced.update.enabled`。PlaceholderAPI 和 Bukkit bStats 集成适用于 Paper/Folia，Fabric 不注册这两项；共用配置仍允许读取这些字段，方便跨平台迁移。

## 构建

```shell
./gradlew :common:check :mod-common:check :fabric:build -PminecraftVersion=26.1.2
./gradlew :common:check :mod-common:check :fabric:build -PminecraftVersion=26.2
```

可用 `python .github/scripts/mod-smoke.py fabric 26.1.2` 或 `fabric 26.2` 启动最终产物的服务端测试。测试仅监听本机地址，在 `.local/fabric-smoke/` 创建专用世界和配置，并验证最终产物、外部 API 依赖、保护规则、预览、物品回收、重载与空服 Tick。CI 与 Release 都会执行，保存日志与 JSON 报告；`fabric-smoke.py` 保留为共用测试的兼容入口。

产物位于 `fabric/build/<minecraftVersion>/libs/`。安装包含领域代码、转换后的模组代码、资源与内置依赖的最终 `EClean-Modern-*-fabric-mc*.jar`，`dev` JAR 仅用于开发。Release 会分别发布 Fabric Modrinth 版本，标注精确的游戏版本，以及必需的 Fabric API 与 Architectury API。
