# Building and updating dependencies

[Home](../README.md) · English | [简体中文](Building-zh.md)

The default build includes `common`, `paper`, and Fabric for Minecraft 26.1.2 and requires JDK 25. Paper artifacts are written to `paper/build/libs/`; Fabric artifacts to `fabric/build/26.1.2/libs/`. Paper API is pinned to `26.1.2.build.74-stable`. The repository includes Gradle dependency locks and SHA-256 verification metadata, verifies the wrapper download against its official checksum, and pins CI Actions to commits. The verification metadata records the dependencies used for the build; it is not a vulnerability scan or verification of every publisher's signature.

```shell
./gradlew build
```

On Windows, use `gradlew.bat build`.

Fabric also supports 26.2 from the same source. Build it with `./gradlew :fabric:build -PminecraftVersion=26.2`; outputs and dependency locks are separate for each game version. CI tests both. See the [Fabric guide](Fabric.md) for installation and runtime behavior.

The pinned Paper compile API does not determine runtime compatibility. The exact tested builds and checksums are recorded in [`paper/compatibility.json`](../paper/compatibility.json); see the [Paper guide](Paper.md) for the 1.21.5–26.2 range and Java 25 requirement. Build CI runs the packaged plugin on every listed Paper version in addition to its unit tests.

Fabric compiles against Mojang's public API with optional Loom access and interface transforms disabled. The build canonicalizes the local server compile JAR's ZIP entry order and timestamps while retaining every class/resource byte, then verifies the complete JAR against its pinned SHA-256. This keeps fresh builds reproducible without an exception to dependency verification.

To update dependencies, first edit the version catalog, then explicitly generate candidate locks and verification metadata. Review artifact sources and checksum changes before committing them. Normal CI builds do not regenerate these files.

```shell
./gradlew build --write-locks --write-verification-metadata sha256
./gradlew :fabric:build -PminecraftVersion=26.2 --write-locks --write-verification-metadata sha256
```

## Releases

The `Release` workflow builds and tests the `modern` branch, including both Fabric targets, and runs the packaged Paper plugin on every version in the compatibility manifest and each Fabric mod on a real server before creating a tag. It reads the version from `build.gradle.kts` and extracts release notes from `docs/Changelog.md`. The GitHub Release contains the Paper/Folia JAR and both Fabric JARs. Separate jobs publish those same artifacts and notes to [EClean-Modern on Modrinth](https://modrinth.com/plugin/ecl-modern).

Configure the repository Actions secret `MODRINTH_TOKEN` with a Modrinth token that can create versions for project `VW7EmMIj` (`ecl-modern`). The workflow checks that the secret is present before creating a tag. Paper/Folia game metadata uses the full tested list from the compatibility manifest, with historical Alpha builds explicitly enabled for tests. Fabric uses exact game metadata and a required Fabric API dependency.

Modrinth version numbers and display names use the same format for both platforms:

| Artifact | Version number | Display name |
| --- | --- | --- |
| Paper/Folia | `<version>+paper.<minimum>-<maximum>` | `<version> (Paper / Folia, MC <minimum>-<maximum>)` |
| Fabric | `<version>+fabric.<minecraft>` | `<version> (Fabric, MC <minecraft>)` |

For 0.3.5, the Paper number is `0.3.5+paper.1.21.5-26.2` and its display name is `0.3.5 (Paper / Folia, MC 1.21.5-26.2)`. The workflow sets `modrinth-version` and `modrinth-name` explicitly; Modrinth may show the version number in lists and selectors. If Modrinth publishing fails after the GitHub Release succeeds, use **Re-run failed jobs** to retry the affected publishing job without creating another tag or rebuilding.

For an already published release, run the **Modrinth version names** workflow with its base version (for example, `0.3.4`). It updates only that release's existing names and numbers using its declared game versions: a single version stays exact, while multiple Paper versions use a range. It preserves files and game metadata. To preview the changes locally, run `python .github/scripts/modrinth-names.py --base-version 0.3.4 --check`.
