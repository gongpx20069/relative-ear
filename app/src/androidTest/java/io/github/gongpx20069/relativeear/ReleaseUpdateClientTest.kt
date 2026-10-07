package io.github.gongpx20069.relativeear

import io.github.gongpx20069.relativeear.core.AppUpdates
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

internal fun releaseFixture(code: Int): JSONObject {
    val version = "0.0.$code"
    val names = AppUpdates.architectures.map { "relative-ear-$version-$it.apk" } + "SHA256SUMS.txt"
    return JSONObject().put("tag_name", "v$version").put("html_url", "${AppUpdates.RELEASES_URL}/tag/v$version")
        .put("draft", false).put("prerelease", true).put("published_at", "2026-10-07T00:00:00Z")
        .put("assets", JSONArray(names.map { name ->
            JSONObject().put("name", name).put("browser_download_url",
                "https://github.com/${AppUpdates.REPOSITORY}/releases/download/v$version/$name")
                .put("size", 1048576).put("state", "uploaded")
        }))
}

class ReleaseUpdateClientTest {
    @Test fun readsPublishedPreviewsAcrossPagesRatherThanUsingLatestStableEndpoint() {
        val calls = mutableListOf<Int>()
        val client = ReleaseUpdateClient { page ->
            calls.add(page)
            if (page == 1) JSONArray(List(30) { releaseFixture(1) }).toString()
            else JSONArray(listOf(releaseFixture(10))).toString()
        }
        assertEquals("0.0.10", client.check(5, listOf("x86_64"))?.versionName)
        assertEquals(listOf(1, 2), calls)
    }
    @Test fun malformedDataAndMissingAssetsAreErrorsNotNoUpdateResults() {
        for (response in listOf("invalid JSON", "{}", "[{}]",
            JSONArray(listOf(releaseFixture(6).put("assets", JSONArray()))).toString(),
            JSONArray(listOf(releaseFixture(6).put("published_at", ""))).toString())) {
            val error = assertThrows(UpdateCheckException::class.java) {
                ReleaseUpdateClient { response }.check(5, emptyList())
            }
            assertEquals(UpdateFailure.INVALID_RELEASE, error.reason)
        }
    }
    @Test fun emptyListIsLegitimateButTransportErrorsAndPaginationLimitsAreNot() {
        assertNull(ReleaseUpdateClient { "[]" }.check(5, emptyList()))
        assertThrows(IOException::class.java) { ReleaseUpdateClient { throw IOException("offline") }.check(5, emptyList()) }
        var calls = 0
        assertThrows(UpdateCheckException::class.java) {
            ReleaseUpdateClient { calls++; JSONArray(List(30) { releaseFixture(1) }).toString() }.check(5, emptyList())
        }
        assertEquals(20, calls)
    }
}
