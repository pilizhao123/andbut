package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.autoclicker.data.ConfigManager
import java.lang.ref.WeakReference
import com.example.autoclicker.util.NotificationUtils
import kotlin.random.Random

/**
 * 系统级自动连点服务。
 *
 * 借助 AccessibilityService 的 [dispatchGesture] 能力，在任意界面注入点击手势。
 * 通过 Handler/Looper 定时器控制点击节奏，支持多个坐标点、点击间隔、点击次数、
 * 循环模式、总运行时长、速度倍率、点击随机微调等高级运行策略。
 *
 * 启动方式：由用户在系统「无障碍」设置中手动启用；启用后系统回调 [onServiceConnected]。
 * 全局实例通过伴生对象保存（弱引用风格的置空策略），供悬浮窗与主页查询与控制。
 */
class ClickerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var config: ConfigManager

    /** 是否正在连点。使用 @Volatile 保证多线程可见性。 */
    @Volatile
    private var isRunning = false

    /** 已执行的点击次数计数。 */
    private var clickCount = 0

    /** 当前循环指向的坐标点索引。 */
    private var currentIndex = 0

    /** 音量下键上次按下时间，用于双击检测。 */
    @Volatile
    private var lastVolumeDownTime = 0L

    /** 开始连点时的系统时间（SystemClock.elapsedRealtime），用于时长控制。 */
    private var startElapsedTime = 0L

    /** 单次点击任务。 */
    private val clickRunnable = Runnable { tick() }

    /** 自动停止任务，用于"运行 N 秒后自动停止"。 */
    private val autoStopRunnable = Runnable { stopClicking() }

    override fun onCreate() {
        super.onCreate()
        config = ConfigManager(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // 作为前台服务运行，保证系统级连点在后台不被回收
        startForeground(
            NOTIFICATION_ID,
            NotificationUtils.buildNotification(this, getString(R.string.service_running))
        )
    }

    /** 开始连点。根据运行模式判断停止条件。 */
    fun startClicking() {
        if (isRunning) return
        val points = config.getClickPoints()
        if (points.isEmpty()) {
            Log.w(TAG, "没有点击点，无法开始")
            notifyStateChanged()
            return
        }

        val mode = config.getRunMode()
        when (mode) {
            ConfigManager.RUN_MODE_COUNT -> {
                Log.d(TAG, "开始连点(按次数): ${points.size} 个点, interval=${config.getInterval()}ms, speed=${config.getSpeedPercent()}%, count=${config.getClickCount()}, loop=${config.isLoopEnabled()}")
            }
            ConfigManager.RUN_MODE_DURATION -> {
                val ds = config.getDurationSeconds()
                if (ds <= 0) {
                    Log.w(TAG, "总时长模式但时长为 0，不开始")
                    notifyStateChanged()
                    return
                }
                Log.d(TAG, "开始连点(按时长): ${points.size} 个点, duration=${ds}s, speed=${config.getSpeedPercent()}%")
            }
            ConfigManager.RUN_MODE_MANUAL -> {
                Log.d(TAG, "开始连点(手动停止): ${points.size} 个点, speed=${config.getSpeedPercent()}%")
            }
        }

        isRunning = true
        clickCount = 0
        currentIndex = 0
        startElapsedTime = SystemClock.elapsedRealtime()

        // 运行 N 秒后自动停止
        val stopAfter = config.getStopAfterSeconds()
        if (stopAfter > 0) {
            handler.postDelayed(autoStopRunnable, stopAfter * 1000L)
        }

        handler.post(clickRunnable)
        notifyStateChanged()
    }

    /** 停止连点。 */
    fun stopClicking() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(clickRunnable)
        handler.removeCallbacks(autoStopRunnable)
        Log.d(TAG, "停止连点，共点击 $clickCount 次")
        notifyStateChanged()
    }

    /** 切换连点状态。 */
    fun toggleClicking() {
        if (isRunning) stopClicking() else startClicking()
    }

    /** 当前是否正在连点。 */
    fun isClicking(): Boolean = isRunning

    /** 本次连点已执行的真实点击次数（运行前/停止后保留最后一次数值）。 */
    fun getExecutedCount(): Int = clickCount

    /**
     * 核心点击循环：每执行一次点击后，按用户设定的间隔延时再执行下一次。
     * 顺序遍历坐标点列表；到达末尾时依据「循环模式」与「次数」决定是否继续。
     * 同时根据运行模式检查：总时长、运行后停止等终止条件。
     */
    private fun tick() {
        if (!isRunning) return
        val points = config.getClickPoints()
        if (points.isEmpty()) {
            Log.w(TAG, "点击点为空，停止连点")
            stopClicking()
            return
        }

        // 按总时长模式：检查是否超时
        if (config.getRunMode() == ConfigManager.RUN_MODE_DURATION) {
            val elapsed = (SystemClock.elapsedRealtime() - startElapsedTime) / 1000
            if (config.getDurationSeconds() > 0 && elapsed >= config.getDurationSeconds()) {
                Log.d(TAG, "达到总运行时长，停止")
                stopClicking()
                return
            }
        }

        val point = points[currentIndex.coerceIn(0, points.size - 1)]
        Log.d(TAG, "第 ${clickCount + 1} 次点击: (${point.x}, ${point.y})")
        dispatchTap(point.x, point.y)
        clickCount++

        // 按次数模式：达到设定次数停止
        if (config.getRunMode() == ConfigManager.RUN_MODE_COUNT) {
            val maxCount = config.getClickCount()
            if (maxCount > 0 && clickCount >= maxCount) {
                stopClicking()
                return
            }
        }

        currentIndex++
        if (currentIndex >= points.size) {
            // 非循环且无限次数（手动模式）下只执行一遍；按总时长模式也受循环开关控制
            if (!config.isLoopEnabled() && config.getRunMode() != ConfigManager.RUN_MODE_COUNT) {
                stopClicking()
                return
            }
            currentIndex = 0
        }

        // 应用速度倍率后的实际间隔
        val interval = config.getEffectiveInterval()
        handler.postDelayed(clickRunnable, interval)
    }

    /**
     * 在指定屏幕坐标注入一次 tap 手势。
     *
     * 注意：为了让小米/华为/OPPO 等 ROM 稳定识别为一次“点击”而非被丢弃，Path 需要一段
     * 非零长度的微小移动，且 StrokeDuration 不能太短（>= [TAP_DURATION]）。
     * 如果开启了随机微调，会在坐标基础上随机偏移 [randomOffset] 个像素。
     */
    private fun dispatchTap(x: Int, y: Int) {
        if (x <= 0 || y <= 0) {
            Log.w(TAG, "非法坐标，跳过: x=$x, y=$y")
            return
        }

        // 应用随机偏移（防检测）
        val randomOffset = config.getRandomOffset()
        val (finalX, finalY) = if (randomOffset > 0) {
            val dx = Random.nextInt(-randomOffset, randomOffset + 1)
            val dy = Random.nextInt(-randomOffset, randomOffset + 1)
            Pair((x + dx).coerceAtLeast(1), (y + dy).coerceAtLeast(1))
        } else {
            Pair(x, y)
        }

        try {
            val path = Path().apply {
                val fx = finalX.toFloat()
                val fy = finalY.toFloat()
                moveTo(fx, fy)
                // 微小向下移动再回来，模拟真实点击；部分 ROM 对零长度手势会直接忽略
                lineTo(fx + 1f, fy + 1f)
                lineTo(fx, fy)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d(TAG, "点击成功: ($finalX, $finalY)")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(TAG, "点击被取消: ($finalX, $finalY)")
                    }
                },
                null
            )
        } catch (e: Exception) {
            // 关键 dispatch 必须捕获异常，避免服务崩溃
            Log.e(TAG, "dispatchTap 异常: ($finalX, $finalY)", e)
        }
    }

    /**
     * 监听按键事件。短时间（[DOUBLE_PRESS_WINDOW] 毫秒）内双击音量下键切换连点启停。
     * 注意：不消费该事件（返回 super），避免影响系统音量调节，符合「避免与系统冲突」要求。
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0
        ) {
            val now = System.currentTimeMillis()
            if (now - lastVolumeDownTime <= DOUBLE_PRESS_WINDOW) {
                lastVolumeDownTime = 0L
                toggleClicking()
            } else {
                lastVolumeDownTime = now
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 本服务不依赖具体无障碍事件，留空
    }

    override fun onInterrupt() {
        stopClicking()
    }

    override fun onDestroy() {
        stopClicking()
        instance = null
        // 显式停止前台，避免通知残留
        stopForeground(true)
        super.onDestroy()
    }

    /** 通知状态监听器（例如悬浮窗）连点状态变化。 */
    private fun notifyStateChanged() {
        stateListener?.get()?.invoke(isRunning)
    }

    companion object {
        private const val TAG = "ClickerService"
        private const val NOTIFICATION_ID = 1001
        // 50ms 是一个比较兼顾“系统能识别为点击”和“不会太慢”的值；
        // 某些 ROM 对 < 50ms 的手势会直接丢弃。
        private const val TAP_DURATION: Long = 100
        private const val DOUBLE_PRESS_WINDOW = 500L

        /** 当前服务实例；服务销毁后置空，调用方必须判空。 */
        @Volatile
        private var instance: ClickerService? = null

        /** 状态变化监听器，使用弱引用避免内存泄漏。 */
        @Volatile
        private var stateListener: WeakReference<((Boolean) -> Unit)>? = null

        /** 获取当前服务实例（可能为 null）。 */
        fun getInstance(): ClickerService? = instance

        /** 注册/清除状态变化监听器。 */
        fun setStateListener(listener: ((Boolean) -> Unit)?) {
            stateListener = listener?.let { WeakReference(it) }
        }
    }
}
