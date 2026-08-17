package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.autoclicker.data.ConfigManager
import java.lang.ref.WeakReference
import com.example.autoclicker.util.NotificationUtils

/**
 * 系统级自动连点服务。
 *
 * 借助 AccessibilityService 的 [dispatchGesture] 能力，在任意界面注入点击手势。
 * 通过 Handler/Looper 定时器控制点击节奏，支持多个坐标点、点击间隔、点击次数与循环模式。
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

    /** 单次点击任务。 */
    private val clickRunnable = Runnable { tick() }

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

    /** 开始连点。若已在运行或没有坐标点则忽略。 */
    fun startClicking() {
        if (isRunning) return
        if (config.getClickPoints().isEmpty()) {
            notifyStateChanged()
            return
        }
        isRunning = true
        clickCount = 0
        currentIndex = 0
        handler.post(clickRunnable)
        notifyStateChanged()
    }

    /** 停止连点。 */
    fun stopClicking() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(clickRunnable)
        notifyStateChanged()
    }

    /** 切换连点状态。 */
    fun toggleClicking() {
        if (isRunning) stopClicking() else startClicking()
    }

    /** 当前是否正在连点。 */
    fun isClicking(): Boolean = isRunning

    /**
     * 核心点击循环：每执行一次点击后，按用户设定的间隔延时再执行下一次。
     * 顺序遍历坐标点列表；到达末尾时依据「循环模式」与「次数」决定是否继续。
     */
    private fun tick() {
        if (!isRunning) return
        val points = config.getClickPoints()
        if (points.isEmpty()) {
            stopClicking()
            return
        }

        val point = points[currentIndex.coerceIn(0, points.size - 1)]
        dispatchTap(point.x, point.y)
        clickCount++

        val maxCount = config.getClickCount()
        if (maxCount > 0 && clickCount >= maxCount) {
            // 达到设定次数，停止
            stopClicking()
            return
        }

        currentIndex++
        if (currentIndex >= points.size) {
            if (!config.isLoopEnabled() && maxCount == 0) {
                // 非循环且无限次数：仅执行一遍即停止
                stopClicking()
                return
            }
            currentIndex = 0
        }

        val interval = config.getInterval().coerceAtLeast(MIN_INTERVAL)
        handler.postDelayed(clickRunnable, interval.toLong())
    }

    /**
     * 在指定屏幕坐标注入一次 tap 手势。
     * 使用 [GestureDescription] + [Path] 模拟一个时长极短的点击。
     */
    private fun dispatchTap(x: Int, y: Int) {
        if (x <= 0 || y <= 0) return
        try {
            val path = Path()
            path.moveTo(x.toFloat(), y.toFloat())
            val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            // dispatchGesture 为异步执行，结果回调此处忽略
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            // 关键 dispatch 必须捕获异常，避免服务崩溃
            e.printStackTrace()
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
        private const val NOTIFICATION_ID = 1001
        private const val TAP_DURATION: Long = 10
        private const val DOUBLE_PRESS_WINDOW = 500L
        private const val MIN_INTERVAL = 10

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
