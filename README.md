# EClean Modern

English | [简体中文](README-zh.md)

An entity cleanup plugin for Paper / Folia, based on [EClean](https://github.com/4o4E/EClean). Supports scheduled cleanup, protection rules, cleanup previews, a shared trash can, and visual statistics.

[Download](https://github.com/MeowCat-Team/EClean-modern/releases/latest) · [Changelog](docs/Changelog.md)

## Installation

1. Use Java 25 and place the plugin JAR in your server's `plugins/` directory.
2. Start the server to generate configuration, then adjust `plugins/EClean-Modern/config/normal/config.yml` as needed.
3. Run `/eclean reload`, then `/eclean clean --preview` to check what would be removed.

Trash-can contents do not survive server restarts. Shut down the server completely before replacing the plugin JAR.

## Common commands

The main command is `/eclean`, with `/ecl` as an alias. Commands require OP permissions by default.

| Command | Purpose |
| --- | --- |
| `/eclean clean --preview` | Preview cleanup without removing entities |
| `/eclean clean` | Run cleanup immediately |
| `/eclean trash` | Open the shared trash can |
| `/eclean stats gui` | Open the statistics menu |
| `/eclean config diff` | View configuration changes |
| `/eclean reload` | Reload configuration and language files |

## Documentation

- [Commands and menus](docs/Commands.md): full command reference, item recovery, and cleanup history
- [Configuration](docs/Configuration.md): profiles, protection rules, scheduling, and language
- [Permissions](docs/Permissions.md)
- [PlaceholderAPI variables](docs/Placeholders.md)
- [Building and updating dependencies](docs/Building.md)
