package com.example.autoclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.autoclicker.util.PermissionUtils

/**
 * 开机广播接收器：设备启动完成后自动拉起悬浮控制窗。
 *
 * 注意：无障碍服务需用户手动在系统设置中启用，故此处仅恢复悬浮窗；
 * 若悬浮窗权限已授予则正常显示，否则由用户后续在 App 内授权。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 开机无悬浮窗权限时启动服务必崩，先校验再启动
            if (!PermissionUtils.canDrawOverlays(context)) {
                return
            }
            val serviceIntent = Intent(context, OverlayService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
