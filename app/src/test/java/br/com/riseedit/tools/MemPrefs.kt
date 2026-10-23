package br.com.riseedit.tools

import android.content.SharedPreferences

/** Minimal in-memory SharedPreferences for layoutlib tests (perf only). */
class MemPrefs : SharedPreferences {
    private val m = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = m
    override fun getString(k: String?, d: String?) = m[k] as? String ?: d
    override fun getStringSet(k: String?, d: MutableSet<String>?) = d
    override fun getInt(k: String?, d: Int) = m[k] as? Int ?: d
    override fun getLong(k: String?, d: Long) = m[k] as? Long ?: d
    override fun getFloat(k: String?, d: Float) = m[k] as? Float ?: d
    override fun getBoolean(k: String?, d: Boolean) = m[k] as? Boolean ?: d
    override fun contains(k: String?) = m.containsKey(k)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(k: String?, v: String?) = apply { m[k!!] = v }
        override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { m[k!!] = v }
        override fun putInt(k: String?, v: Int) = apply { m[k!!] = v }
        override fun putLong(k: String?, v: Long) = apply { m[k!!] = v }
        override fun putFloat(k: String?, v: Float) = apply { m[k!!] = v }
        override fun putBoolean(k: String?, v: Boolean) = apply { m[k!!] = v }
        override fun remove(k: String?) = apply { m.remove(k) }
        override fun clear() = apply { m.clear() }
        override fun commit() = true
        override fun apply() {}
    }
}
