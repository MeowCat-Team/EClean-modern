# PlaceholderAPI variables

[Home](../README.md) · English | [简体中文](Placeholders-zh.md)

Install PlaceholderAPI and enable `advanced.papi.enabled` to use these placeholders.

- `%eclean_before_next%`: seconds until the next cleanup.
- `%eclean_before_next_formatted%`: formatted time until the next cleanup.
- `%eclean_last_drop%`: actual entities removed by the most recently completed dropped-item cleanup request; server-wide requests sum all worlds. One dropped stack counts as one entity.
- `%eclean_last_living%`: actual entities removed by the most recently completed living-entity cleanup request; server-wide requests sum all worlds.
- `%eclean_last_chunk%`: actual entities removed by the most recently completed density cleanup request or menu action; server-wide requests sum all worlds.
- `%eclean_trashcan_countdown%`: seconds until the earliest trash entry expires; `0` when empty or when no entry has a timed expiry.
- `%eclean_trashcan_countdown_formatted%`: formatted time until the earliest trash entry expires.
- `%eclean_total_entities%`: server-wide entity count.
- `%eclean_total_chunks%`: server-wide loaded-chunk count.
- `%eclean_world_<world>_entities%`: entity count for the specified world.
- `%eclean_last_clean_time%`: end time of the most recent audited execution, including zero-removal or failed executions.
- `%eclean_last_removal_time%`: time of the most recent actual entity removal; empty if no removal has been recorded.
- `%eclean_total_removed_entities%`: actual entities removed during the current server process, excluding item amounts cleared from the trash can.
- `%eclean_next_clean%`: seconds until the next cleanup.
- `%eclean_next_clean_formatted%`: formatted time until the next cleanup.
- `%eclean_trashcan_entries%`: number of trash-can entries.
- `%eclean_trashcan_total%`: total number of individual items stored in the trash can.
- `%eclean_history_count%`: retained history records, up to 100.

## Counting rules

Entity and chunk counts come from a cache of loaded chunks. A refresh starts every 10 seconds, and results are published after the scan completes. Totals are `0` before the first completed scan. World names preserve their case. Placeholder requests do not trigger scans or load chunks.

History records each actual world/module execution, including source, actor, scope, configuration revision, duration, actual removals, removal failures, skipped chunks, and interrupted execution. Scheduled cleanup, commands, and menu actions share this audit path. Concurrent requests sharing an execution produce one record; previews produce none. Cleanup counts entities; trash-can amounts count individual items. History and cumulative counters reset on restart.

## Time and restarts

Formatted durations use the plugin's configured language. Trash expiry is tracked per entry; merging new items does not extend an existing entry's lifetime. Trash-can contents, history, and cumulative removal counts reset on server restart.
