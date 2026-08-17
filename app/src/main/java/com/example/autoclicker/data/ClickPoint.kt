package com.example.autoclicker.data

/**
 * 表示一个屏幕点击坐标点。
 *
 * @property id    唯一标识，默认以当前纳秒时间生成，便于去重与定向删除。
 * @property x     屏幕横坐标（像素，基于 WindowManager 屏幕绝对坐标系）。
 * @property y     屏幕纵坐标（像素）。
 * @property label 用户可自定义的备注名称；为空时使用默认序号标签。
 */
data class ClickPoint(
    val id: Long = System.nanoTime(),
    val x: Int = 0,
    val y: Int = 0,
    val label: String = ""
)
