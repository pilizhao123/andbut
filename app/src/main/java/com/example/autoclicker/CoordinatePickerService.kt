package com.example.autoclicker

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.example.autoclicker.data.ClickPoint
import com.example.autoclicker.data.ConfigManager

/**
 * 坐标拾取悬浮层。
 *
 * 以半透明全屏 [WindowManager] 叠加层捕获用户点击，记录屏幕绝对坐标后自动关闭。
 * 该层位于控制悬浮窗之上，因此能优先接收触摸事件。
 */
class CoordinatePickerService : Service() {

    private lateinit var windowManager: WindowManager
    private var pickerView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showPicker()
        return START_NOT_STICKY
    }

    private fun showPicker() {
        if (pickerView != null) return
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_picker, null)
        pickerView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                if (x > 0 && y > 0) {
                    ConfigManager(this).addClickPoint(ClickPoint(x = x, y = y))
                    Toast.makeText(
                        this,
                        getString(R.string.toast_point_added, x, y),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                stopSelf()
                true
            } else {
                false
            }
        }

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        pickerView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // 忽略移除异常
            }
        }
        pickerView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
