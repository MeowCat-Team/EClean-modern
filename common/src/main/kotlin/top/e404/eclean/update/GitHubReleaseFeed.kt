package top.e404.eclean.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun interface ReleaseFeed {
    fun fetch(userAgent: String): List<ReleaseCandidate>
}

class ReleaseRateLimitedException : Exception()

class GitHubReleaseFeed(
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) : ReleaseFeed {
    override fun fetch(userAgent: String): List<ReleaseCandidate> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent)
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 429 ||
            (response.statusCode() == 403 && response.headers().firstValue("X-RateLimit-Remaining").orElse("") == "0")) {
            throw ReleaseRateLimitedException()
        }
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
        return parseReleaseCandidates(response.body())
    }

    companion object {
        const val RELEASE_URL = "https://github.com/MeowCat-Team/EClean-modern"
        private const val API_URL = "https://api.github.com/repos/MeowCat-Team/EClean-modern/releases"
    }
}

internal fun parseReleaseCandidates(body: String): List<ReleaseCandidate> {
    val releases = Json.parseToJsonElement(body) as? JsonArray ?: error("Expected a release list")
    return releases.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val tag = (item["tag_name"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return@mapNotNull null
        ReleaseCandidate(
            tag = tag,
            draft = (item["draft"] as? JsonPrimitive)?.booleanOrNull ?: false,
            prerelease = (item["prerelease"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }
}
