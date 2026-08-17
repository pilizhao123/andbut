package com.example.autoclicker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * 配置管理中心：负责点击点列表、点击间隔、点击次数、循环模式等设置的
 * 持久化与读取。所有数据保存在应用的 SharedPreferences 中。
 *
 * 线程安全说明：SharedPreferences 的读写本身是进程内安全的，本类未做额外同步，
 * 因其均在主线程（UI / 服务主线程）调用。
 */
class ConfigManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取已配置的点击点列表。 */
    fun getClickPoints(): List<ClickPoint> {
        val raw = prefs.getString(KEY_POINTS, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<ClickPoint>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ClickPoint(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        x = obj.optInt("x", 0),
                        y = obj.optInt("y", 0),
                        label = obj.optString("label", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 保存完整点击点列表。 */
    fun saveClickPoints(points: List<ClickPoint>) {
        try {
            val array = JSONArray()
            for (p in points) {
                array.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("x", p.x)
                        put("y", p.y)
                        put("label", p.label)
                    }
                )
            }
            prefs.edit { putString(KEY_POINTS, array.toString()) }
        } catch (e: Exception) {
            // 忽略序列化异常，避免影响主流程
        }
    }

    /** 新增一个点击点。 */
    fun addClickPoint(point: ClickPoint) {
        val list = getClickPoints().toMutableList()
        list.add(point)
        saveClickPoints(list)
    }

    /** 删除指定 id 的点击点。 */
    fun removeClickPoint(id: Long) {
        val list = getClickPoints().filter { it.id != id }
        saveClickPoints(list)
    }

    /** 点击间隔（毫秒）。 */
    fun getInterval(): Int = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL)

    fun setInterval(ms: Int) {
        prefs.edit { putInt(KEY_INTERVAL, ms.coerceAtLeast(MIN_INTERVAL)) }
    }

    /** 点击总次数；0 表示无限。 */
    fun getClickCount(): Int = prefs.getInt(KEY_COUNT, DEFAULT_COUNT)

    fun setClickCount(count: Int) {
        prefs.edit { putInt(KEY_COUNT, count.coerceAtLeast(0)) }
    }

    /** 循环模式：true 表示循环执行坐标列表；false 表示无限模式下只执行一遍。 */
    fun isLoopEnabled(): Boolean = prefs.getBoolean(KEY_LOOP, DEFAULT_LOOP)

    fun setLoopEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LOOP, enabled) }
    }

    companion object {
        private const val PREFS_NAME = "autoclicker_config"
        private const val KEY_POINTS = "click_points"
        private const val KEY_INTERVAL = "interval_ms"
        private const val KEY_COUNT = "click_count"
        private const val KEY_LOOP = "loop_enabled"

        const val DEFAULT_INTERVAL = 1000
        const val MIN_INTERVAL = 1
        const val DEFAULT_COUNT = 0
        const val DEFAULT_LOOP = true
    }
}
