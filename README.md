# EClean Modern
> [!IMPORTANT] 
> 该插件为`EClean Modern`, 由`CoffeePopStudio`组织维护, 插件旨意完善原版`EClean`插件的功能, 解决原版插件在新版本中存在的兼容性问题, 并增加更多功能和配置选项

## Why
> [!NOTE]
> 原EClean插件[在Folia支持的issue](https://github.com/4o4E/EClean/issues/39)中
> 
> 明确说明`没有时间, 现在没有任何对folia的支持计划`, 而且Eplugin框架是基于旧版bukkit api开发, 而并非paper api
> 
> 这也就会导致很多api在paper中标记`Deprecated`甚至`Removed`, 这也就导致了原版插件可能在未来版本中无法使用
> 
> 该插件的开发目的就是为了在Folia环境下支持EClean插件的功能, 并且在原版插件的基础上增加更多功能和配置选项

## 指令

> 插件主命令为`/eclean`，包括缩写`/ecl`，如果`/ecl`与其他插件冲突，请使用`/eclean`

- `/eclean reload` 重载插件, 重载后计划清理的任务将重新开始计时
- `/eclean clean` 立刻执行一次清理(不显示清理前提示，在有玩家的服务器中慎用)
- `/eclean clean --preview` 预览一次清理，不真正删除实体
- `/eclean clean entity` 立刻执行一次实体清理(不显示清理前提示)
- `/eclean clean entity <世界名>` 立刻在指定世界执行一次实体清理(不显示清理前提示)
- `/eclean clean drop` 立刻执行一次掉落物清理(不显示清理前提示)
- `/eclean clean drop <世界名>` 立刻在指定世界执行一次掉落物清理(不显示清理前提示)
- `/eclean clean chunk` 立刻执行一次密集实体清理(不显示清理前提示)
- `/eclean clean chunk <世界名>` 立刻在指定世界执行一次密集实体清理(不显示清理前提示)
- `/eclean entity <实体名>` 统计当前世界每个区块的指定实体
- `/eclean entity <实体名> <世界名>` 统计指定世界每个区块的指定实体
- `/eclean entity <实体名> <世界名> <纳入统计所需数量>` 统计指定世界每个区块的指定实体并隐藏不超过指定数量的内容
- `/eclean entity <实体名> <世界名> <区块X> <区块Z>` 查看指定区块内该类型的实体列表
- `/eclean stats` 统计当前所在世界的实体和区块统计
- `/eclean stats <世界名>` 统计实体和区块统计
- `/eclean stats gui [世界名]` 打开统计 GUI
- `/eclean status all` 统计全服所有世界的实体/区块
- `/eclean status <世界名>` 查看单个世界状态
- `/eclean history [数量]` 查看最近清理记录
- `/eclean top entity [数量] [世界名]` 查看实体数量最多的类型
- `/eclean top chunk [数量] [世界名]` 查看实体数量最多的区块
- `/eclean trash` 打开垃圾桶（支持分类、搜索、排序）
- `/eclean trash stats` 查看垃圾桶统计信息(每个聚合条目的类型/数量/剩余时间)
- `/eclean show` 打开密集实体统计信息菜单
- `/eclean tp <世界名> <x> <y> <z>` 直接传送到指定坐标(管理员)

自动清理和 `/eclean clean ...`（包括指定世界与 `--preview`）均遵守对应功能的 `enabled`、`disabledWorlds` 和 `perWorld.worlds.<世界名>.enabled`，指定世界不会绕过这些限制。`--preview` 统计实际规则选中的对象，不更新上次清理计数、历史或倒计时，也不广播清理完成消息和密集区警报。

`cleanup.cleanWhenNoPlayers: false` 仅限制无人在线时的自动清理；管理员仍可通过命令手动执行清理。

密集实体菜单右键先预览，显示区块、实体类型、预计清理和保留数量；30 秒内确认后才执行。它与自动密度清理共用规则和世界开关，保护对象及限额内对象会保留。确认前配置变化需要重新预览，后来出现的实体不会被加入本次删除范围。

掉落物清理仅在 `trashcan.enabled` 和 `trashcan.collectFromDropCleanup` 都开启时回收入垃圾桶；关闭任一项表示直接删除。回收保留完整数量和元数据，回收失败保留原实体。垃圾桶仍为内存存储，重启后内容不会保留。

垃圾桶前五行是物品，第六行是翻页、分类、排序、搜索控件。顶部拖拽、数字键换物和双击归集不会转移展示物品；下方背包左键存入一个、右键存入一半、Shift+左键存入整组。搜索会关闭菜单，并在 60 秒内接收一次英文物品 ID 关键词；输入 `cancel` 返回菜单，超时后会提示恢复正常聊天。统计 GUI 支持翻页。

> 统计功能支持聊天栏点击：
> - `/eclean stats` 里的实体类型可点击，查看该类型在各个区块的分布
> - `/eclean entity` 里的区块行可点击，查看该区块内该类型的所有实体
> - 实体坐标可点击，点击后直接传送

## 权限

插件为每个命令/操作注册了独立权限，均默认为 `op`；`eclean.admin` 为兼容旧版的综合管理权限，会授予以下全部能力，`eclean.trash` 仍等价于打开垃圾桶。

命令、菜单和补全使用一致的权限判断。权限插件显式撤销某项能力（包括其父权限或兼容别名）时，拒绝优先于其他别名、父权限或 `eclean.admin` 的授权；未授权非 OP 玩家的默认拒绝不会阻止单独授予叶子权限。

- `eclean.admin` 使用全部 EClean 功能（兼容旧版综合管理权限）
- `eclean.trash` 打开共享垃圾桶（等价 `eclean.command.trash.open`）
- `eclean.command.debug` 切换 Debug 消息
- `eclean.command.reload` 重载插件配置和语言
- `eclean.command.clean.all` 执行一次完整清理
- `eclean.command.clean.entity` 执行实体清理
- `eclean.command.clean.drop` 执行掉落物清理
- `eclean.command.clean.chunk` 执行密集实体清理
- `eclean.command.clean.preview` 使用 `--preview` 预演模式
- `eclean.command.clean.trash` 清空共享垃圾桶
- `eclean.command.trash.open` 打开共享垃圾桶
- `eclean.command.trash.stats` 查看垃圾桶统计
- `eclean.command.stats.self` 统计当前世界
- `eclean.command.stats.world` 统计指定世界
- `eclean.command.stats.gui` 打开统计 GUI
- `eclean.command.status.world` 查看指定世界状态
- `eclean.command.status.all` 查看全服状态
- `eclean.command.entity.self` 查询当前世界实体分布
- `eclean.command.entity.world` 查询指定世界实体分布
- `eclean.command.entity.chunk` 查询指定区块实体详情
- `eclean.command.players` 查看在线玩家坐标
- `eclean.command.show` 打开密集实体统计菜单
- `eclean.command.show.teleport` 在密集实体菜单中传送
- `eclean.command.show.clean` 在密集实体菜单中删除实体
- `eclean.command.history` 查看清理历史
- `eclean.command.top.entity` 查看实体类型排名
- `eclean.command.top.chunk` 查看区块排名
- `eclean.command.teleport` 使用 `/eclean tp` 传送
- `eclean.alerts` 接收插件自动提醒

## PlaceholderAPI

- `%eclean_before_next%` - `距离下一次清理的时间, 单位秒`
- `%eclean_before_next_formatted%` - `距离下一次清理的时间, 格式化的时间`
- `%eclean_last_drop%` - `上次清理的掉落物数量`
- `%eclean_last_living%` - `上次清理的生物数量`
- `%eclean_last_chunk%` - `上次清理的密集实体数量`
- `%eclean_trashcan_countdown%` - `最早到期条目的剩余时间(条目永不过期时为空桶, 为0), 单位秒`
- `%eclean_trashcan_countdown_formatted%` - `最早到期条目的剩余时间, 格式化的时间`
- `%eclean_total_entities%` - `全服实体总数`
- `%eclean_total_chunks%` - `全服已加载区块总数`
- `%eclean_world_<世界名>_entities%` - `指定世界的实体总数`
- `%eclean_last_clean_time%` - `上次清理时间`
- `%eclean_next_clean%` - `距离下次清理的秒数`
- `%eclean_next_clean_formatted%` - `距离下次清理的格式化时间`
- `%eclean_trashcan_entries%` - `垃圾桶条目数`
- `%eclean_trashcan_total%` - `垃圾桶物品总数量`
- `%eclean_history_count%` - `清理历史次数`

## 配置

EClean 提供两套配置预设：

- `normal`（默认）：简洁基础配置，只提供常用项。
  - 配置文件：`config/normal/config.yml`
  - 适合大多数服务器，避免给普通用户过多繁琐配置。
- `dev`：完整高级配置，适合高度自由度的技术玩家。
  - 配置文件目录：`config/dev/`
  - 包含全部现有参数，以及菜单 UI、调度性能、PAPI / bStats / 更新检查、垃圾桶行为等高级项。
  - 每个 dev 文件顶部都有“仅适合技术玩家”的警告。

`drop.protectWrittenBook` 同时保护已签名的成书和已有内容的书与笔，空白书与笔仍按照其他清理规则处理。

切换方式：

- 修改根目录 `config.yml` 的 `profile: normal|dev`，然后重启；或
- 使用命令：
  - `/eclean config show` 查看当前预设
  - `/eclean config profile <normal|dev>` 立即切换并热重载

首次从旧版升级时，插件会把旧的根目录配置文件备份到 `config-backup-<时间戳>/`，然后生成新的 normal/dev 配置模板。

## 下载
- [最新版](https://github.com/CoffeePopStudio/EClean-modern/releases/latest)

## 更新记录
详见 [ChangeLog](https://github.com/CoffeePopStudio/EClean-modern/blob/modern/docs/Changelog.md)
