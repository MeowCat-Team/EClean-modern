# Building and updating dependencies

[Home](../README.md) · English | [简体中文](Building-zh.md)

The default build includes `common` and `paper` and requires JDK 25. Plugin artifacts are written to `paper/build/libs/`. Paper API is pinned to `26.1.2.build.74-stable`. The repository includes Gradle dependency locks and SHA-256 verification metadata, verifies the wrapper download against its official checksum, and pins CI Actions to commits. The verification metadata records the dependencies used for the build; it is not a vulnerability scan or verification of every publisher's signature.

```shell
./gradlew build
```

On Windows, use `gradlew.bat build`.

To update dependencies, first edit the version catalog, then explicitly generate candidate locks and verification metadata. Review artifact sources and checksum changes before committing them. Normal CI builds do not regenerate these files.

```shell
./gradlew build --write-locks --write-verification-metadata sha256
```
