package top.e404.eclean.config

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser

/** Reject ambiguous or unsafe values before any runtime state is changed. */
object ConfigValidator {
    fun validate(bundle: ConfigBundle): ConfigBundle = (if (bundle.cleanup.cron?.isBlank() == true)
        bundle.copy(cleanup = bundle.cleanup.copy(cron = null)) else bundle).also { c ->
        fun positive(path: String, value: Long) = require(value in 1..(Long.MAX_VALUE / 2000)) { "$path must be positive and leave room for a millisecond deadline" }
        fun distance(path: String, value: Double?) = require(value == null || value.isFinite() && value >= 0) { "$path must be a finite non-negative distance" }
        require(Regex("[a-zA-Z][a-zA-Z0-9_-]{1,31}").matches(c.global.language)) { "global.language must be a locale name, not a path" }
        require(c.global.debugCooldownMillis >= 0) { "global.debugCooldownMillis must be non-negative" }
        positive("cleanup.intervalSeconds", c.cleanup.intervalSeconds)
        positive("cleanup.alertCheckIntervalSeconds", c.cleanup.alertCheckIntervalSeconds)
        require(c.cleanup.alertEntityThreshold >= 0) { "cleanup.alertEntityThreshold must be non-negative" }
        c.cleanup.cron?.let { expression ->
            require(expression.isNotBlank()) { "cleanup.cron must be null or a Quartz expression" }
            try { CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)).parse(expression).validate() }
            catch (error: Exception) { throw IllegalArgumentException("cleanup.cron: ${error.message}", error) }
            require(c.perWorld.worlds.values.none { it.intervalSeconds != null }) { "perWorld.intervalSeconds cannot be combined with cleanup.cron" }
        }
        distance("drop.maxDistance", c.drop.maxDistance)
        distance("living.maxDistance", c.living.maxDistance)
        c.drop.typeRules.forEach { (type, rule) -> distance("drop.typeRules.$type.maxDistance", rule.maxDistance) }
        c.living.typeRules.forEach { (type, rule) -> distance("living.typeRules.$type.maxDistance", rule.maxDistance) }
        require(c.chunkDensity.alertThreshold >= 0) { "chunkDensity.alertThreshold must be non-negative" }
        c.chunkDensity.entityLimits.forEach { (pattern, limit) -> require(limit >= 0) { "chunkDensity.entityLimits.$pattern must be non-negative" } }
        c.trashcan.clearIntervalSeconds?.let { positive("trashcan.clearIntervalSeconds", it) }
        c.perWorld.worlds.forEach { (name, rule) ->
            require(name.isNotBlank()) { "perWorld.worlds contains a blank world name" }
            rule.intervalSeconds?.let { positive("perWorld.$name.intervalSeconds", it) }
            distance("perWorld.$name.dropMaxDistance", rule.dropMaxDistance)
            distance("perWorld.$name.livingMaxDistance", rule.livingMaxDistance)
        }
        val scheduler = c.advanced.scheduler
        require(scheduler.chunkScanBatchSize in 1..1000) { "advanced.scheduler.chunkScanBatchSize must be between 1 and 1000" }
        require(scheduler.chunkScanIntervalTicks in 1..1200) { "advanced.scheduler.chunkScanIntervalTicks must be between 1 and 1200" }
        require(scheduler.cleanupTickIntervalTicks in 1..1200) { "advanced.scheduler.cleanupTickIntervalTicks must be between 1 and 1200" }
        require(scheduler.trashcanTickIntervalTicks in 1..1200) { "advanced.scheduler.trashcanTickIntervalTicks must be between 1 and 1200" }
        val color = Regex("<(?:#[0-9a-fA-F]{6}|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>")
        listOf(c.advanced.menu.primaryColor, c.advanced.menu.secondaryColor, c.advanced.menu.accentColor).forEach {
            require(color.matches(it)) { "advanced.menu colors must be a single MiniMessage color tag" }
        }
    }
}
