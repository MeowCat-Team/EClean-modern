# NeoForge

[返回首页](../README-zh.md) · [English](NeoForge.md) | 简体中文

EClean 支持 Minecraft **26.1.2、26.2 和 26.3** 的 NeoForge 独立服务端。先安装与游戏版本一致的 NeoForge 服务端，再将匹配的 EClean JAR放入 `mods/`。所有版本均需 **Java 25**，原版客户端无需安装模组。

| Minecraft | 已验证 NeoForge | 文件 |
| --- | --- | --- |
| 26.1.2 | `26.1.2.114` | `EClean-Modern-0.3.8-neoforge-mc26.1.2.jar` |
| 26.2 | `26.2.0.88` | `EClean-Modern-0.3.8-neoforge-mc26.2.jar` |
| 26.3 | `26.3.0.66-beta` | `EClean-Modern-0.3.8-neoforge-mc26.3.jar` |

NeoForge 26.3 目前处于 Beta，其 EClean Modrinth 产物也标记为 Beta。26.1.2 与 26.2 的发布渠道保持不变。

EClean 产物已包含 Kotlin 与配置库，EClean 从 0.3.8 起无需 Architectury API。每份 EClean 产物声明精确的 Minecraft 版本。

使用表中已验证的加载器构建，或同一 Minecraft 版本线内更新的构建；模组至少要求上述加载器版本，并拒绝其他游戏版本。

## Moonrise 兼容性

EClean 从 0.3.8 起无需 Architectury API。Minecraft 26.2 已使用 Moonrise `1.1.2+151ce65` 在 Fabric 与 NeoForge 上验证统计、预览、清理、保护规则与自然消失回收。实体查询使用当前空间索引，不再读取被替换的原版 `entityManager`。升级时，如果其他模组也不需要 Architectury API，可以移除其 JAR。

## 配置与命令

配置位于 `config/eclean/`，默认预设为 `config/eclean/config/normal/config.yml`。NeoForge 与 Fabric 通过 `mod-common` 共用原生实现，使用相同的预设、语言迁移、重载校验、清理规则、统计、提醒、审计历史与内存垃圾桶。详见 [配置说明](Configuration-zh.md) 与 [命令参考](Commands-zh.md)。

世界参数使用 `minecraft:overworld` 等维度标识。原版匹配名称为 `ZOMBIE`、`DIAMOND` 等，模组类型保留命名空间，例如 `EXAMPLE:CUSTOM_MOB`。

```text
/eclean config validate
/eclean clean all minecraft:overworld --preview
/eclean stats gui minecraft:overworld
/eclean trash
/eclean show
```

`/ecl` 为别名。命令与菜单操作默认要求 OP 等级 2。统计与清理只访问已加载的完整区块，不加载新区块。回收保留原生物品数据组件，自然消失回收使用 NeoForge 原生到期事件，遵循物品自定义存活时长以及其他模组提供的延寿。菜单使用原版箱子界面，支持私密聊天搜索、手动存取与指定范围的密度清理确认。临时传送在 30 秒后返回；垃圾桶与返回位置保存在内存中，不跨服务器重启保留。

搜索文字在公开聊天格式化前捕获，不会广播。权限使用 NeoForge 原生 PermissionAPI，节点保持 `eclean.command.clean.all` 等点分格式，自定义处理器的判断为最终结果。详见 [权限说明](Permissions-zh.md)。

清理模块开启且 `cleanup.cleanWhenNoPlayers: true` 时，清理任务会保持空服 Tick；否则继续遵守空服暂停设置，未完成的手动任务会保持 Tick 直到结束。更新检查遵循 `global.updateCheck` 与 `advanced.update.enabled`；PlaceholderAPI 与 Bukkit bStats 适用于 Paper/Folia。

## 构建与验证

```shell
./gradlew :common:check :mod-common:check :neoforge:build -PminecraftVersion=26.1.2
./gradlew :common:check :mod-common:check :neoforge:build -PminecraftVersion=26.2
./gradlew :common:check :mod-common:check :neoforge:build -PminecraftVersion=26.3
python .github/scripts/mod-smoke.py neoforge 26.1.2
python .github/scripts/mod-smoke.py neoforge 26.2
python .github/scripts/mod-smoke.py neoforge 26.3
```

最终产物位于 `neoforge/build/<minecraftVersion>/libs/`，使用 `EClean-Modern-*-neoforge-mc*.jar`。CI 与手动 Release 使用 Fabric 共用的真实服务端测试，核对最终产物与 API 依赖，日志和 JSON 报告保存在 `.local/neoforge-smoke/<minecraftVersion>/`。Release 为每个游戏版本分别发布 NeoForge 版本，无需 Architectury API 依赖。
