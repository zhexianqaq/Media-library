package com.zhexian.douyingallery.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhexian.douyingallery.domain.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val ROOT_PATH = stringPreferencesKey("root_path")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val LOOP_VIDEO = booleanPreferencesKey("loop_video")
        val MUTE_VIDEO = booleanPreferencesKey("mute_video")
        val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val LOCK_TIMEOUT = intPreferencesKey("lock_timeout_min")
        val LAST_RESUME_TS = longPreferencesKey("last_resume_ts")
        val PLAYBACK_PREFIX = "playback_"
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            rootPath = p[Keys.ROOT_PATH] ?: AppSettings.DEFAULT_ROOT,
            gridColumns = p[Keys.GRID_COLUMNS] ?: 3,
            loopVideo = p[Keys.LOOP_VIDEO] ?: true,
            muteVideo = p[Keys.MUTE_VIDEO] ?: false,
            lockEnabled = p[Keys.LOCK_ENABLED] ?: false,
            lockTimeoutMinutes = p[Keys.LOCK_TIMEOUT] ?: 3
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setRootPath(path: String) = edit { it[Keys.ROOT_PATH] = path }

    suspend fun setGridColumns(columns: Int) = edit { it[Keys.GRID_COLUMNS] = columns.coerceIn(2, 5) }

    suspend fun setLoopVideo(loop: Boolean) = edit { it[Keys.LOOP_VIDEO] = loop }

    suspend fun setMuteVideo(mute: Boolean) = edit { it[Keys.MUTE_VIDEO] = mute }

    suspend fun setLockEnabled(enabled: Boolean) = edit { it[Keys.LOCK_ENABLED] = enabled }

    suspend fun setLockTimeout(minutes: Int) = edit { it[Keys.LOCK_TIMEOUT] = minutes }

    suspend fun markResumed(ts: Long = System.currentTimeMillis()) = edit {
        it[Keys.LAST_RESUME_TS] = ts
    }

    suspend fun lastResumeAt(): Long =
        context.settingsDataStore.data.first()[Keys.LAST_RESUME_TS] ?: 0L

    // ---------- 播放进度 ----------

    suspend fun savePlaybackPosition(path: String, positionMs: Long) {
        context.settingsDataStore.edit { it[longPreferencesKey(Keys.PLAYBACK_PREFIX + path)] = positionMs }
    }

    suspend fun playbackPosition(path: String): Long =
        context.settingsDataStore.data.first()[longPreferencesKey(Keys.PLAYBACK_PREFIX + path)] ?: 0L

    suspend fun clearPlaybackPosition(path: String) {
        context.settingsDataStore.edit { it.remove(longPreferencesKey(Keys.PLAYBACK_PREFIX + path)) }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }
}
