# EClean Modern

[English](README.md) | 简体中文

面向 Paper / Folia 的实体清理插件，同时通过 Architectury 支持 Fabric 和 NeoForge 26.1.2 / 26.2 服务端，基于 [EClean](https://github.com/4o4E/EClean) 开发。支持定时清理、规则保护、清理预览、共享垃圾桶和可视化统计。开发主分支为 `architectury`。

[下载](https://github.com/MeowCat-Team/EClean-modern/releases/latest) · [更新日志](docs/Changelog-zh.md) · [English changelog](docs/Changelog.md)

## 安装

1. 使用 **Paper 1.21.5–26.2 和 Java 25**，将插件 JAR 放入服务器的 `plugins/` 目录。具体测试构建及历史 Alpha 构建说明见 [Paper 兼容性文档](docs/Paper-zh.md)。
2. 启动服务器生成配置，按需调整 `plugins/EClean-Modern/config/normal/config.yml`。
3. 执行 `/eclean reload`，再用 `/eclean clean --preview` 确认清理范围。

垃圾桶内容不跨重启保留。更新插件时请完整停服后替换 JAR。

Fabric 或 NeoForge 服务端请将匹配 **26.1.2 或 26.2** 的 EClean 与 Architectury API 放入 `mods/`；Fabric 还需对应的 Fabric API。配置生成于 `config/eclean/`，客户端无需安装模组。具体依赖、权限与安装方法见 [Fabric 使用说明](docs/Fabric-zh.md) 或 [NeoForge 使用说明](docs/NeoForge-zh.md)。

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

- [命令与菜单](docs/Commands-zh.md)：完整命令、回收操作和清理历史
- [配置说明](docs/Configuration-zh.md)：预设、保护规则、调度和语言
- [权限列表](docs/Permissions-zh.md)
- [Paper 版本与兼容性验证](docs/Paper-zh.md)
- [Fabric 安装与兼容性](docs/Fabric-zh.md)
- [NeoForge 安装与兼容性](docs/NeoForge-zh.md)
- [PlaceholderAPI 占位符](docs/Placeholders-zh.md)
- [构建与依赖更新](docs/Building-zh.md)
