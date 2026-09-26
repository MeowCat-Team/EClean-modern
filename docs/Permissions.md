# 权限

[返回首页](../README.md)

插件为每个命令/操作注册了独立权限，均默认为 `op`；`eclean.admin` 为兼容旧版的综合管理权限，会授予以下全部能力，`eclean.trash` 仍等价于打开垃圾桶。

命令、菜单和补全使用一致的权限判断。权限插件显式撤销某项能力（包括其父权限或兼容别名）时，拒绝优先于其他别名、父权限或 `eclean.admin` 的授权；未授权非 OP 玩家的默认拒绝不会阻止单独授予叶子权限。

- `eclean.admin` 使用全部 EClean 功能（兼容旧版综合管理权限）
- `eclean.trash` 打开共享垃圾桶（等价 `eclean.command.trash.open`）
- `eclean.command.debug` 切换 Debug 消息
- `eclean.command.reload` 重载插件配置和语言
- `eclean.command.config` 查看、校验、比较配置及切换 profile
- `eclean.command.clean.all` 执行一次完整清理
- `eclean.command.clean.entity` 执行实体清理
- `eclean.command.clean.drop` 执行掉落物清理
- `eclean.command.clean.chunk` 执行密集实体清理
- `eclean.command.clean.preview` 使用 `--preview` 预演模式
- `eclean.command.clean.trash` 清空共享垃圾桶
- `eclean.command.trash.open` 打开共享垃圾桶
- `eclean.command.trash.stats` 查看垃圾桶统计
- `eclean.command.stats.self` 统计当前世界
- `eclean.command.stats.world` 统计指定世界
- `eclean.command.stats.gui` 打开统计 GUI
- `eclean.command.status.world` 查看指定世界状态
- `eclean.command.status.all` 查看全服状态
- `eclean.command.entity.self` 查询当前世界实体分布
- `eclean.command.entity.world` 查询指定世界实体分布
- `eclean.command.entity.chunk` 查询指定区块实体详情
- `eclean.command.players` 查看在线玩家坐标
- `eclean.command.show` 打开密集实体统计菜单
- `eclean.command.show.teleport` 在密集实体菜单中传送
- `eclean.command.show.clean` 在密集实体菜单中删除实体
- `eclean.command.history` 查看清理历史
- `eclean.command.top.entity` 查看实体类型排名
- `eclean.command.top.chunk` 查看区块排名
- `eclean.command.teleport` 使用 `/eclean tp` 传送
- `eclean.alerts` 接收插件自动提醒

跨世界统计菜单同时需要 `eclean.command.stats.gui` 与 `eclean.command.stats.world`。菜单中的实体分布、区块详情和传送操作各自检查对应权限。
