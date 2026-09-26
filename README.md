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
- `/eclean clean all <世界名> [--preview]` 清理或预演指定世界的全部清理模块
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
- `/eclean trash [open]` 打开垃圾桶（支持分类、搜索、排序）
- `/eclean trash stats` 查看垃圾桶统计信息(每个聚合条目的类型/数量/剩余时间)
- `/eclean show` 打开密集实体统计信息菜单
- `/eclean tp <世界名> <x> <y> <z>` 直接传送到指定坐标(管理员)

`top` 的参数位置固定为“类型 → 数量 → 世界”。指定世界时必须先写数量，例如 `/eclean top entity 10 123` 查询名为 `123` 的世界；不再靠参数能否解析为数字猜测世界。数量范围为 1–100。未知世界明确报错。`--preview` 可以放在 `clean` 之后的任意参数位置。

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
- `eclean.command.config` 查看、校验、比较配置及切换 profile
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
- `%eclean_last_drop%` - `最近完成的掉落物清理请求实际删除的实体数，全服请求为各世界之和（一组物品算一个实体）`
- `%eclean_last_living%` - `最近完成的生物清理请求实际删除的实体数，全服请求为各世界之和`
- `%eclean_last_chunk%` - `最近完成的密度清理请求或 GUI 实际删除的实体数，全服请求为各世界之和`
- `%eclean_trashcan_countdown%` - `最早到期条目的剩余秒数，空桶或全部条目无定时过期时为 0`
- `%eclean_trashcan_countdown_formatted%` - `最早到期条目的剩余时间, 格式化的时间`
- `%eclean_total_entities%` - `全服实体总数`
- `%eclean_total_chunks%` - `全服已加载区块总数`
- `%eclean_world_<世界名>_entities%` - `指定世界的实体总数`
- `%eclean_last_clean_time%` - `最近一次有审计记录的执行结束时间（可为零删除或失败）`
- `%eclean_last_removal_time%` - `最近一次实际删除实体的时间，无删除记录时为空`
- `%eclean_total_removed_entities%` - `本次进程运行内累计实际删除实体数，不包含垃圾桶清空的物品件数`
- `%eclean_next_clean%` - `距离下次清理的秒数`
- `%eclean_next_clean_formatted%` - `距离下次清理的格式化时间`
- `%eclean_trashcan_entries%` - `垃圾桶条目数`
- `%eclean_trashcan_total%` - `垃圾桶物品总数量`
- `%eclean_history_count%` - `当前保留的历史记录条数，最多 100 条`

## 配置

EClean 提供两套独立的配置预设，彼此不继承。切换 profile 会切换整套清理规则；normal 同样支持全部配置字段，单文件与分文件只是组织方式：

- `normal`（默认）：单文件配置，模板以常用项为主。
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
  - `/eclean config show` 查看当前预设与路径
  - `/eclean config validate` 只读校验磁盘上的当前 profile 与语言，不迁移、生成文件或激活服务
  - `/eclean config diff` 对比运行中配置与磁盘候选，显示变化的模块及旧/新值，不执行重载
  - `/eclean config effective [世界名]` 查看运行中的规则；指定世界后解析其开关、距离和周期覆盖并列出来源
  - `/eclean config profile <normal|dev>` 立即切换并热重载

首次从旧版升级时，插件会先验证旧配置，将已有的保护规则、禁用世界和功能开关迁移到 normal，并在 `config-backup-<时间戳>/` 永久保留原文件备份。缺失的旧功能配置不会自动启用清理；无法识别的字段需要修正后再加载。

修改文件后使用 `/eclean reload`。配置和语言会一起验证、一起生效；失败时继续使用上一次有效配置。首次启动加载失败时暂停自动和手动清理及回收，修正文件后可重载恢复。误删已有配置文件也会报错，不会重新生成可能扩大删除范围的默认规则。

normal 也支持可选的 `advanced` 节点，例如：

```yaml
advanced:
  bStats:
    enabled: false
  papi:
    enabled: true
  update:
    enabled: false
```

这些开关、统计告警及调度设置在重载时生效。dev 将相同选项拆分到多个文件；它不是开发/测试模式，不会自动放宽清理保护。

Cron 使用 Quartz 格式，例如每天 03:00 为 `0 0 3 * * ?`；`null` 或空字符串表示使用间隔调度。Cron 与各世界的 `intervalSeconds` 不能同时设置。使用间隔调度时，每个世界分别计时。

清理和统计按区块分批扫描，默认每批最多 100 个区块，上一批完成后等待 10 tick，再提交下一批。清理会跳过已卸载区块并在结果中计数；统计遇到区块卸载或任务失败会报告采集失败，保留已有完整缓存。菜单颜色分别替换默认菜单中的 gold（标题）、gray（说明）、yellow（操作提示），不会修改物品本身的元数据。

PlaceholderAPI 的实体数和区块数来自已加载区块的缓存，每 10 秒发起一次刷新，结果在扫描完成后发布。首次扫描完成前总数为 0，世界名称保留大小写。占位符请求不会触发世界扫描或加载区块。

Folia 不支持插件热卸载；关闭服务器后再替换插件。临时传送在传送成功后开始倒计时，退出后会在本次服务进程中的下次登录尝试返回；返回位置不跨服务器重启保存。


`drop.mode` 和 `living.mode` 使用 `remove-matching`（只清匹配项）或 `keep-matching`（保留匹配项）。旧 `blacklistMode` 仍兼容，显式 `mode` 优先。空匹配列表配合 `remove-matching` 不清任何对象，配合 `keep-matching` 会选择所有未受保护对象。密度模块没有 `entityLimits` 时不会删除实体。

`chunkDensity.protectTamed`、`chunkDensity.protectAllay` 默认均为 `true`，分别保护驯服生物和悦灵。这是密度模块自己的保护设置，和 `living` 不互相覆盖。命名、拴绳、骑乘保护仍由各自模块的 `settings` 控制。`alertThreshold` 按区块内的单一实体类型计数（包含受保护对象），不会将牛和羊相加；一个限额正则匹配多类实体时共享该限额，重叠规则按配置顺序处理。

更新检查统一推荐使用 `advanced.update.enabled`；旧 `global.updateCheck: false` 仍会禁用检查。首次检查在启动/启用后约 20 秒，之后每 6 小时检查。版本按 [SemVer](https://semver.org/spec/v2.0.0.html) 比较，允许 `v` 前缀；正式版只提示更高的正式版本，预发布安装可提示更高的预发布版本。检查失败会节流提示，不自动下载或执行更新。

历史按“实际执行的世界与模块”记录，包含来源、操作者、范围、配置版本、耗时、实际删除数、失败删除数、跳过区块数和执行中断标记。自动、命令和菜单入口共用记录；合并的并发请求只记一次实际执行，预览不记历史。所有实体清理数量均按实体计数，垃圾桶数量按物品件数计数。历史和累计计数在重启后重置。

跨世界统计 GUI 同时需要 `eclean.command.stats.gui` 与 `eclean.command.stats.world`。查看实体分布、查看区块详情和传送仍分别检查对应权限。普通传送及菜单传送等待平台实际结果，失败不会提示成功；临时传送开关在当前菜单会话中持续生效，关闭并重新打开菜单后恢复默认关闭。

中英文消息检查键集合和参数契约，缺失键回退到对应内置语言。自定义翻译的参数与内置模板不一致时，该键回退并记录提示，其他自定义键继续使用。普通消息参数按文本转义，内部富文本和点击动作使用显式组件构造。实体名称展示 Minecraft 翻译和精确 ID，物品本身保留原生名称；插件消息仍使用全局语言，搜索仍使用英文 Material ID。格式化时间使用配置语言。

垃圾桶的“无定时过期”只适用于当前服务进程，不保证重启恢复；新物品合入旧条目不会延长该条目的到期时间。

## 构建与依赖更新

默认构建目标为 common/paper，需要 JDK 25。Paper API 固定为 `26.1.2.build.74-stable`；项目提交 Gradle 依赖锁与 SHA-256 校验清单，wrapper 下载使用官方 SHA-256，CI Action 固定提交。校验清单以当前成功构建的依赖为基线，并不等同于漏洞扫描或所有发布方签名验证。

```shell
./gradlew build
```

更新依赖时先修改版本目录，再显式生成候选锁与校验数据，审查下载来源和校验差异后提交，日常 CI 不自动刷新这些文件：

```shell
./gradlew build --write-locks --write-verification-metadata sha256
```

实验性的 Fabric/NeoForge 占位模块不属于这套已验证的构建范围。

## 下载
- [最新版](https://github.com/CoffeePopStudio/EClean-modern/releases/latest)

## 更新记录
详见 [ChangeLog](https://github.com/CoffeePopStudio/EClean-modern/blob/modern/docs/Changelog.md)
