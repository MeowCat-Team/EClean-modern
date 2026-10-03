# Paper 兼容性

[返回首页](../README-zh.md) · [English](Paper.md) | 简体中文

EClean Modern 0.3.5 使用同一份 Paper 插件 JAR 支持 **Minecraft 1.21.5–26.2**。**清单中所有版本都需要 Java 25**，包括通常使用较低 Java 版本的旧服务端。将带有内嵌依赖的 `EClean-Modern-*-paper.jar` 放入 `plugins/`，然后重启服务器。

## 已测试构建

以下官方 Paper 构建于 2026-10-03 验证。[`paper/compatibility.json`](../paper/compatibility.json) 记录具体构建号、渠道和 SHA-256，同时用于生成 CI 矩阵与发布时的游戏版本元数据。

| Minecraft | Paper 构建 | 渠道 |
| --- | --- | --- |
| 1.21.5 | 114 | Alpha |
| 1.21.6 | 48 | Stable |
| 1.21.7 | 32 | Stable |
| 1.21.8 | 60 | Stable |
| 1.21.9 | 59 | Alpha |
| 1.21.10 | 130 | Stable |
| 1.21.11 | 132 | Stable |
| 26.1.1 | 29 | Alpha |
| 26.1.2 | 74 | Stable |
| 26.2 | 129 | Stable |

三个 Alpha 条目在官方下载服务中没有 Stable 构建。这些历史构建通过了相同的插件检查，但仍属于实验渠道，生产服务器建议选择 Stable 条目。渠道定义见 Paper 的 [下载服务文档](https://docs.papermc.io/misc/downloads-service/)。

## 为什么下界是 1.21.5

插件仍使用 Paper API `26.1.2.build.74-stable` 编译。运行兼容性通过逐版本 API 核对和实际启动最终插件另行确定。

原来的 `api-version: '1.21.11'` 是随意填写的加载门槛。0.3.5 将它改为已验证的下界 `1.21.5`。Paper 会依据这个字段拒绝声明了更高 API 版本的插件，但它不能证明其他版本都能运行，见 [插件描述文件文档](https://docs.papermc.io/paper/dev/plugin-yml/#api-version)。

Paper 1.21.4 build 232 即使只降低测试副本的 `api-version`，也会因 `Unsupported class file major version 69` 拒绝当前 Java 25 插件。该构建的插件重映射器使用 ASM 9.7.1，而 Java 25 支持从 [ASM 9.8](https://asm.ow2.io/versions.html) 开始。独立 API 检查还发现 1.20.6 的 `InventoryView` 存在类与接口不匹配。降低描述文件版本无法解决这些问题。

## 验证覆盖范围

每个条目都使用同一份最终插件 JAR，不覆盖描述文件版本。检查包括启用与停用、配置和统计命令、掉落物与生物清理预览、Lore/命名/驯服保护、手动与自然消失回收、重载后垃圾桶保留、密集实体限制，以及无效配置重载被拒绝且活动配置版本不变。字节码检查还逐版本核对原生 API 与 Adventure 依赖中的 72 个引用类型、232 个成员引用。

Build CI 会对每个版本重复这些服务端检查，Release 也会在创建 tag 前执行。完成构建后可单独验证一个版本：

```shell
python .github/scripts/paper-smoke.py 1.21.6 --build 48 --expected-sha256 35e2dfa66b3491b9d2f0bb033679fa5aca1e1fdf097e7a06a80ce8afeda5c214
```

固定的 Alpha 条目需添加 `--allow-unstable`。日志和 JSON 报告保存于 `.local/paper-smoke/<minecraft>/`。

这些检查在无玩家连接的 Paper 服务端上完成。玩家菜单交互、第三方插件组合和真实 Folia 区域线程运行需要单独测试。插件保留 Folia 适配，但本矩阵确定的是 Paper 范围；清单以外的版本，包括 26.3 Beta，没有本次检查提供的兼容结论。
