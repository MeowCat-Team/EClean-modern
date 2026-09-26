# Configuration

[Home](../README.md) · English | [简体中文](Configuration-zh.md)

## Profiles

Both profiles support all configuration fields and are independent. Switching profiles replaces the complete rule set. Paths below are relative to `plugins/EClean-Modern/`.

| Profile | Location | Layout |
| --- | --- | --- |
| `normal` (default) | `config/normal/config.yml` | One file; the template focuses on common settings |
| `dev` | `config/dev/` | Separate files for each feature |

## Switching, validation, and reloads

Select `profile: normal` or `profile: dev` in the root `config.yml` and restart, or use the commands below:

- `/eclean config show` displays the active profile and configuration paths.
- `/eclean config validate` checks the selected profile and language files on disk without migrating, creating files, or activating services.
- `/eclean config diff` compares the active configuration with the disk candidate and shows changed sections with old/new values, without reloading.
- `/eclean config effective [world]` displays active rules; a world argument resolves its switches, distances, and interval overrides and shows their sources.
- `/eclean config profile <normal|dev>` switches profiles and reloads immediately.

On the first upgrade from a legacy configuration, the plugin validates the old files, migrates protection rules, world exclusions, and switches into `normal`, and keeps the originals in a permanent `config-backup-<timestamp>/` directory. Missing legacy feature configuration does not automatically enable cleanup. Unrecognized fields must be corrected before loading.

After editing files, run `/eclean reload`. Configuration and language are validated and applied together; a failed reload retains the previous valid configuration. If initial loading fails, automatic/manual cleanup and item recovery remain disabled until you correct the files and reload. Missing existing configuration files produce an error instead of being regenerated with potentially broader cleanup defaults.

## Scheduling and integrations

The `normal` profile also accepts an optional `advanced` section, for example:

```yaml
advanced:
  bStats:
    enabled: false
  papi:
    enabled: true
  update:
    enabled: false
```

These switches, statistics alerts, and scheduler settings take effect on reload. The `dev` profile stores the same settings across multiple files. It does not enable a development/test runtime mode or relax cleanup protection.

Cron expressions use Quartz syntax, such as `0 0 3 * * ?` for 03:00 every day. `null` or an empty string selects interval scheduling. Cron cannot be combined with per-world `intervalSeconds` overrides. Under interval scheduling, each world has its own timer.

Cleanup and statistics scan loaded chunks in batches, by default up to 100 chunks per batch, with a 10-tick delay after each completed batch. Cleanup counts unloaded chunks as skipped. Statistics report collection failure if a chunk unloads or a task fails and retain the last complete cache.

Use `advanced.update.enabled` to control update checks; legacy `global.updateCheck: false` also disables them. The first check runs about 20 seconds after enabling, followed by checks every six hours. Versions use [SemVer](https://semver.org/spec/v2.0.0.html) precedence and accept a `v` prefix. Stable installations only receive notices for newer stable releases; prerelease installations can also receive newer prerelease notices. Failure messages are throttled. Updates are not downloaded or installed automatically.

## Cleanup rules

`drop.mode` and `living.mode` accept `remove-matching` (remove matching candidates) or `keep-matching` (retain matching candidates). Explicit `mode` takes precedence over legacy `blacklistMode`. With an empty matcher list, `remove-matching` removes nothing, while `keep-matching` selects all unprotected candidates. Density cleanup does not remove entities when `entityLimits` is empty.

`chunkDensity.protectTamed` and `chunkDensity.protectAllay` both default to `true`, protecting tamed mobs and allays. These settings belong to the density module and do not override, or inherit from, `living`. Named, leashed, and passenger protections use each module's own `settings`. `alertThreshold` counts each entity type separately within a chunk, including protected entities; cows and sheep are not added together. A limit regex matching multiple types shares its limit across those types. Overlapping rules are processed in configuration order.

`drop.protectWrittenBook` protects both signed books and book-and-quill items containing written pages. Blank book-and-quill items follow the other cleanup rules.

## Language and appearance

Plugin messages and formatted durations use `global.language`: choose `en_us` for English or `zh_cn` for Simplified Chinese. Preserve the original placeholders when editing translations. Missing translations or incompatible placeholders fall back to the bundled text and produce a log message. Entity names use Minecraft translations together with exact IDs; trash-can search uses English Material IDs.

Menu colors replace the default gold (titles), gray (descriptions), and yellow (action hints). They do not modify the original item's metadata.

See [Commands and menus](Commands.md) for recovery and menu controls, and [PlaceholderAPI variables](Placeholders.md) for statistics integration.
