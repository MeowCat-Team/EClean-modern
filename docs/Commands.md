# Commands and menus

[Home](../README.md) · English | [简体中文](Commands-zh.md)

The main command is `/eclean`, with `/ecl` as an alias. Use `/eclean` if another plugin also uses `/ecl`.

- `/eclean debug` toggles debug messages.
- `/eclean players` lists online players and their coordinates.
- `/eclean clean trash` clears all items from the shared trash can.
- `/eclean reload` reloads configuration and language files and restarts scheduled cleanup timers.
- `/eclean clean` runs cleanup immediately, without a countdown announcement.
- `/eclean clean --preview` previews cleanup without removing entities.
- `/eclean clean all <world> [--preview]` runs or previews all cleanup modules in one world.
- `/eclean clean entity` runs living-entity cleanup without a countdown announcement.
- `/eclean clean entity <world>` runs living-entity cleanup in one world.
- `/eclean clean drop` runs dropped-item cleanup without a countdown announcement.
- `/eclean clean drop <world>` runs dropped-item cleanup in one world.
- `/eclean clean chunk` runs dense-entity cleanup without a countdown announcement.
- `/eclean clean chunk <world>` runs dense-entity cleanup in one world.
- `/eclean entity <type>` shows the distribution of an entity type across chunks in your current world.
- `/eclean entity <type> <world>` shows that distribution in the specified world.
- `/eclean entity <type> <world> <minimum>` only includes chunks with a count strictly greater than the specified minimum.
- `/eclean entity <type> <world> <chunkX> <chunkZ>` lists matching entities in one chunk.
- `/eclean stats` shows entity and loaded-chunk statistics for your current world.
- `/eclean stats <world>` shows statistics for the specified world.
- `/eclean stats gui [world]` opens the statistics menu.
- `/eclean status all` shows entity/chunk statistics for all worlds.
- `/eclean status <world>` shows one world's status.
- `/eclean history [amount]` shows recent cleanup records.
- `/eclean top entity [amount] [world]` ranks entity types by count.
- `/eclean top chunk [amount] [world]` ranks chunks by entity count.
- `/eclean trash [open]` opens the trash can, with category, search, and sorting controls.
- `/eclean trash stats` shows the type, amount, and remaining lifetime of each stored entry.
- `/eclean show` opens the dense-entity statistics menu.
- `/eclean tp <world> <x> <y> <z>` teleports you to the specified coordinates.

## Arguments and cleanup rules

`top` uses fixed positions: type, amount, then world. Specifying a world requires an amount, for example `/eclean top entity 10 123` for the world named `123`. Amounts must be between 1 and 100. Unknown worlds produce an error. `--preview` can appear anywhere after `clean`.

Automatic cleanup and `/eclean clean ...`, including world-specific commands and previews, respect the relevant module's `enabled`, `disabledWorlds`, and `perWorld.worlds.<world>.enabled` settings. Specifying a world does not bypass these rules. Previews count selected entities without changing the last cleanup counts, history, or timers, and without broadcasting completion messages or density alerts.

`cleanup.cleanWhenNoPlayers: false` only restricts automatic cleanup when nobody is online. Administrators can still run manual cleanup.

## Menus and item recovery

Right-clicking in the dense-entity menu first shows a preview with the chunk, entity type, and expected removal/retention counts. Confirm within 30 seconds to proceed. The menu uses the same density rules and world switches as automatic cleanup, retaining protected entities and those within configured limits. Configuration changes require a new preview; newly appearing entities are excluded from an existing preview.

Dropped-item cleanup sends items to the trash can only when both `trashcan.enabled` and `trashcan.collectFromDropCleanup` are enabled. Otherwise, selected drops are deleted directly. Recovery preserves complete stack amounts and metadata; recovery failures leave the source entity intact. Trash-can contents are held in memory and do not survive server restarts.

The first five rows of the trash-can menu display items; the sixth row contains pagination, category, sorting, and search controls. Dragging into the top inventory, hotbar swaps, and double-click collection cannot transfer display items. In your own inventory, left-click deposits one item, right-click deposits half, and Shift+left-click deposits the stack. Search closes the inventory and accepts one English Material ID keyword within 60 seconds. Enter `cancel` to return to the menu; a timeout message indicates that normal chat has resumed. The statistics menu also supports pagination.

Chat statistics are clickable:

- Click an entity type in `/eclean stats` to view its chunk distribution.
- Click a chunk row in `/eclean entity` to list matching entities in that chunk.
- Click entity coordinates to teleport, subject to permission checks.

A trash entry with no timed expiry still exists only for the current server process. Adding items to an existing entry does not extend its expiry.

Cross-world statistics menus require both `eclean.command.stats.gui` and `eclean.command.stats.world`. Entity distribution, chunk details, and teleport actions each check their respective permissions. Normal and menu teleports report the actual asynchronous result. Temporary teleport mode stays enabled for the current menu session; closing and reopening the menu resets it to off.

Folia does not support plugin hot unloading; shut down the server before replacing the plugin. Temporary-return timers start after a successful teleport. If a player disconnects, the plugin attempts the return on their next login during the same server process. Return locations do not survive restarts.

## Cleanup history

History records each actual world/module execution, including source, actor, scope, configuration revision, duration, actual removals, removal failures, skipped chunks, and interrupted execution. Scheduled cleanup, commands, and menu actions share this audit path. Concurrent requests sharing an execution produce one record; previews produce none. Entity cleanup counts entities, while trash-can amounts count individual items. History and cumulative counters reset on restart.

See [Configuration](Configuration.md) for diagnostics and profiles, and [Permissions](Permissions.md) for access control.
