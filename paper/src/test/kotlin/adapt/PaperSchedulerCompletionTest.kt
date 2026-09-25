package adapt

import io.papermc.paper.threadedregions.scheduler.EntityScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask as NativeTask
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import top.e404.eclean.paper.adapt.PaperScheduler
import java.lang.reflect.Proxy
import java.util.function.Consumer
import java.util.logging.Logger
import kotlin.test.*

class PaperSchedulerCompletionTest {
    private val plugin = Proxy.newProxyInstance(Plugin::class.java.classLoader, arrayOf(Plugin::class.java)) { _, method, _ ->
        when (method.name) { "getLogger" -> Logger.getAnonymousLogger(); "getName" -> "EClean-test"; else -> null }
    } as Plugin

    @Test fun `retired entity resolves cancellation instead of leaving an unfinished task`() {
        val entity = entity { _, args -> (args!![2] as Runnable).run(); null }
        var calls = 0
        val future = PaperScheduler(plugin).submitForEntity(entity) { calls++ }
        assertTrue(future.isCancelled)
        assertEquals(0, calls)
    }

    @Test fun `shutdown cancels queued entity task and resolves its future`() {
        var cancelled = false
        val native = Proxy.newProxyInstance(NativeTask::class.java.classLoader, arrayOf(NativeTask::class.java)) { _, method, _ ->
            if (method.name == "cancel") cancelled = true
            method.returnType.enumConstants?.firstOrNull()
        } as NativeTask
        val scheduler = PaperScheduler(plugin)
        val future = scheduler.submitForEntity(entity { _, _ -> native }) { error("Must not run") }
        assertFalse(future.isDone)
        scheduler.cancelAll()
        assertTrue(cancelled)
        assertTrue(future.isCancelled)
        val rejected = scheduler.submitForEntity(entity { _, _ -> error("Disabled scheduler must not submit") }) {}
        assertTrue(rejected.isCancelled)
    }

    @Test fun `entity task exception is observable and completes once`() {
        val entity = entity { _, args ->
            @Suppress("UNCHECKED_CAST")
            (args!![1] as Consumer<NativeTask?>).accept(null)
            null
        }
        val scheduler = PaperScheduler(plugin)
        var calls = 0
        val future = scheduler.submitForEntity(entity) { calls++; error("test failure") }
        assertTrue(future.isCompletedExceptionally)
        scheduler.cancelAll()
        assertEquals(1, calls)
    }

    private fun entity(dispatch: (java.lang.reflect.Method, Array<out Any?>?) -> Any?): Entity {
        val entityScheduler = Proxy.newProxyInstance(EntityScheduler::class.java.classLoader, arrayOf(EntityScheduler::class.java)) { _, method, args ->
            dispatch(method, args)
        }
        return Proxy.newProxyInstance(Entity::class.java.classLoader, arrayOf(Entity::class.java)) { _, method, _ ->
            if (method.name == "getScheduler") entityScheduler else null
        } as Entity
    }
}
