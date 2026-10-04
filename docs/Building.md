# Building and updating dependencies

[Home](../README.md) · English | [简体中文](Building-zh.md)

The `architectury` branch contains five modules: `common` provides platform-independent rules and services; `paper` adapts them to Paper/Folia; `mod-common` contains the shared native Minecraft runtime; `fabric` and `neoforge` provide loader entrypoints and transformed artifacts. The default build targets Minecraft 26.1.2 for both mod loaders and requires JDK 25. Paper artifacts live under `paper/build/libs/`; mod artifacts under `<loader>/build/<minecraftVersion>/libs/`. Paper API remains pinned to `26.1.2.build.74-stable`.

The repository includes Gradle dependency locks and SHA-256 verification metadata, verifies the wrapper download against its official checksum, and pins CI Actions to commits. The verification metadata records the dependencies used for the build; it is not a vulnerability scan or verification of every publisher's signature.

```shell
./gradlew build
```

On Windows, use `gradlew.bat build`.

Both mod loaders also support 26.2 from the same source. Build it with `./gradlew :mod-common:check :fabric:build :neoforge:build -PminecraftVersion=26.2`. Outputs and dependency locks are separate for each loader and game version; Build CI verifies the four combinations. See the [Fabric guide](Fabric.md) and [NeoForge guide](NeoForge.md) for installation and runtime behavior.

The pinned Paper compile API does not determine runtime compatibility. The exact tested builds and checksums are recorded in [`paper/compatibility.json`](../paper/compatibility.json); see the [Paper guide](Paper.md) for the 1.21.5–26.2 range and Java 25 requirement. Build CI runs the packaged plugin on every listed Paper version in addition to its unit tests.

Architectury transforms `mod-common` for the selected loader. Each final mod contains the domain code, transformed native implementation, common resources, and shaded Kotlin/configuration libraries. Architectury API remains an external dependency on both loaders; Fabric API is additionally required on Fabric. The production smoke verifies the actual packaged mod and matching runtime dependencies.

To update dependencies, first edit the version catalog, then explicitly generate candidate locks and verification metadata. Review artifact sources and checksum changes before committing them. Normal CI builds do not regenerate these files.

```shell
./gradlew build --write-locks --write-verification-metadata sha256
./gradlew :mod-common:check :fabric:build :neoforge:build -PminecraftVersion=26.2 --write-locks --write-verification-metadata sha256
```

## Releases

The manually dispatched `Release` workflow builds and tests the `architectury` branch. Before creating a tag, it runs the packaged Paper plugin on every version in the compatibility manifest and the four Fabric/NeoForge artifacts on real servers. It reads the version from `build.gradle.kts` and extracts release notes from `docs/Changelog.md`. The GitHub Release contains one Paper/Folia JAR, two Fabric JARs, and two NeoForge JARs. Separate jobs publish those same artifacts and notes to [EClean-Modern on Modrinth](https://modrinth.com/plugin/ecl-modern).

Configure the repository Actions secret `MODRINTH_TOKEN` with a Modrinth token that can create versions for project `VW7EmMIj` (`ecl-modern`). The workflow checks that the secret is present before creating a tag. Paper/Folia game metadata uses the full tested list from the compatibility manifest, with historical Alpha builds explicitly enabled for tests. Mod versions use their exact game and loader metadata and require Architectury API; Fabric also requires Fabric API.

Modrinth version numbers and display names identify the platform and supported games:

| Artifact | Version number | Display name |
| --- | --- | --- |
| Paper/Folia | `<version>+paper.<minimum>-<maximum>` | `<version> (Paper / Folia, MC <minimum>-<maximum>)` |
| Fabric | `<version>+fabric.<minecraft>` | `<version> (Fabric, MC <minecraft>)` |
| NeoForge | `<version>+neoforge.<minecraft>` | `<version> (NeoForge, MC <minecraft>)` |

For 0.3.6, the Paper number is `0.3.6+paper.1.21.5-26.2` and its display name is `0.3.6 (Paper / Folia, MC 1.21.5-26.2)`. A NeoForge example is `0.3.6+neoforge.26.2` / `0.3.6 (NeoForge, MC 26.2)`. The workflow sets `modrinth-version` and `modrinth-name` explicitly; Modrinth may show the version number in lists and selectors. If Modrinth publishing fails after the GitHub Release succeeds, use **Re-run failed jobs** to retry the affected publishing job without creating another tag or rebuilding.

For an already published release, run the **Modrinth version names** workflow with its base version (for example, `0.3.4`). It updates only that release's existing names and numbers using its declared game versions: a single version stays exact, while multiple Paper versions use a range. It preserves files and game metadata. To preview the changes locally, run `python .github/scripts/modrinth-names.py --base-version 0.3.4 --check`.
