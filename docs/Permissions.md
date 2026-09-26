# Permissions

[Home](../README.md) · English | [简体中文](Permissions-zh.md)

Commands and actions have separate permissions, defaulting to `op`. The legacy `eclean.admin` permission grants all capabilities listed below. `eclean.trash` remains an alias for opening the shared trash can.

Commands, menus, and tab completion use the same permission rules. An explicit denial of a capability, including its parent or compatibility alias, takes precedence over grants from other aliases, parents, or `eclean.admin`. The default lack of permission for a non-OP player does not prevent granting an individual leaf permission.

- `eclean.admin`: all EClean capabilities (legacy aggregate permission).
- `eclean.trash`: open the shared trash can; equivalent to `eclean.command.trash.open`.
- `eclean.command.debug`: toggle debug messages.
- `eclean.command.reload`: reload configuration and language files.
- `eclean.command.config`: inspect, validate, compare, and switch configuration profiles.
- `eclean.command.clean.all`: run all cleanup modules.
- `eclean.command.clean.entity`: clean living entities.
- `eclean.command.clean.drop`: clean dropped items.
- `eclean.command.clean.chunk`: clean dense entities.
- `eclean.command.clean.preview`: use `--preview`.
- `eclean.command.clean.trash`: clear the shared trash can.
- `eclean.command.trash.open`: open the shared trash can.
- `eclean.command.trash.stats`: view trash-can statistics.
- `eclean.command.stats.self`: view statistics for the current world.
- `eclean.command.stats.world`: view statistics for a specified world.
- `eclean.command.stats.gui`: open the statistics menu.
- `eclean.command.status.world`: view one world's status.
- `eclean.command.status.all`: view server-wide status.
- `eclean.command.entity.self`: view entity distribution in the current world.
- `eclean.command.entity.world`: view entity distribution in a specified world.
- `eclean.command.entity.chunk`: view entity details for a specified chunk.
- `eclean.command.players`: view online players and their coordinates.
- `eclean.command.show`: open the dense-entity menu.
- `eclean.command.show.teleport`: teleport from the dense-entity menu.
- `eclean.command.show.clean`: remove entities through the dense-entity menu.
- `eclean.command.history`: view cleanup history.
- `eclean.command.top.entity`: view entity-type rankings.
- `eclean.command.top.chunk`: view chunk rankings.
- `eclean.command.teleport`: use `/eclean tp`.
- `eclean.alerts`: receive automatic plugin alerts.

Cross-world statistics menus require both `eclean.command.stats.gui` and `eclean.command.stats.world`. Entity distribution, chunk details, and teleport actions inside menus also check their respective permissions.
