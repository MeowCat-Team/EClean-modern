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

    fun stop() { generation++; task?.cancel(); task = null }

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
            if (response.statusCode() != 200 || token != generation) return
            val json = JsonParser.parseString(response.body()).asJsonArray
            if (json.isEmpty) return
            val latest = json[0].asJsonObject.get("tag_name").asString
            val current = top.e404.eclean.PL.pluginMeta.version
            if (latest != current) {
                Bukkit.getConsoleSender().sendMessage(
                    "§6[EClean-Modern] §e新版本可用: §b$latest §e(当前: §7$current§e) → §a$GITHUB_URL"
                )
            }
        } catch (_: Exception) {
        }
    }
}
