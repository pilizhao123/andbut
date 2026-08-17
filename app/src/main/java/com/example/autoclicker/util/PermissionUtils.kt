package com.example.autoclicker.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * 权限与系统设置跳转工具集。
 *
 * 自动连点器依赖两类关键权限：
 * 1. 无障碍服务（AccessibilityService）是否被系统启用；
 * 2. 悬浮窗（SYSTEM_ALERT_WINDOW）是否已授予。
 */
object PermissionUtils {

    /**
     * 检测指定无障碍服务是否已启用。
     *
     * @param serviceName 服务的完整类名，例如 [com.example.autoclicker.ClickerService] 的 name。
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceName: String): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabled = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabled.any { it.resolveInfo.serviceInfo.name == serviceName }
    }

    /** 跳转到系统无障碍设置页面。 */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 检测悬浮窗权限（SYSTEM_ALERT_WINDOW）是否已授予。 */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /** 跳转到本应用的悬浮窗权限设置页面。 */
    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
