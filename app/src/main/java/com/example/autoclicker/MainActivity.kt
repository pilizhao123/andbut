package com.example.autoclicker

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
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
 *
 * 支持运行方式（按次数/总时长/手动停止）、循环间隔（分/秒/毫秒）、
 * 运行速度倍率、点击位置随机微调、启动后自动运行、运行后自动停止等高级设置。
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
    private lateinit var rgRunMode: RadioGroup
    private lateinit var rbModeCount: RadioButton
    private lateinit var rbModeDuration: RadioButton
    private lateinit var rbModeManual: RadioButton
    private lateinit var etRunCount: EditText
    private lateinit var etDurationMin: EditText
    private lateinit var etDurationSec: EditText
    private lateinit var etIntervalMin: EditText
    private lateinit var etIntervalSec: EditText
    private lateinit var etIntervalMs: EditText
    private lateinit var tvIntervalValue: TextView
    private lateinit var spinnerSpeed: Spinner
    private lateinit var switchLoop: SwitchMaterial
    private lateinit var switchRandomOffset: SwitchMaterial
    private lateinit var etRandomOffset: EditText
    private lateinit var switchAutoStart: SwitchMaterial
    private lateinit var etStopAfterMin: EditText
    private lateinit var etStopAfterSec: EditText
    private lateinit var btnToggleOverlay: Button
    private lateinit var btnStartClick: Button

    private val speedOptions = listOf(0.5f, 1.0f, 2.0f, 5.0f, 10.0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = ConfigManager(this)

        bindViews()
        setupRecyclerView()
        setupIntervalControls()
        setupSpeedSpinner()
        setupRunMode()
        setupButtons()
        loadConfigToUi()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        refreshClickState()
        maybeAutoStart()
    }

    private fun bindViews() {
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        btnAccessibility = findViewById(R.id.btn_accessibility)
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        btnOverlay = findViewById(R.id.btn_overlay)
        btnAddPoint = findViewById(R.id.btn_add_point)
        recyclerView = findViewById(R.id.recycler_points)

        rgRunMode = findViewById(R.id.rg_run_mode)
        rbModeCount = findViewById(R.id.rb_mode_count)
        rbModeDuration = findViewById(R.id.rb_mode_duration)
        rbModeManual = findViewById(R.id.rb_mode_manual)
        etRunCount = findViewById(R.id.et_run_count)
        etDurationMin = findViewById(R.id.et_duration_min)
        etDurationSec = findViewById(R.id.et_duration_sec)

        etIntervalMin = findViewById(R.id.et_interval_min)
        etIntervalSec = findViewById(R.id.et_interval_sec)
        etIntervalMs = findViewById(R.id.et_interval_ms)
        tvIntervalValue = findViewById(R.id.tv_interval_value)

        spinnerSpeed = findViewById(R.id.spinner_speed)
        switchLoop = findViewById(R.id.switch_loop)

        switchRandomOffset = findViewById(R.id.switch_random_offset)
        etRandomOffset = findViewById(R.id.et_random_offset)
        switchAutoStart = findViewById(R.id.switch_auto_start)
        etStopAfterMin = findViewById(R.id.et_stop_after_min)
        etStopAfterSec = findViewById(R.id.et_stop_after_sec)

        btnToggleOverlay = findViewById(R.id.btn_toggle_overlay)
        btnStartClick = findViewById(R.id.btn_start_click)
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
        val listener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveIntervalFromUi()
        }
        etIntervalMin.onFocusChangeListener = listener
        etIntervalSec.onFocusChangeListener = listener
        etIntervalMs.onFocusChangeListener = listener
    }

    private fun saveIntervalFromUi() {
        val min = etIntervalMin.text.toString().toIntOrNull() ?: 0
        val sec = etIntervalSec.text.toString().toIntOrNull() ?: 0
        val ms = etIntervalMs.text.toString().toIntOrNull() ?: 0
        config.setInterval(min, sec, ms)
        refreshIntervalDisplay()
    }

    private fun refreshIntervalDisplay() {
        val interval = config.getInterval()
        tvIntervalValue.text = getString(
            R.string.interval_format,
            interval / 60000,
            (interval % 60000) / 1000,
            interval % 1000
        )
    }

    private fun setupSpeedSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            speedOptions.map { getString(R.string.speed_format, it) }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSpeed.adapter = adapter
        spinnerSpeed.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val percent = (speedOptions[position] * 100).toInt()
                config.setSpeedPercent(percent)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRunMode() {
        rgRunMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_mode_count -> config.setRunMode(ConfigManager.RUN_MODE_COUNT)
                R.id.rb_mode_duration -> config.setRunMode(ConfigManager.RUN_MODE_DURATION)
                R.id.rb_mode_manual -> config.setRunMode(ConfigManager.RUN_MODE_MANUAL)
            }
            refreshRunModeUi()
        }
    }

    private fun refreshRunModeUi() {
        val mode = config.getRunMode()
        rbModeCount.isChecked = mode == ConfigManager.RUN_MODE_COUNT
        rbModeDuration.isChecked = mode == ConfigManager.RUN_MODE_DURATION
        rbModeManual.isChecked = mode == ConfigManager.RUN_MODE_MANUAL
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
            toggleClicking()
        }
    }

    private fun toggleClicking() {
        saveAllSettingsFromUi()
        val service = ClickerService.getInstance()
        if (service == null) {
            Toast.makeText(this, R.string.toast_enable_accessibility_first, Toast.LENGTH_SHORT)
                .show()
            PermissionUtils.openAccessibilitySettings(this)
            return
        }
        service.toggleClicking()
        refreshClickState()
    }

    /** 启动后自动运行：如果开关打开且服务已连接且未在运行，则自动开始。 */
    private fun maybeAutoStart() {
        if (!config.isAutoStart()) return
        val service = ClickerService.getInstance() ?: return
        if (service.isClicking()) return
        if (config.getClickPoints().isEmpty()) return
        service.startClicking()
        refreshClickState()
    }

    private fun loadConfigToUi() {
        // 运行方式
        refreshRunModeUi()
        etRunCount.setText(config.getClickCount().toString())
        etDurationMin.setText(config.getDurationMinutes().toString())
        etDurationSec.setText(config.getDurationRemainSeconds().toString())

        // 间隔
        val interval = config.getInterval()
        etIntervalMin.setText((interval / 60000).toString())
        etIntervalSec.setText(((interval % 60000) / 1000).toString())
        etIntervalMs.setText((interval % 1000).toString())
        refreshIntervalDisplay()

        // 速度
        val speedIndex = speedOptions.indexOf(config.getSpeedPercent() / 100f)
        if (speedIndex >= 0) {
            spinnerSpeed.setSelection(speedIndex)
        }

        // 循环、随机偏移、自动启动、运行后停止
        switchLoop.isChecked = config.isLoopEnabled()
        val randomOffset = config.getRandomOffset()
        switchRandomOffset.isChecked = randomOffset > 0
        etRandomOffset.setText(randomOffset.toString())
        switchAutoStart.isChecked = config.isAutoStart()
        etStopAfterMin.setText(config.getStopAfterMinutes().toString())
        etStopAfterSec.setText(config.getStopAfterRemainSeconds().toString())

        // 焦点变化时保存
        etRunCount.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveAllSettingsFromUi()
        }
        etDurationMin.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveAllSettingsFromUi()
        }
        etDurationSec.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveAllSettingsFromUi()
        }
        etRandomOffset.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveAllSettingsFromUi()
        }
        etStopAfterMin.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveAllSettingsFromUi()
        }
        etStopAfterSec.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveAllSettingsFromUi()
        }
        switchLoop.setOnCheckedChangeListener { _, isChecked ->
            config.setLoopEnabled(isChecked)
        }
        switchRandomOffset.setOnCheckedChangeListener { _, isChecked ->
            config.setRandomOffset(if (isChecked) parseOffset() else 0)
        }
        switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            config.setAutoStart(isChecked)
        }
    }

    private fun saveAllSettingsFromUi() {
        val mode = when (rgRunMode.checkedRadioButtonId) {
            R.id.rb_mode_duration -> ConfigManager.RUN_MODE_DURATION
            R.id.rb_mode_manual -> ConfigManager.RUN_MODE_MANUAL
            else -> ConfigManager.RUN_MODE_COUNT
        }
        config.setRunMode(mode)

        config.setClickCount(etRunCount.text.toString().toIntOrNull() ?: 0)
        config.setDuration(
            etDurationMin.text.toString().toIntOrNull() ?: 0,
            etDurationSec.text.toString().toIntOrNull() ?: 0
        )

        saveIntervalFromUi()
        config.setRandomOffset(if (switchRandomOffset.isChecked) parseOffset() else 0)
        config.setAutoStart(switchAutoStart.isChecked)
        config.setStopAfter(
            etStopAfterMin.text.toString().toIntOrNull() ?: 0,
            etStopAfterSec.text.toString().toIntOrNull() ?: 0
        )
    }

    private fun parseOffset(): Int {
        return etRandomOffset.text.toString().toIntOrNull()?.coerceAtLeast(0)?.coerceAtMost(ConfigManager.MAX_RANDOM_OFFSET) ?: 0
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
