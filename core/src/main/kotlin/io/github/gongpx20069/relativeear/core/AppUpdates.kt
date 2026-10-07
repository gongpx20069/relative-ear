package io.github.gongpx20069.relativeear.core

data class ReleaseAsset(val name: String, val url: String, val size: Long, val uploaded: Boolean)
data class PublishedRelease(
    val tag: String, val url: String, val draft: Boolean, val published: Boolean,
    val assets: List<ReleaseAsset>,
)
data class AppUpdate(val versionCode: Int, val releaseUrl: String, val apk: ReleaseAsset) {
    val versionName: String get() = "0.0.$versionCode"
}

object AppUpdates {
    const val REPOSITORY = "gongpx20069/relative-ear"
    const val RELEASES_URL = "https://github.com/$REPOSITORY/releases"
    val architectures = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal")

    fun versionCode(tag: String): Int? = Regex("v0\\.0\\.([1-9][0-9]*)").matchEntire(tag)
        ?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it in 1..2_100_000_000 }?.toInt()

    fun findUpdate(releases: List<PublishedRelease>, installedCode: Int, supportedAbis: List<String>): AppUpdate? {
        require(installedCode > 0)
        val candidates = releases.filter { !it.draft && it.published }
            .mapNotNull { release -> versionCode(release.tag)?.let { it to release } }
        val (code, release) = candidates.maxByOrNull { it.first } ?: return null
        if (code <= installedCode) return null
        val version = "0.0.$code"
        val releaseUrl = "$RELEASES_URL/tag/v$version"
        require(release.url == releaseUrl) { "Unexpected release page" }
        val base = "https://github.com/$REPOSITORY/releases/download/v$version/"
        val required = architectures.map { "relative-ear-$version-$it.apk" } + "SHA256SUMS.txt"
        val assets = required.associateWith { name ->
            val asset = release.assets.singleOrNull { it.name == name }
            requireNotNull(asset) { "Missing or duplicated release asset: $name" }
            require(asset.uploaded && asset.size > 0 && asset.url == base + name) { "Invalid release asset: $name" }
            asset
        }
        val architecture = supportedAbis.firstOrNull { it in architectures && it != "universal" } ?: "universal"
        return AppUpdate(code, releaseUrl, assets.getValue("relative-ear-$version-$architecture.apk"))
    }
}
