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

    /** 将毫秒间隔转为"分+秒"展示；内部仍按毫秒存储。 */
    fun getIntervalMinutes(): Int = getInterval() / 60000
    fun getIntervalSeconds(): Int = (getInterval() % 60000) / 1000
    fun getIntervalMillis(): Int = getInterval() % 1000

    fun setInterval(minutes: Int, seconds: Int, millis: Int = 0) {
        val total = minutes * 60000 + seconds * 1000 + millis
        setInterval(total.coerceAtLeast(MIN_INTERVAL))
    }

    /** 运行模式：0=按次数，1=按总时长，2=手动停止。 */
    fun getRunMode(): Int = prefs.getInt(KEY_RUN_MODE, DEFAULT_RUN_MODE)

    fun setRunMode(mode: Int) {
        prefs.edit { putInt(KEY_RUN_MODE, mode.coerceIn(0, 2)) }
    }

    /** 点击总次数；0 表示无限（仅在按次数模式下使用）。 */
    fun getClickCount(): Int = prefs.getInt(KEY_COUNT, DEFAULT_COUNT)

    fun setClickCount(count: Int) {
        prefs.edit { putInt(KEY_COUNT, count.coerceAtLeast(0)) }
    }

    /** 总运行时长（秒）；0 表示不限（仅在按总时长模式下使用）。 */
    fun getDurationSeconds(): Int = prefs.getInt(KEY_DURATION_SECONDS, DEFAULT_DURATION_SECONDS)

    fun setDurationSeconds(seconds: Int) {
        prefs.edit { putInt(KEY_DURATION_SECONDS, seconds.coerceAtLeast(0)) }
    }

    fun getDurationMinutes(): Int = getDurationSeconds() / 60
    fun getDurationRemainSeconds(): Int = getDurationSeconds() % 60

    fun setDuration(minutes: Int, seconds: Int) {
        setDurationSeconds(minutes * 60 + seconds)
    }

    /** 运行速度倍率，以百分比存储：100 = 1.0x、200 = 2.0x、50 = 0.5x。 */
    fun getSpeedPercent(): Int = prefs.getInt(KEY_SPEED_PERCENT, DEFAULT_SPEED_PERCENT)

    fun setSpeedPercent(percent: Int) {
        prefs.edit { putInt(KEY_SPEED_PERCENT, percent.coerceAtLeast(MIN_SPEED_PERCENT).coerceAtMost(MAX_SPEED_PERCENT)) }
    }

    /** 计算实际执行间隔（毫秒），已应用速度倍率。 */
    fun getEffectiveInterval(): Long {
        val base = getInterval().toLong().coerceAtLeast(MIN_INTERVAL.toLong())
        val speed = getSpeedPercent().toFloat() / 100f
        return (base / speed).toLong().coerceAtLeast(MIN_INTERVAL.toLong())
    }

    /** 循环模式：true 表示循环执行坐标列表；false 表示无限模式下只执行一遍。 */
    fun isLoopEnabled(): Boolean = prefs.getBoolean(KEY_LOOP, DEFAULT_LOOP)

    fun setLoopEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LOOP, enabled) }
    }

    /** 点击位置随机微调范围（像素），0 表示关闭。 */
    fun getRandomOffset(): Int = prefs.getInt(KEY_RANDOM_OFFSET, DEFAULT_RANDOM_OFFSET)

    fun setRandomOffset(offset: Int) {
        prefs.edit { putInt(KEY_RANDOM_OFFSET, offset.coerceAtLeast(0).coerceAtMost(MAX_RANDOM_OFFSET)) }
    }

    /** 启动后是否自动开始连点。 */
    fun isAutoStart(): Boolean = prefs.getBoolean(KEY_AUTO_START, DEFAULT_AUTO_START)

    fun setAutoStart(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_START, enabled) }
    }

    /** 运行 N 秒后自动停止，0 表示不自动停止。 */
    fun getStopAfterSeconds(): Int = prefs.getInt(KEY_STOP_AFTER_SECONDS, DEFAULT_STOP_AFTER_SECONDS)

    fun setStopAfterSeconds(seconds: Int) {
        prefs.edit { putInt(KEY_STOP_AFTER_SECONDS, seconds.coerceAtLeast(0)) }
    }

    fun getStopAfterMinutes(): Int = getStopAfterSeconds() / 60
    fun getStopAfterRemainSeconds(): Int = getStopAfterSeconds() % 60

    fun setStopAfter(minutes: Int, seconds: Int) {
        setStopAfterSeconds(minutes * 60 + seconds)
    }

    companion object {
        private const val PREFS_NAME = "autoclicker_config"
        private const val KEY_POINTS = "click_points"
        private const val KEY_INTERVAL = "interval_ms"
        private const val KEY_RUN_MODE = "run_mode"
        private const val KEY_COUNT = "click_count"
        private const val KEY_DURATION_SECONDS = "duration_seconds"
        private const val KEY_SPEED_PERCENT = "speed_percent"
        private const val KEY_LOOP = "loop_enabled"
        private const val KEY_RANDOM_OFFSET = "random_offset"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_STOP_AFTER_SECONDS = "stop_after_seconds"

        const val RUN_MODE_COUNT = 0
        const val RUN_MODE_DURATION = 1
        const val RUN_MODE_MANUAL = 2

        const val DEFAULT_INTERVAL = 1000
        const val MIN_INTERVAL = 50
        const val DEFAULT_RUN_MODE = RUN_MODE_COUNT
        const val DEFAULT_COUNT = 0
        const val DEFAULT_DURATION_SECONDS = 0
        const val DEFAULT_SPEED_PERCENT = 100
        const val MIN_SPEED_PERCENT = 25
        const val MAX_SPEED_PERCENT = 1000
        const val DEFAULT_LOOP = true
        const val DEFAULT_RANDOM_OFFSET = 0
        const val MAX_RANDOM_OFFSET = 20
        const val DEFAULT_AUTO_START = false
        const val DEFAULT_STOP_AFTER_SECONDS = 0
    }
}
