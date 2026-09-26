# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

### Added

- **Configuration profiles and diagnostics**: independent `normal` and `dev` profiles, `/eclean config profile <normal|dev>`, read-only `config validate` and `config diff`, and `config effective [world]` to show active defaults and world overrides with their sources.
- **Explicit matching modes**: `drop.mode` and `living.mode` accept `remove-matching` or `keep-matching`. Density cleanup has independent `protectTamed` and `protectAllay` options, both enabled by default.
- **Scoped commands**: `/eclean clean all <world> [--preview]`, `--preview` anywhere after `clean`, and `/eclean trash open`.
- **Cleanup audit details**: source, actor, world/scope, configuration revision, duration, actual removals, failed removals, skipped chunks, and interrupted execution. Concurrent requests sharing one execution produce one record; previews produce none.
- **PlaceholderAPI counters**: `%eclean_total_removed_entities%` and `%eclean_last_removal_time%` expose process-lifetime actual entity removals and the latest removal time.

### Fixed

- **Cleanup boundaries**: automatic cleanup, manual commands, previews, and density-menu cleanup respect module switches, disabled worlds, per-world switches, and applicable protection rules. Previews no longer update history, counts, or timers, recover items, or broadcast cleanup results.
- **Item recovery**: preserve stack amounts, custom names, lore, enchantments, and persistent metadata. Failed recovery retains the source; failed source removal rolls back the pending store transfer. Genuine lore is no longer stripped by matching translated display text.
- **Menu interactions**: block unsafe drag/click transfers and invalid slot mappings; handle pagination, expiry, and custom stack limits. Search sessions survive inventory closure, support cancellation and timeout, and keep search input out of public chat.
- **Density-menu deletion**: show a scoped preview and require confirmation within 30 seconds. Execution rechecks configuration and protections and only considers entities present in the preview; repeated confirmation cannot submit twice.
- **Permissions and teleport results**: preserve explicit permission denials, recheck menu access, and require world-statistics permission for cross-world statistics menus. Teleports report the actual asynchronous outcome, reject non-finite/out-of-range coordinates, and start temporary-return timers only after success.
- **Scheduling and statistics**: access chunks on their owning regions, avoid loading unloaded chunks, scan in bounded batches, and complete pending operations on cancellation or scheduling failure. Failed statistics scans no longer publish partial caches; empty loaded chunks are included.
- **Configuration reloads**: reject malformed or unknown fields, invalid ranges, regexes, and Cron expressions; retain the previous configuration on reload failure. Validate legacy configuration before migration and preserve backups. Configuration, profile, and language are published together, and configured services are reapplied on reload.
- **Messages and translations**: escape ordinary message arguments, avoid recursive placeholder substitution, and construct click actions as components. Missing translations or incompatible custom placeholders fall back per key. Localize durations, display Minecraft entity names with exact IDs, and correct ambiguous help/menu text.

### Changed

- **Command syntax**: `top` now uses fixed positions: `/eclean top <entity|chunk> [amount] [world]`. Specifying a world requires an amount from 1 to 100; `/eclean top entity 10 123` queries the world named `123`. Unknown worlds report an error.
- **Cleanup counters**: `last_drop`, `last_living`, and `last_chunk` describe the most recently completed corresponding cleanup request; server-wide requests sum their world results. One dropped stack counts as one entity. Trash-can totals and trash-clear audit counts use item amounts.
- **History semantics**: keep the latest 100 records. `history_count` is the retained record count, `last_clean_time` is the latest recorded execution end, including zero-removal or failed executions, and `last_removal_time` only advances after actual entity removal.
- **Update checks**: use SemVer precedence, ignore draft releases, keep stable installations on stable releases, deduplicate update notices, and throttle failure messages. `advanced.update.enabled` is the recommended switch; legacy `global.updateCheck: false` still disables checking.
- **Internal structure**: separate platform-independent code into `common` and the server implementation into `paper`, share command/permission definitions, inject adapter dependencies explicitly, and remove superseded cleanup planners and helpers.
- **Build reproducibility**: require JDK 25, pin Paper API to `26.1.2.build.74-stable`, commit Gradle dependency locks and SHA-256 verification metadata, verify the wrapper download, and pin CI Actions to commits.

### Upgrade notes

- The root `config.yml` must explicitly select `profile: normal` or `profile: dev`. Profiles do not inherit from each other; switching profiles replaces the complete rule set. Back up existing configuration and review `config validate`, `config diff`, and `config effective` before applying changes.
- Explicit `mode` takes precedence over legacy `blacklistMode`. An empty matcher list removes nothing with `remove-matching` and selects all unprotected candidates with `keep-matching`. Density cleanup now protects tamed mobs and allays by default.
- Cross-world statistics menus require both `eclean.command.stats.gui` and `eclean.command.stats.world`. Configuration diagnostics and profile switching require `eclean.command.config`.
- Trash-can contents, cleanup history/cumulative counters, and temporary-return locations remain in memory and do not survive restart. Adding items to an existing trash entry does not extend its expiry.
- Replace the plugin with a full server shutdown; Folia hot unloading is unsupported.

## 0.2.9

### Added
- **i18n language system**: flat-key language files (`lang/zh_cn.yml`, `lang/en_us.yml`), auto-merge missing keys, configurable language in `config.yml`.
- **Cron cleanup scheduling**: `cleanup.yml` supports `cron` expressions while keeping `intervalSeconds`.
- **Cleanup rules refinement**: distance-to-player filtering, per-entity-type rules, and per-world distance overrides.
- **Trash can enhancements**: category filter, search (via chat), and sort options (count/name/time).
- **More PlaceholderAPI variables**: last clean time, next clean time, trash-can entries/total, cleanup history count.
- **Debug rate limiting**: repeated debug messages are merged within a configurable cooldown.

## 0.2.8

### Changed
- **bStats**: switched to the registered plugin ID so usage data is recorded on the project's own bStats dashboard.

## 0.2.7

### Added
- **Server-wide status**: `/eclean status all` shows entity/chunk statistics for every world.
- **Single-world status**: `/eclean status <world>` shows one world's status.
- **Cleanup history**: `/eclean history` shows recent cleanup records.
- **Top lists**: `/eclean top entity` and `/eclean top chunk` show the busiest entity types/chunks.
- **Stats GUI**: `/eclean stats gui [world]` opens a clickable statistics menu.
- **New PlaceholderAPI variables**: total entities, total chunks, and per-world entity counts.
- **Entity threshold alerts**: optional periodic alerts when a world/entity type exceeds a configured count.

### Changed
- Updated dependencies and removed unused Maven repositories.

## 0.2.6

### Added
- **Stats detail view**: entity types in `/eclean stats` are now clickable to show per-chunk distribution.
- **Chunk entity list**: chunk rows in `/eclean entity` are clickable to list the entities in that chunk.
- **Click-to-teleport**: entity coordinates in chat are clickable and teleport the admin directly to that position.

### Fixed
- **Fixed number-key exploit in trash can**: unhandled clicks such as hotbar number keys can no longer take the display item without deducting from the store.
- **Fixed trash-can lore leaking onto taken items**: plugin-generated lore is cleaned when items are deposited.
- **Fixed expired-entry window**: adding to or taking from an expired entry is no longer possible.
- **Fixed wrong-entry deduction when stacking is disabled**: taking now deducts from the exact clicked entry.
- **Fixed menu listener leaks and Folia thread-safety issues** in `MenuManager`.
- **Fixed remaining-time lore not refreshing** while the trash-can menu is open.
- **Fixed pager staying on an empty page** after entries shrink.

## 0.2.5

### Fixed
- **Fixed trash-can item duplication**: items are now deducted from the store before being given to the player, so stale menus, concurrent players, or expired entries can no longer create extra items.
- **Fixed other open trash-can menus showing stale counts**: all open menus are refreshed after taking items.
- Added tests to ensure taking never removes more than available and display lore never leaks into the stored/taken item.

## 0.2.4

### Changed
- **Removed the global `RuntimeServices` singleton**: services are now a plain class owned by the plugin instance and accessed through `EClean.services` / `PL.services`.
- All command, cleanup, config, listener, menu, and PAPI code now use the plugin-owned services container.

## 0.2.3

### Changed
- **Unified menu lifecycle**: all open menus are now tracked by `MenuManager` and cleaned up on close, quit, and plugin disable.
- Reused the update-check `HttpClient` instead of creating a new one each check.
- Dense-chunk alerts now parse each message once before sending to all admins.
- Trash-can item storage reads config values outside the write lock.
- Reused a single MiniMessage regex for stripping tags.
- Removed leftover dead legacy tests.

## 0.2.2

### Fixed
- **Chunk cleanup preview now matches real cleanup**: the dry-run count no longer shows the total number of dense entities; it shows how many would actually be removed.
- **Stats/entity/players commands now require the admin permission**, matching the plugin's permission design.
- **Async callbacks are consistent for empty worlds/chunks**: chunk scanning now calls back on the global scheduler just like drop and living cleanup.

### Changed
- Optimized dense-chunk checks by grouping entities by type before applying limits.
- Avoided unnecessary menu refresh scheduling when no trash-can menu is open.
- Menus only rebuild item stacks when the item actually needs updating.
- Reused a single MiniMessage instance instead of creating it repeatedly.

## 0.2.1

### Changed
- **Big internal cleanup and refactor** — no player-facing behavior changed.
- Removed dead code and duplicate classes.
- Split the command handling into smaller files so it is easier to maintain.
- Unified repeated logic across drop/living cleanup, world planning, pager buttons, cleanup announcements, and scheduler gateways.
- Moved hardcoded command messages into `lang.yml`.
- Updated the Gradle wrapper.
- Cleaned up disabled/dead tests.

## 0.2.0

### Added
- **Trash can item stacking**: identical items are merged into one slot automatically. One slot shows one item type with its total count (e.g., Dirt × 2345). No capacity limit — you can store as many as you want.
- **Per-item countdown**: each item type has its own lifetime and disappears on its own when it expires, without affecting other items. The remaining time is shown right in the item description.
- **New stats command**: `/eclean trash stats` (admin only) shows every item type in the trash can with its count and remaining time.
- **New config options**: `trashcan.yml` gains three commented options — stacking on/off, sorting, and showing the remaining time.

### Changed
- The trash can is no longer limited to 54 slots × 64 items each — capacity is unlimited.
- The whole bin is no longer cleared on a fixed timer; each item type now expires on its own.
- The item description now shows the total count (can exceed 64) and the remaining time.

### Removed
- Removed the capacity limit setting (capacity is unlimited now; the old setting in existing config files is ignored automatically).
- Removed the server-wide "bin will be cleared soon" reminder messages.

## 0.1.9

### Fixed
- **Fixed several problems with the automatic language file upgrade**: quoted text, reset marks, and multi-line text could be converted wrongly when switching from old color codes to the new text format — all handled correctly now.
- **Fixed the upgrade continuing after a backup failure**: the upgrade now stops if the old file cannot be backed up, so the original file is never lost.
- **Fixed the plugin failing to start when the language file is damaged**: it now falls back to the built-in default file automatically.
- Removed duplicate code.

## 0.1.8

### Fixed
- **Fixed identical items not merging** in the trash can — each one took its own slot.
- **Fixed items taken from the trash can carrying leftover information** (strange description lines).
- **Fixed item description text showing format tags literally** instead of formatting.
- **Fixed possible item loss** when the server handles multiple regions at the same time.
- **Fixed the countdown staying visible after the feature is disabled**.
- **Fixed memory usage growing** when opening and closing the trash can repeatedly.
- **Fixed the inventory display getting out of sync** when putting items in from the player's backpack.
- **Fixed duplicate reminder messages** with multiple worlds — countdown and cleanup announcements are no longer sent several times.
- **Fixed commands not working on some servers**.
- **Fixed color codes not showing**: old color codes displayed as garbage text on new servers; everything now uses the new text format.
- **Fixed the language file being read incompletely or coming up empty**.

### Changed
- **Rewrote the plugin's base structure** — no longer relies on the old plugin framework.
- **Rewrote the trash can feature**: the menu is now chest-style, and items stay in the bin after closing and reopening the menu.
- **Default cleanup interval changed from 6000 s (100 min) to 600 s (10 min)**.
- **The "bin cleared" notice is now sent to admins only**.
- **Faster config reload**: only the changed parts are reloaded.

### Added
- **Capacity limit setting** for the trash can: configure how many slots it holds; new items are discarded when full (removed in a later version).
- Added automated tests to keep the feature stable.

## 0.1.7

### Changed
- **Rewrote the menu framework** — no longer relies on the old plugin framework.
- **Rewrote the countdown variable display**.
- **Rewrote language file loading**.
- Greatly reduced dependency on the old plugin framework.

## 0.1.6

### Added
- **Automatic text color upgrade**: old color codes are converted to the new text format on first startup.
- **Preview mode**: `/ecl clean --preview` shows what would be cleaned without removing anything.
- **Per-world settings**: `per-world.yml` lets you set a separate cleanup interval for each world.

### Changed
- **Each world counts down separately**.
- Messages now use the new text format.
- Removed old color-handling code.

## 0.1.5

### Changed
- **Rewrote the command system** — no longer relies on the old plugin framework.
- **Rewrote server task scheduling** for compatibility with new server versions.

## 0.1.4

### Added
- Added a one-command way to start a local server for testing.

### Changed
- The debug command, update check, and event handling no longer rely on the old plugin framework.
- Files generated by local testing are no longer tracked in version control.

### Fixed
- **Fixed an error when counting chunks on new-architecture servers**.
- **Fixed an error when loading chunks on new-architecture servers**.
- **Fixed garbled console logs**: logs now go through the plugin logger; Chinese text was replaced with English to avoid garbled output.
- Announcements sent to players are no longer duplicated in the console.

## 0.1.3

### Added
- **Support for new-architecture servers (Folia)**.
- **Rewrote the config system**: default settings split into multiple files, every option commented.
- **New world statistics feature**.
- Added automated tests.

### Changed
- **Renamed the project to EClean-Modern** and upgraded to the new server interface.
- **Major internal rewrite**: cleanup logic split into parts, task scheduling rewritten, statistics collected chunk by chunk to fit the new server architecture.
- Updated documentation.
