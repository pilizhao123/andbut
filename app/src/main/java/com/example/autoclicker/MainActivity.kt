package com.example.autoclicker

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.autoclicker.data.ConfigManager
import com.example.autoclicker.ui.ClickPointAdapter
import com.example.autoclicker.util.PermissionUtils
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * 主界面：权限引导、点击点管理、连点参数设置、悬浮窗与连点启停控制。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var config: ConfigManager
    private lateinit var adapter: ClickPointAdapter

    // 视图引用
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var tvOverlayStatus: TextView
    private lateinit var btnOverlay: Button
    private lateinit var btnAddPoint: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var seekInterval: SeekBar
    private lateinit var tvIntervalValue: TextView
    private lateinit var etInterval: EditText
    private lateinit var etCount: EditText
    private lateinit var switchLoop: SwitchMaterial
    private lateinit var btnToggleOverlay: Button
    private lateinit var btnStartClick: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = ConfigManager(this)

        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        btnAccessibility = findViewById(R.id.btn_accessibility)
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        btnOverlay = findViewById(R.id.btn_overlay)
        btnAddPoint = findViewById(R.id.btn_add_point)
        recyclerView = findViewById(R.id.recycler_points)
        seekInterval = findViewById(R.id.seek_interval)
        tvIntervalValue = findViewById(R.id.tv_interval_value)
        etInterval = findViewById(R.id.et_interval)
        etCount = findViewById(R.id.et_count)
        switchLoop = findViewById(R.id.switch_loop)
        btnToggleOverlay = findViewById(R.id.btn_toggle_overlay)
        btnStartClick = findViewById(R.id.btn_start_click)

        setupRecyclerView()
        setupIntervalControls()
        setupButtons()
        loadConfigToUi()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        refreshClickState()
    }

    private fun setupRecyclerView() {
        adapter = ClickPointAdapter { point ->
            config.removeClickPoint(point.id)
            adapter.submitList(config.getClickPoints())
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        adapter.submitList(config.getClickPoints())
    }

    private fun setupIntervalControls() {
        seekInterval.max = 5000
        seekInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // 对齐到 10 毫秒，避免过小粒度
                    val aligned = maxOf(10, (progress / 10) * 10)
                    config.setInterval(aligned)
                    tvIntervalValue.text = getString(R.string.interval_format, aligned)
                    etInterval.setText(aligned.toString())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupButtons() {
        btnAccessibility.setOnClickListener {
            PermissionUtils.openAccessibilitySettings(this)
        }
        btnOverlay.setOnClickListener {
            PermissionUtils.openOverlaySettings(this)
        }
        btnAddPoint.setOnClickListener {
            if (!PermissionUtils.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.toast_overlay_required, Toast.LENGTH_SHORT).show()
                PermissionUtils.openOverlaySettings(this)
                return@setOnClickListener
            }
            // 启动坐标拾取悬浮层
            startService(Intent(this, CoordinatePickerService::class.java))
        }
        btnToggleOverlay.setOnClickListener {
            if (PermissionUtils.canDrawOverlays(this)) {
                val intent = Intent(this, OverlayService::class.java)
                ContextCompat.startForegroundService(this, intent)
                btnToggleOverlay.setText(R.string.btn_hide_overlay)
            } else {
                PermissionUtils.openOverlaySettings(this)
            }
        }
        btnStartClick.setOnClickListener {
            val service = ClickerService.getInstance()
            if (service == null) {
                Toast.makeText(this, R.string.toast_enable_accessibility_first, Toast.LENGTH_SHORT)
                    .show()
                PermissionUtils.openAccessibilitySettings(this)
                return@setOnClickListener
            }
            service.toggleClicking()
            refreshClickState()
        }
    }

    private fun loadConfigToUi() {
        val interval = config.getInterval()
        seekInterval.progress = interval.coerceIn(0, 5000)
        tvIntervalValue.text = getString(R.string.interval_format, interval)
        etInterval.setText(interval.toString())
        etCount.setText(config.getClickCount().toString())
        switchLoop.isChecked = config.isLoopEnabled()

        etInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val v = etInterval.text.toString().toIntOrNull() ?: config.getInterval()
                config.setInterval(v)
                seekInterval.progress = v.coerceIn(0, 5000)
                tvIntervalValue.text = getString(R.string.interval_format, v)
            }
        }
        etCount.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val v = etCount.text.toString().toIntOrNull() ?: 0
                config.setClickCount(maxOf(0, v))
            }
        }
        switchLoop.setOnCheckedChangeListener { _, isChecked ->
            config.setLoopEnabled(isChecked)
        }
    }

    private fun refreshPermissionStatus() {
        val serviceName = ClickerService::class.java.name
        val accEnabled = PermissionUtils.isAccessibilityServiceEnabled(this, serviceName)
        tvAccessibilityStatus.setText(
            if (accEnabled) R.string.status_enabled else R.string.status_disabled
        )
        btnAccessibility.setText(
            if (accEnabled) R.string.btn_accessibility_disable_hint else R.string.btn_enable
        )
        val overlay = PermissionUtils.canDrawOverlays(this)
        tvOverlayStatus.setText(if (overlay) R.string.status_enabled else R.string.status_disabled)
        btnOverlay.setText(if (overlay) R.string.btn_overlay_disable_hint else R.string.btn_grant)
    }

    private fun refreshClickState() {
        val running = ClickerService.getInstance()?.isClicking() ?: false
        btnStartClick.setText(if (running) R.string.stop else R.string.start)
    }
}
