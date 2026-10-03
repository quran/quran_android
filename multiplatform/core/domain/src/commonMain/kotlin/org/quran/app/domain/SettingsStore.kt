package org.quran.app.domain

interface SettingsStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
}
