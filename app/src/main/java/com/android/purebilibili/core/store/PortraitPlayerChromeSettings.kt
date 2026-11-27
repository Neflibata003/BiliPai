package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 竖屏播放器（竖屏流 / 竖屏全屏）的系统栏偏好。
 *
 * 默认保留系统状态栏（时间、电量、信号），只隐藏导航栏；关闭后恢复完全沉浸。
 * 内存缓存让首帧直接拿到上次的值，避免进入竖屏时状态栏闪一下再消失。
 */
object PortraitPlayerChromeSettings {
    const val DEFAULT_SHOW_STATUS_BAR = true

    private val KEY_SHOW_STATUS_BAR = booleanPreferencesKey("portrait_player_show_status_bar")

    @Volatile
    private var cachedShowStatusBar: Boolean? = null

    fun peekShowStatusBar(): Boolean = cachedShowStatusBar ?: DEFAULT_SHOW_STATUS_BAR

    fun getShowStatusBar(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences ->
                (preferences[KEY_SHOW_STATUS_BAR] ?: DEFAULT_SHOW_STATUS_BAR).also {
                    cachedShowStatusBar = it
                }
            }
            .distinctUntilChanged()

    suspend fun setShowStatusBar(context: Context, value: Boolean) {
        cachedShowStatusBar = value
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SHOW_STATUS_BAR] = value
        }
    }
}
