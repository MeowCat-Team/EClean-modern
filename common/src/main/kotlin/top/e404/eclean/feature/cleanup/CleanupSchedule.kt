package top.e404.eclean.feature.cleanup

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import top.e404.eclean.config.ConfigBundle
import java.time.Duration
import java.time.ZonedDateTime

data class CleanupDue(val worlds: List<String>, val remainingSeconds: Long, val elapsedSeconds: Long)

/** Deadlines are retained between polls, so coarse polling cannot skip a scheduled execution. */
class CleanupSchedule(private val config: ConfigBundle, worlds: List<String>, now: ZonedDateTime) {
    private val cron = config.cleanup.cron?.let {
        ExecutionTime.forCron(CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)).parse(it))
    }
    private var nextCron = cron?.nextExecution(now)?.orElse(null)
    private val deadlines = mutableMapOf<String, ZonedDateTime>()
    private var started = now

    init { reset(worlds, now) }

    fun reset(worlds: List<String>, now: ZonedDateTime) {
        started = now
        deadlines.clear()
        worlds.filter { config.perWorld.worlds[it]?.enabled != false }.forEach { deadlines[it] = nextInterval(it, now) }
    }

    fun poll(worlds: List<String>, now: ZonedDateTime): CleanupDue {
        val enabled = worlds.filter { config.perWorld.worlds[it]?.enabled != false }
        val due: List<String>
        val next: ZonedDateTime?
        if (cron != null) {
            due = if (nextCron?.let { !now.isBefore(it) } == true) {
                nextCron = cron.nextExecution(now).orElse(null)
                enabled
            } else emptyList()
            next = nextCron
        } else {
            deadlines.keys.retainAll(enabled.toSet())
            enabled.forEach { deadlines.putIfAbsent(it, nextInterval(it, now)) }
            due = enabled.filter { !now.isBefore(deadlines.getValue(it)) }
            due.forEach { deadlines[it] = nextInterval(it, now) }
            next = deadlines.values.minOrNull()
        }
        return CleanupDue(due, next?.let { Duration.between(now, it).seconds.coerceAtLeast(0) } ?: 0,
            Duration.between(started, now).seconds.coerceAtLeast(0))
    }

    private fun nextInterval(world: String, now: ZonedDateTime): ZonedDateTime =
        now.plusSeconds(config.perWorld.worlds[world]?.intervalSeconds ?: config.cleanup.intervalSeconds)
}
