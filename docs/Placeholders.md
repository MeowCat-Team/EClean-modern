# PlaceholderAPI 占位符

[返回首页](../README.md)

- `%eclean_before_next%` - `距离下一次清理的时间, 单位秒`
- `%eclean_before_next_formatted%` - `距离下一次清理的时间, 格式化的时间`
- `%eclean_last_drop%` - `最近完成的掉落物清理请求实际删除的实体数，全服请求为各世界之和（一组物品算一个实体）`
- `%eclean_last_living%` - `最近完成的生物清理请求实际删除的实体数，全服请求为各世界之和`
- `%eclean_last_chunk%` - `最近完成的密度清理请求或 GUI 实际删除的实体数，全服请求为各世界之和`
- `%eclean_trashcan_countdown%` - `最早到期条目的剩余秒数，空桶或全部条目无定时过期时为 0`
- `%eclean_trashcan_countdown_formatted%` - `最早到期条目的剩余时间, 格式化的时间`
- `%eclean_total_entities%` - `全服实体总数`
- `%eclean_total_chunks%` - `全服已加载区块总数`
- `%eclean_world_<世界名>_entities%` - `指定世界的实体总数`
- `%eclean_last_clean_time%` - `最近一次有审计记录的执行结束时间（可为零删除或失败）`
- `%eclean_last_removal_time%` - `最近一次实际删除实体的时间，无删除记录时为空`
- `%eclean_total_removed_entities%` - `本次进程运行内累计实际删除实体数，不包含垃圾桶清空的物品件数`
- `%eclean_next_clean%` - `距离下次清理的秒数`
- `%eclean_next_clean_formatted%` - `距离下次清理的格式化时间`
- `%eclean_trashcan_entries%` - `垃圾桶条目数`
- `%eclean_trashcan_total%` - `垃圾桶物品总数量`
- `%eclean_history_count%` - `当前保留的历史记录条数，最多 100 条`

## 统计口径

PlaceholderAPI 的实体数和区块数来自已加载区块的缓存，每 10 秒发起一次刷新，结果在扫描完成后发布。首次扫描完成前总数为 0，世界名称保留大小写。占位符请求不会触发世界扫描或加载区块。

历史按“实际执行的世界与模块”记录，包含来源、操作者、范围、配置版本、耗时、实际删除数、失败删除数、跳过区块数和执行中断标记。自动、命令和菜单入口共用记录；合并的并发请求只记一次实际执行，预览不记历史。所有实体清理数量均按实体计数，垃圾桶数量按物品件数计数。历史和累计计数在重启后重置。

## 时间与重启

格式化时间使用插件配置的语言。垃圾桶的过期时间按条目计算，合入新物品不会延长已有条目的到期时间。垃圾桶内容、历史和累计删除计数在服务器重启后重置。
