# EClean Modern

面向 Paper / Folia 的实体清理插件，基于 [EClean](https://github.com/4o4E/EClean) 开发。支持定时清理、规则保护、清理预览、共享垃圾桶和可视化统计。

[下载](https://github.com/MeowCat-Team/EClean-modern/releases/latest) · [更新日志](docs/Changelog-zh.md) · [English changelog](docs/Changelog.md)

## 安装

1. 使用 Java 25，将插件 JAR 放入服务器的 `plugins/` 目录。
2. 启动服务器生成配置，按需调整`plugins/EClean-Modern/config/normal/config.yml`。
3. 执行 `/eclean reload`，再用 `/eclean clean --preview` 确认清理范围。

垃圾桶内容不跨重启保留。更新插件时请完整停服后替换 JAR。

## 常用命令

主命令为 `/eclean`，别名 `/ecl`；默认需要 OP 权限。

| 命令 | 用途 |
| --- | --- |
| `/eclean clean --preview` | 预览清理范围 |
| `/eclean clean` | 立即执行清理 |
| `/eclean trash` | 打开共享垃圾桶 |
| `/eclean stats gui` | 打开统计菜单 |
| `/eclean config diff` | 查看配置变更 |
| `/eclean reload` | 重载配置和语言 |

## 使用文档

- [命令与菜单](docs/Commands.md)：完整命令、回收操作和清理历史
- [配置说明](docs/Configuration.md)：预设、保护规则、调度和语言
- [权限列表](docs/Permissions.md)
- [PlaceholderAPI 占位符](docs/Placeholders.md)
- [构建与依赖更新](docs/Building.md)
