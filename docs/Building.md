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

## Releases

The `Release` workflow builds and tests the `modern` branch, reads the version from `build.gradle.kts`, and extracts its release notes from `docs/Changelog.md`. It creates the GitHub Release, then publishes the same Paper/Folia JAR and release notes to [EClean-Modern on Modrinth](https://modrinth.com/plugin/ecl-modern).

Configure the repository Actions secret `MODRINTH_TOKEN` with a Modrinth token that can create versions for project `VW7EmMIj` (`ecl-modern`). The workflow checks that the secret is present before creating a tag. The Minecraft version follows the pinned Paper API in `gradle/libs.versions.toml`; loader metadata lists Paper and Folia. If Modrinth publishing fails after the GitHub Release succeeds, use **Re-run failed jobs** to retry `publish-modrinth` without creating another tag or rebuilding.
