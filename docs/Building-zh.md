# 构建与依赖更新

[返回首页](../README-zh.md) · [English](Building.md) | 简体中文

默认构建 `common` 和 `paper`，需要 JDK 25，生成的插件位于 `paper/build/libs/`。Paper API 固定为 `26.1.2.build.74-stable`；项目提交 Gradle 依赖锁与 SHA-256 校验清单，wrapper 下载使用官方 SHA-256，CI Action 固定提交。校验清单以当前成功构建的依赖为基线，并不等同于漏洞扫描或所有发布方签名验证。

```shell
./gradlew build
```

Windows 使用 `gradlew.bat build`。

更新依赖时先修改版本目录，再显式生成候选锁与校验数据，审查下载来源和校验差异后提交，日常 CI 不自动刷新这些文件：

```shell
./gradlew build --write-locks --write-verification-metadata sha256
```
