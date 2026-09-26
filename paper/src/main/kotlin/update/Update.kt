package top.e404.eclean.update

import com.google.gson.JsonParser
import org.bukkit.Bukkit
import top.e404.eclean.config.Config
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit

object Update {
    private const val GITHUB_API = "https://api.github.com/repos/MeowCat-Team/EClean-modern/releases"
    private const val GITHUB_URL = "https://github.com/MeowCat-Team/EClean-modern"
    private val httpClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build()
    private var task: io.papermc.paper.threadedregions.scheduler.ScheduledTask? = null
    @Volatile private var generation = 0L
    private var lastFailureAt = 0L
    private var lastNotified: String? = null

    @Synchronized fun stop() { generation++; task?.cancel(); task = null }

    fun register() {
        stop()
        val plugin = top.e404.eclean.PL
        val token = generation
        task = plugin.server.asyncScheduler.runAtFixedRate(
            plugin,
            { _ -> check(token) },
            20L,
            6L * 60 * 60,
            TimeUnit.SECONDS,
        )
    }

    private fun check(token: Long) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API))
                .header("Accept", "application/vnd.github+json")
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (token != generation) return
            check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
            val json = JsonParser.parseString(response.body()).asJsonArray
            val current = top.e404.eclean.PL.pluginMeta.version
            val latest = newerRelease(current, json.mapNotNull { element ->
                val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val tag = item.get("tag_name")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                ReleaseCandidate(tag, item.get("draft")?.asBoolean ?: false, item.get("prerelease")?.asBoolean ?: false)
            }) ?: return
            synchronized(this) {
                if (token != generation || lastNotified == latest.tag) return
                top.e404.eclean.PL.services.messages.send(Bukkit.getConsoleSender(), top.e404.eclean.lang.MLang[
                    "update.available", "latest" to latest.tag, "current" to current, "url" to GITHUB_URL,
                ])
                lastNotified = latest.tag
            }
        } catch (failure: Exception) {
            synchronized(this) {
                val now = System.currentTimeMillis()
                if (token == generation && now - lastFailureAt >= TimeUnit.HOURS.toMillis(1)) {
                    lastFailureAt = now
                    top.e404.eclean.PL.services.messages.send(Bukkit.getConsoleSender(), top.e404.eclean.lang.MLang[
                        "update.failed", "reason" to (failure.message ?: failure.javaClass.simpleName),
                    ])
                }
            }
        }
    }
}
