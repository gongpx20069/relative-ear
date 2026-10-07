package io.github.gongpx20069.relativeear

import io.github.gongpx20069.relativeear.core.AppUpdate
import io.github.gongpx20069.relativeear.core.AppUpdates
import io.github.gongpx20069.relativeear.core.PublishedRelease
import io.github.gongpx20069.relativeear.core.ReleaseAsset
import org.json.JSONArray
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

enum class UpdateFailure { INVALID_RELEASE, ACCESS_RESTRICTED }
class UpdateCheckException(val reason: UpdateFailure, cause: Throwable? = null) : IOException(reason.name, cause)

class ReleaseUpdateClient(private val fetchPage: (Int) -> String = ::fetchReleasePage) {
    fun check(installedCode: Int, supportedAbis: List<String>): AppUpdate? {
        val releases = mutableListOf<PublishedRelease>()
        try {
            for (page in 1..20) {
                val entries = JSONArray(fetchPage(page))
                for (index in 0 until entries.length()) {
                    val entry = entries.getJSONObject(index)
                    val assets = entry.getJSONArray("assets")
                    val publishedAt = entry.get("published_at")
                    require(publishedAt == org.json.JSONObject.NULL || publishedAt is String && publishedAt.isNotBlank()) {
                        "Invalid release publication state"
                    }
                    releases.add(PublishedRelease(entry.getString("tag_name"), entry.getString("html_url"),
                        entry.getBoolean("draft"), publishedAt != org.json.JSONObject.NULL,
                        List(assets.length()) { assetIndex ->
                            val asset = assets.getJSONObject(assetIndex)
                            ReleaseAsset(asset.getString("name"), asset.getString("browser_download_url"),
                                asset.getLong("size"), asset.getString("state") == "uploaded")
                        }))
                }
                if (entries.length() < 30) return AppUpdates.findUpdate(releases, installedCode, supportedAbis)
            }
            throw UpdateCheckException(UpdateFailure.INVALID_RELEASE)
        } catch (error: JSONException) {
            throw UpdateCheckException(UpdateFailure.INVALID_RELEASE, error)
        } catch (error: IllegalArgumentException) {
            throw UpdateCheckException(UpdateFailure.INVALID_RELEASE, error)
        }
    }
}

private fun fetchReleasePage(page: Int): String {
    val url = URL("https://api.github.com/repos/${AppUpdates.REPOSITORY}/releases?per_page=30&page=$page")
    val connection = url.openConnection() as HttpsURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "Relative-Ear/${BuildConfig.VERSION_NAME} Android")
        val status = connection.responseCode
        if (status == 403 || status == 429) throw UpdateCheckException(UpdateFailure.ACCESS_RESTRICTED)
        if (status != 200) throw IOException("GitHub Releases HTTP $status")
        return connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 2 * 1024 * 1024) throw UpdateCheckException(UpdateFailure.INVALID_RELEASE)
                output.write(buffer, 0, count)
            }
            output.toString("UTF-8")
        }
    } finally {
        connection.disconnect()
    }
}
