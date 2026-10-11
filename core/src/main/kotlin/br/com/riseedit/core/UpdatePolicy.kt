package br.com.riseedit.core

data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    companion object {
        fun parse(tag: String): ReleaseVersion? {
            val parts = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(tag)?.groupValues ?: return null
            return runCatching { ReleaseVersion(parts[1].toInt(), parts[2].toInt(), parts[3].toInt()) }.getOrNull()
        }
    }
}

object UpdatePolicy {
    const val REPOSITORY = "Marcelov07br/rise-edit"
    const val ASSET_NAME = "rise-edit.apk"
    fun newer(candidate: String, installed: String): Boolean {
        val a = ReleaseVersion.parse(candidate) ?: return false
        val b = ReleaseVersion.parse(installed) ?: return false
        return a > b
    }
    fun digest(value: String): String? = Regex("^sha256:([a-fA-F0-9]{64})$").matchEntire(value)?.groupValues?.get(1)?.lowercase()
}
