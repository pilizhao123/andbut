package com.example.autoclicker

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.autoclicker.data.ConfigManager
import com.example.autoclicker.util.NotificationUtils
import com.example.autoclicker.util.PermissionUtils

/**
 * 悬浮控制窗服务。
 *
 * 通过 [WindowManager] 在任意界面上叠加一个可拖动的悬浮面板，提供：
 * - 开始/停止连点按钮（控制 [ClickerService]）
 * - 进入设置（跳转 [MainActivity]）
 * - 当前连点状态指示
 *
 * 以前台服务形式运行，避免被系统回收；注册 [ClickerService] 的状态监听器，
 * 以便音量键触发的启停能同步刷新悬浮窗 UI。
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var config: ConfigManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var btnToggle: Button
    private lateinit var btnSettings: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvClickCount: TextView

    /** 点击次数轮询任务：运行中每 300ms 刷新悬浮窗计数。 */
    private val countRunnable = object : Runnable {
        override fun run() {
            if (!::tvClickCount.isInitialized) return
            val count = ClickerService.getInstance()?.getExecutedCount() ?: 0
            tvClickCount.text = getString(R.string.overlay_click_count, count)
            // 持续轮询，直到 stopCountPolling 移除
            mainHandler.postDelayed(this, 300)
        }
    }

    override fun onCreate() {
        super.onCreate()
        config = ConfigManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(
            NOTIFICATION_ID,
            NotificationUtils.buildNotification(this, getString(R.string.overlay_running))
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) {
            showOverlay()
        }
        // 注册状态监听，及时同步连点状态（例如音量键触发的启停）
        ClickerService.setStateListener { running ->
            mainHandler.post { applyRunningState(running) }
        }
        return START_STICKY
    }

    /** 构建并添加悬浮窗视图。 */
    private fun showOverlay() {
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_view, null)
        overlayView = view

        btnToggle = view.findViewById(R.id.btn_overlay_toggle)
        btnSettings = view.findViewById(R.id.btn_overlay_settings)
        tvStatus = view.findViewById(R.id.tv_overlay_status)
        tvClickCount = view.findViewById(R.id.tv_overlay_click_count)
        tvClickCount.text = getString(R.string.overlay_click_count, 0)

        val initialRunning = ClickerService.getInstance()?.isClicking() ?: false
        applyRunningState(initialRunning)

        btnToggle.setOnClickListener { onToggleClicked() }
        btnSettings.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        try {
            windowManager.addView(view, params)
            makeDraggable(view, params)
        } catch (e: Exception) {
            // 添加悬浮窗失败（如未授予悬浮窗权限），安全退出，避免崩溃
            e.printStackTrace()
            overlayView = null
            return
        }
    }

    /** 开始/停止按钮点击处理。 */
    private fun onToggleClicked() {
        val service = ClickerService.getInstance()
        if (service == null) {
            Toast.makeText(this, R.string.toast_enable_accessibility_first, Toast.LENGTH_SHORT)
                .show()
            PermissionUtils.openAccessibilitySettings(this)
            return
        }
        if (config.getClickPoints().isEmpty() && !service.isClicking()) {
            Toast.makeText(this, R.string.toast_no_points, Toast.LENGTH_SHORT).show()
            return
        }
        service.toggleClicking()
        applyRunningState(service.isClicking())
    }

    /**
     * 统一处理运行态变化：刷新按钮/状态文案，并开启或停止点击次数轮询。
     * 停止时保留最后一次计数，方便用户查看本次共点击多少次。
     */
    private fun applyRunningState(running: Boolean) {
        if (!::btnToggle.isInitialized) return
        val pointCount = config.getClickPoints().size
        btnToggle.setText(if (running) R.string.stop else R.string.start)
        tvStatus.text = getString(
            if (running) R.string.status_running else R.string.status_idle,
            pointCount
        )
        if (running) startCountPolling() else stopCountPolling()
    }

    /** 开始/继续点击次数轮询。 */
    private fun startCountPolling() {
        mainHandler.removeCallbacks(countRunnable)
        mainHandler.post(countRunnable)
    }

    /** 停止点击次数轮询（保留最后显示的数值）。 */
    private fun stopCountPolling() {
        mainHandler.removeCallbacks(countRunnable)
    }

    /** 让悬浮窗可通过顶部手柄拖动。 */
    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        val handle = view.findViewById<View>(R.id.overlay_drag_handle)
        handle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onDestroy() {
        ClickerService.setStateListener(null)
        stopCountPolling()
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // 忽略移除异常
            }
        }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1002
    }
}
