package top.e404.eclean.command

import top.e404.eclean.common.api.CommonCommandSender
import top.e404.eclean.config.ConfigurationManager
import top.e404.eclean.config.effective
import top.e404.eclean.config.model.ConfigProfile
import top.e404.eclean.config.sections

/** The configuration command's decisions and output data are shared by every loader. */
class ConfigCommandHandler(
    private val configuration: ConfigurationManager,
    private val worldExists: (String) -> Boolean,
    private val normalPath: () -> String,
    private val devPath: () -> String,
    private val inspect: (Boolean) -> Unit,
    private val reload: (ConfigProfile) -> Unit,
    private val feedback: (String, List<Pair<String, Any>>) -> Unit,
) {
    fun handle(sender: CommonCommandSender, args: Array<out String>) {
        if (!sender.hasPermission(PermissionNode.CONFIG.node)) {
            send("command.no_permission")
            return
        }
        val operation = args.getOrNull(1)?.lowercase()
        val validSize = if (operation in listOf("profile", "effective")) args.size in 2..3 else args.size <= 2
        if (!validSize) { usage(); return }
        when (operation) {
            null, "show", "info" -> show()
            "validate" -> inspect(false)
            "diff" -> inspect(true)
            "effective" -> effective(args.getOrNull(2))
            "profile" -> switchProfile(args.getOrNull(2))
            else -> usage()
        }
    }

    private fun effective(world: String?) {
        if (world != null && !worldExists(world)) {
            send("command.invalid.world", "world" to world)
            return
        }
        val current = configuration.current
        val effective = world?.let { current.effective(it) }
        send("command.config.effective", "profile" to configuration.currentProfile.id,
            "world" to (world ?: "*"), "revision" to configuration.revision)
        send("command.config.semantics")
        effective?.sources?.forEach { (field, source) ->
            send("command.config.source", "field" to field, "source" to source)
        }
        (effective?.bundle ?: current).sections().forEach { (section, yaml) ->
            send("command.config.line", "line" to "[${section.displayName}]")
            yaml.lines().forEach { send("command.config.line", "line" to it) }
        }
    }

    private fun show() {
        send("command.config.show")
        send("command.config.current", "profile" to configuration.currentProfile.id)
        send("command.config.normal_path", "path" to normalPath())
        send("command.config.dev_path", "path" to devPath())
    }

    private fun switchProfile(raw: String?) {
        val profile = ConfigProfile.entries.firstOrNull { it.id.equals(raw, ignoreCase = true) }
        if (profile == null) {
            send("command.config.invalid_profile", "value" to (raw ?: ""))
            usage()
            return
        }
        reload(profile)
    }

    private fun usage() = send("command.usage.config")
    private fun send(key: String, vararg args: Pair<String, Any>) = feedback(key, args.toList())
}
