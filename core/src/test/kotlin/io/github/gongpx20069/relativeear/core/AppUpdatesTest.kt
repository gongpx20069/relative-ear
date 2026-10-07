package io.github.gongpx20069.relativeear.core

import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    private fun release(code: Int, draft: Boolean = false, published: Boolean = true): PublishedRelease {
        val version = "0.0.$code"
        val names = AppUpdates.architectures.map { "relative-ear-$version-$it.apk" } + "SHA256SUMS.txt"
        return PublishedRelease("v$version", "${AppUpdates.RELEASES_URL}/tag/v$version", draft, published,
            names.map { ReleaseAsset(it, "https://github.com/${AppUpdates.REPOSITORY}/releases/download/v$version/$it", 100, true) })
    }
    @Test fun usesNumericVersionsAndPublishedPreviewReleasesNotArrayOrder() {
        val update = AppUpdates.findUpdate(listOf(release(9), release(2), release(10)), 5, listOf("arm64-v8a"))
        assertEquals("0.0.10", update?.versionName)
        assertEquals("relative-ear-0.0.10-arm64-v8a.apk", update?.apk?.name)
    }
    @Test fun neverDowngradesOrOffersDraftsOrUnpublishedVersions() {
        assertNull(AppUpdates.findUpdate(listOf(release(5), release(4), release(8, draft = true),
            release(9, published = false)), 5, listOf("arm64-v8a")))
        assertNull(AppUpdates.findUpdate(emptyList(), 5, emptyList()))
    }
    @Test fun rejectsNoncanonicalVersionsAndOverflow() {
        for (tag in listOf("0.0.6", "v0.0.06", "v0.0.0", "v0.1.0", "v0.0.2147483647",
            "v0.0.999999999999999999999999999999", "v0.0.6-beta")) assertNull(AppUpdates.versionCode(tag))
        assertEquals(2_100_000_000, AppUpdates.versionCode("v0.0.2100000000"))
    }
    @Test fun respectsDeviceAbiOrderAndFallsBackToUniversalForUnknownArchitectures() {
        for (abi in AppUpdates.architectures.filter { it != "universal" }) {
            val update = AppUpdates.findUpdate(listOf(release(6)), 5, listOf("unknown", abi))
            assertTrue(requireNotNull(update).apk.name.endsWith("-$abi.apk"))
        }
        assertTrue(requireNotNull(AppUpdates.findUpdate(listOf(release(6)), 5,
            listOf("armeabi-v7a", "arm64-v8a"))).apk.name.endsWith("-armeabi-v7a.apk"))
        assertTrue(requireNotNull(AppUpdates.findUpdate(listOf(release(6)), 5, emptyList())).apk.name.endsWith("-universal.apk"))
    }
    @Test fun incompleteNewestReleaseIsAnErrorNotAnOlderOrUpToDateFallback() {
        val newest = release(8).let { it.copy(assets = it.assets.dropLast(1)) }
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdates.findUpdate(listOf(newest, release(6), release(5)), 5, listOf("arm64-v8a"))
        }
        val duplicate = release(6).let { it.copy(assets = it.assets + it.assets.first()) }
        assertThrows(IllegalArgumentException::class.java) { AppUpdates.findUpdate(listOf(duplicate), 5, emptyList()) }
    }
    @Test fun onlyOffersUploadedAssetsFromExactOfficialReleaseLocations() {
        for (asset in listOf(
            release(6).assets.first().copy(url = "https://example.com/app.apk"),
            release(6).assets.first().copy(size = 0),
            release(6).assets.first().copy(uploaded = false),
        )) {
            val invalid = release(6).let { it.copy(assets = listOf(asset) + it.assets.drop(1)) }
            assertThrows(IllegalArgumentException::class.java) { AppUpdates.findUpdate(listOf(invalid), 5, emptyList()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdates.findUpdate(listOf(release(6).copy(url = "https://example.com")), 5, emptyList())
        }
    }
}
