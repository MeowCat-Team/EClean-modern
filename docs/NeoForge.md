# NeoForge

[Home](../README.md) · English | [简体中文](NeoForge-zh.md)

EClean runs on NeoForge dedicated servers for Minecraft **26.1.2 and 26.2**. Install the NeoForge server for the exact game version, then place the matching EClean and NeoForge build of [Architectury API](https://modrinth.com/mod/architectury-api) in `mods/`. Both require **Java 25**; vanilla clients need no mod.

| Minecraft | Verified NeoForge | Artifact | Architectury API |
| --- | --- | --- | --- |
| 26.1.2 | `26.1.2.114` | `EClean-Modern-0.3.6-neoforge-mc26.1.2.jar` | `20.1.16` |
| 26.2 | `26.2.0.88` | `EClean-Modern-0.3.6-neoforge-mc26.2.jar` | `21.1.11` |

Kotlin and configuration libraries are included in the EClean artifact. Architectury API is an external server dependency. Each EClean artifact declares its exact Minecraft version.

Use the verified loader build above or a newer build within the same Minecraft line. The mod requires at least that loader version and rejects another game version.

## Configuration and commands

Configuration lives under `config/eclean/`; the normal profile is `config/eclean/config/normal/config.yml`. NeoForge and Fabric share their native implementation in `mod-common` and use the same profiles, language migration, reload validation, cleanup rules, statistics, alerts, audit history, and in-memory trash can. See the [configuration guide](Configuration.md) and [command reference](Commands.md).

Use dimension identifiers such as `minecraft:overworld` for world arguments. Vanilla matchers use names such as `ZOMBIE` and `DIAMOND`; modded identifiers retain their namespace, for example `EXAMPLE:CUSTOM_MOB`.

```text
/eclean config validate
/eclean clean all minecraft:overworld --preview
/eclean stats gui minecraft:overworld
/eclean trash
/eclean show
```

`/ecl` is an alias. Commands and menu actions require operator level 2 by default. Statistics and cleanup visit loaded full chunks without loading new chunks. Recovery preserves native item components; natural-despawn recovery follows NeoForge's expiration event and respects custom item lifespans and extensions from other mods. Menus use vanilla chest screens, including private chat search, manual deposits and withdrawals, and scoped density confirmation. Temporary teleports return after 30 seconds. Trash contents and return locations remain in memory and do not survive a server restart.

Search text is captured before public chat formatting and is not broadcast. Permissions use NeoForge's native PermissionAPI and dotted nodes such as `eclean.command.clean.all`; a custom handler's decision is final. See the [permission reference](Permissions.md).

With a cleanup module enabled and `cleanup.cleanWhenNoPlayers: true`, cleanup keeps an empty server ticking. Otherwise the configured empty-server pause remains active; unfinished manual work keeps ticking until completion. Update checks respect `global.updateCheck` and `advanced.update.enabled`. PlaceholderAPI and Bukkit bStats apply to Paper/Folia.

## Building and verification

```shell
./gradlew :common:check :mod-common:check :neoforge:build -PminecraftVersion=26.1.2
./gradlew :common:check :mod-common:check :neoforge:build -PminecraftVersion=26.2
python .github/scripts/mod-smoke.py neoforge 26.1.2
python .github/scripts/mod-smoke.py neoforge 26.2
```

Final artifacts live under `neoforge/build/<minecraftVersion>/libs/`. Use `EClean-Modern-*-neoforge-mc*.jar`. CI and the manual Release workflow use the same real-server suite as Fabric, verify the packaged artifact and API dependencies, and retain logs and JSON reports under `.local/neoforge-smoke/<minecraftVersion>/`. Release publishing creates one NeoForge version per game version with an Architectury API dependency.
