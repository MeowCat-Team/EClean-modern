# Paper compatibility

[Home](../README.md) · English | [简体中文](Paper-zh.md)

EClean Modern 0.3.5 uses one Paper plugin JAR for **Minecraft 1.21.5–26.2**. **Java 25 is required on every listed version**, including older servers that normally use an older Java version. Install the shaded `EClean-Modern-*-paper.jar` in `plugins/` and restart the server.

## Tested builds

The following exact official Paper builds were checked on 2026-10-03. [`paper/compatibility.json`](../paper/compatibility.json) records their build numbers, channels, and SHA-256 checksums and supplies both the CI matrix and release game metadata.

| Minecraft | Paper build | Channel |
| --- | --- | --- |
| 1.21.5 | 114 | Alpha |
| 1.21.6 | 48 | Stable |
| 1.21.7 | 32 | Stable |
| 1.21.8 | 60 | Stable |
| 1.21.9 | 59 | Alpha |
| 1.21.10 | 130 | Stable |
| 1.21.11 | 132 | Stable |
| 26.1.1 | 29 | Alpha |
| 26.1.2 | 74 | Stable |
| 26.2 | 129 | Stable |

The official download service has no Stable build for the three Alpha entries. Those historical builds passed the same plugin checks, but their experimental channel still applies. Prefer a Stable entry for a production server. See Paper's [download service documentation](https://docs.papermc.io/misc/downloads-service/) for channel definitions.

## Why the minimum is 1.21.5

The plugin still compiles against Paper API `26.1.2.build.74-stable`. Runtime compatibility was checked separately against each server's API and by running the packaged plugin.

The old `api-version: '1.21.11'` was an arbitrary loader gate. 0.3.5 sets it to the tested minimum, `1.21.5`. Paper uses this field to reject plugins that declare a newer API than the server supports; it does not prove compatibility with all other versions. See the [plugin descriptor documentation](https://docs.papermc.io/paper/dev/plugin-yml/#api-version).

Paper 1.21.4 build 232 rejects this Java 25 plugin with `Unsupported class file major version 69`, even when only the test copy's `api-version` is lowered. Its plugin remapper uses ASM 9.7.1; Java 25 support arrived in [ASM 9.8](https://asm.ow2.io/versions.html). An independent API audit also found a class/interface mismatch for `InventoryView` on 1.20.6. Lowering the descriptor cannot fix either problem.

## What the checks cover

Every listed build runs the same final plugin JAR without a descriptor override. Checks cover startup and shutdown, configuration and statistics commands, drop and living-entity previews, lore/name/tame protection, manual and natural-expiry item recovery, trash retention across reloads, density limits, and rejection of invalid configuration without changing the active revision. A separate bytecode audit checks 72 referenced API types and 232 member references against each version's native API and Adventure dependencies.

Build CI repeats these server checks for every version. Release repeats them before creating a tag. Run a single case locally after building:

```shell
python .github/scripts/paper-smoke.py 1.21.6 --build 48 --expected-sha256 35e2dfa66b3491b9d2f0bb033679fa5aca1e1fdf097e7a06a80ce8afeda5c214
```

Use `--allow-unstable` for a pinned Alpha entry. Logs and JSON reports are saved under `.local/paper-smoke/<minecraft>/`.

These are Paper server checks without a connected player. Player menu interactions, third-party plugin combinations, and real Folia region-thread execution require separate testing. The JAR retains its Folia adapter, but this matrix establishes the Paper range. Versions outside the manifest, including 26.3 Beta, have no compatibility claim from these checks.
