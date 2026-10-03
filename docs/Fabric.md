# Fabric

[Home](../README.md) · English | [简体中文](Fabric-zh.md)

EClean runs on Fabric dedicated servers for Minecraft **26.1.2 and 26.2**. Each game version has its own artifact with an exact Minecraft dependency; install only the matching one.

| Minecraft | Artifact | Minimum Fabric API |
| --- | --- | --- |
| 26.1.2 | `EClean-Modern-0.3.4-fabric-mc26.1.2.jar` | `0.155.3+26.1.2` |
| 26.2 | `EClean-Modern-0.3.4-fabric-mc26.2.jar` | `0.161.0+26.2` |

Both require Java 25 and Fabric Loader 0.19.5 or newer. Install EClean and the matching Fabric API in the server's `mods/` directory, then start the server. Kotlin and configuration libraries are included; Fabric Language Kotlin is unnecessary. Clients can use vanilla Minecraft: menus use the vanilla chest protocol.

## Configuration and commands

Configuration and language files live under `config/eclean/`. The active normal configuration is `config/eclean/config/normal/config.yml`; the dev profile uses `config/eclean/config/dev/`. The profiles, language migration, transactional reloads, cleanup rules, previews, statistics, alerts, audit history, and shared trash can use the same common implementation as Paper.

Use dimension identifiers for world names: `minecraft:overworld`, `minecraft:the_nether`, `minecraft:the_end`, or a mod's dimension identifier. Vanilla entity/item matchers retain uppercase names such as `ZOMBIE` and `DIAMOND`. Modded types include their namespace, for example `EXAMPLE:CUSTOM_MOB`.

```text
/eclean config validate
/eclean config effective minecraft:overworld
/eclean clean all minecraft:overworld --preview
/eclean stats gui minecraft:overworld
/eclean trash
/eclean show
```

`/ecl` is an alias. Statistics and cleanup inspect only already loaded full chunks and never create chunk tickets to scan. Item recovery preserves native item data components. Natural despawn recovery hooks the vanilla age expiry; pickup, damage, and chunk unloading do not enter the trash can. Trash contents remain in memory and are lost when the server stops.

With a cleanup module enabled and `cleanup.cleanWhenNoPlayers: true`, the mod keeps an empty server ticking so scheduled cleanup continues. Otherwise the server can pause according to `pause-when-empty-seconds`; unfinished manual work keeps ticking until it completes. Vanilla `/tick freeze` remains under the server's control.

The menus include category/sort/search controls, manual deposits, withdrawals, statistics, dense entity inspection, and rule previews before a 30-second cleanup confirmation. Temporary dense-menu teleports return after 30 seconds, retain orientation, and recover the return location after a reconnect in the same server session. Search input is private and expires after 60 seconds; enter `cancel` to exit.

## Permissions and integrations

Without a permission provider, commands and actions require vanilla operator level 2. EClean uses Fabric API's native permission context. Providers use resource identifiers: `eclean.command.clean.all` becomes **`eclean:command.clean.all`**, and `eclean.admin` becomes **`eclean:admin`**. The [permission reference](Permissions.md) lists the capabilities and compatibility aliases; all use the same conversion. Explicit denials take precedence over aliases, parent grants, and administrator grants. Grants can give a non-operator an individual capability.

Update checks run asynchronously and respect `global.updateCheck` and `advanced.update.enabled`. PlaceholderAPI and Bukkit bStats integration apply to Paper/Folia; Fabric does not register either. Their shared configuration fields remain readable when moving configurations between platforms.

## Building

```shell
./gradlew :common:check :fabric:build -PminecraftVersion=26.1.2
./gradlew :common:check :fabric:build -PminecraftVersion=26.2
```

Run `python .github/scripts/fabric-smoke.py 26.1.2` or `26.2` to exercise the packaged mod on a real server. The test binds to localhost, creates a dedicated world and configuration under `.local/fabric-smoke/`, and verifies protection rules, previews, item recovery, reloads, and empty-server ticks. CI and Release both run it and retain the logs as build artifacts.

Outputs live in `fabric/build/<minecraftVersion>/libs/`. Use the final `EClean-Modern-*-fabric-mc*.jar`, which includes the common resources and shaded libraries. The `dev` JAR is for development. Release publishing creates separate Fabric Modrinth versions with exact game metadata and a required Fabric API dependency.
