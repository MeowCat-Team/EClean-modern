# 配置说明

[返回首页](../README.md)

## 配置预设

两套预设均支持完整配置字段，彼此不继承。切换 profile 会替换整套清理规则。下列路径相对于 `plugins/EClean-Modern/`。

| 预设 | 配置位置 | 组织方式 |
| --- | --- | --- |
| `normal`（默认） | `config/normal/config.yml` | 单文件，模板以常用项为主 |
| `dev` | `config/dev/` | 按功能拆分多个文件 |

## 切换、校验与重载

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

## 调度与服务

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

清理和统计按区块分批扫描，默认每批最多 100 个区块，上一批完成后等待 10 tick，再提交下一批。清理会跳过已卸载区块并在结果中计数；统计遇到区块卸载或任务失败会报告采集失败，保留已有完整缓存。

更新检查统一推荐使用 `advanced.update.enabled`；旧 `global.updateCheck: false` 仍会禁用检查。首次检查在启动/启用后约 20 秒，之后每 6 小时检查。版本按 [SemVer](https://semver.org/spec/v2.0.0.html) 比较，允许 `v` 前缀；正式版只提示更高的正式版本，预发布安装可提示更高的预发布版本。检查失败会节流提示，不自动下载或执行更新。

## 清理规则

`drop.mode` 和 `living.mode` 使用 `remove-matching`（只清匹配项）或 `keep-matching`（保留匹配项）。旧 `blacklistMode` 仍兼容，显式 `mode` 优先。空匹配列表配合 `remove-matching` 不清任何对象，配合 `keep-matching` 会选择所有未受保护对象。密度模块没有 `entityLimits` 时不会删除实体。

`chunkDensity.protectTamed`、`chunkDensity.protectAllay` 默认均为 `true`，分别保护驯服生物和悦灵。这是密度模块自己的保护设置，和 `living` 不互相覆盖。命名、拴绳、骑乘保护仍由各自模块的 `settings` 控制。`alertThreshold` 按区块内的单一实体类型计数（包含受保护对象），不会将牛和羊相加；一个限额正则匹配多类实体时共享该限额，重叠规则按配置顺序处理。

`drop.protectWrittenBook` 同时保护已签名的成书和已有内容的书与笔，空白书与笔仍按照其他清理规则处理。

## 语言与界面

插件提示和格式化时间使用 `global.language` 指定的语言。修改翻译时请保留原有占位符；缺失的翻译或占位符不匹配的条目会使用内置文本，并记录提示。实体名称展示 Minecraft 翻译和精确 ID；垃圾桶搜索使用英文 Material ID。

菜单颜色分别替换默认菜单中的 gold（标题）、gray（说明）、yellow（操作提示），不会修改物品本身的元数据。

回收和菜单操作见[命令与菜单](Commands.md)，统计占位符见[PlaceholderAPI](Placeholders.md)。
